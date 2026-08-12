# Руководство по развёртыванию ASG

> Пошаговое руководство по развёртыванию Адаптивного семантического шлюза (АСШ)
> на трёх окружениях: локальном (Docker Compose), staging (Helm + ArgoCD) и
> production (Yandex Cloud Managed K8s).

## Содержание

- [1. Локальное окружение (Docker Compose)](#1-локальное-окружение-docker-compose)
- [2. Staging (Helm + ArgoCD)](#2-staging-helm--argocd)
- [3. Production (Yandex Cloud Managed K8s)](#3-production-yandex-cloud-managed-k8s)
- [4. Пост-деплойная верификация](#4-пост-деплойная-верификация)
- [5. Процедура отката (Rollback)](#5-процедура-отката-rollback)
- [6. Шаблон деплой-тикета](#6-шаблон-деплой-тикета)

---

## 1. Локальное окружение (Docker Compose)

Цель: дать разработчику возможность за 5 минут поднять весь стек (8
сервисов) и начать кодить.

### 1.1. Предварительные требования

- Docker 24.x + Docker Compose v2 (`docker compose version`)
- JDK 17 (Temurin / OpenJDK) — только если планируется собирать asg-core
  из исходников.
- curl + jq для smoke-тестов.

### 1.2. Поднятие стека

```bash
# Из корня репозитория
cd asg-repository

# Опционально: переопределить дефолтные секреты
cp .env.example .env
$EDITOR .env   # выставить ASG_JWT_SECRET, ASG_POSTGRES_PASSWORD, GRAFANA_ADMIN_PASSWORD

# Сборка asg-core образа + поднятие всех 8 сервисов с ожиданием healthcheck
docker compose up -d --build --wait --timeout 180

# Проверка статуса
docker compose ps
# Все сервисы должны быть в состоянии healthy (или service_started для jaeger/loki).
```

### 1.3. Ожидаемые порты на хосте

| Порт  | Сервис          | Endpoint                                     |
|-------|-----------------|----------------------------------------------|
| 8080  | asg-core REST   | `GET http://localhost:8080/api/v1/health`    |
| 9090  | asg-core gRPC   | `grpc://localhost:9090`                       |
| 6379  | Redis           | `redis-cli -p 6379 ping`                     |
| 5432  | PostgreSQL      | `psql -h localhost -U asg -d asg`             |
| 3030  | Jena Fuseki     | `http://localhost:3030/$/ping`                |
| 19090 | Prometheus      | `http://localhost:19090/-/healthy`            |
| 3000  | Grafana         | `http://localhost:3000` (admin / admin)       |
| 3100  | Loki            | `http://localhost:3100/ready`                 |
| 16686 | Jaeger UI       | `http://localhost:16686`                       |

### 1.4. Smoke-тест

```bash
# Health
curl -fsS http://localhost:8080/api/v1/health | jq
# → {"status":"UP","version":"0.1.0","uptime":12345}

# Readiness
curl -fsS http://localhost:8080/api/v1/ready | jq
# → {"ready":true,"components":{"redis":"UP","postgres":"UP","jena":"UP"}}

# Трансляция запроса
curl -fsS -X POST http://localhost:8080/api/v1/translate \
  -H 'Authorization: Bearer dev.jwt.token' \
  -H 'Content-Type: application/json' \
  -d '{
        "sourceOntologyId":"smev:registration:v1",
        "targetOntologyId":"smev:tax:v1",
        "query":"∃ hasTaxId.Taxpayer"
      }' | jq
# → {"requestId":"req-...","accepted":true,"outcome":"valid","confidence":0.92,"tookMs":123}
```

### 1.5. Остановка и очистка

```bash
# Остановить с сохранением volumes
docker compose stop

# Остановить и удалить контейнеры (volumes сохранятся)
docker compose down

# Полная очистка включая volumes (ОСТОРОЖНО: удаляет данные!)
docker compose down -v --remove-orphans
```

### 1.6. Логи

```bash
# Все сервисы (live tail)
docker compose logs -f --tail=100

# Только asg-core
docker compose logs -f asg-core

# За последние 5 минут с фильтром по ошибкам
docker compose logs --since 5m asg-core | grep -iE 'error|warn'
```

---

## 2. Staging (Helm + ArgoCD)

Staging-кластер: Yandex Cloud Managed K8s (single-AZ, 2 worker-nodes
`standard-v3`, 4 vCPU / 8 GiB RAM). Имя неймспейса: `asg-staging`.

### 2.1. Предварительные требования

- `kubectl` 1.30+, настроенный kubeconfig для staging-кластера.
- `helm` 3.16+.
- `argocd` CLI 2.12+ (опционально — для ручного sync).
- Доступ к репозиторию (write) — ArgoCD читает `helm/` директорию.
- Secret `KUBECONFIG_STAGING` в GitHub Actions (base64-encoded kubeconfig).

### 2.2. Подготовка Helm values

```bash
cd helm
# 1. Создать/обновить values-staging.yaml (НЕ коммитить секреты!)
cat > values-staging.yaml <<EOF
image:
  repository: ghcr.io/smev/asg-core
  tag: staging-main      # обновляется CI (см. ci.yml Stage 6)

replicaCount: 3
resources:
  requests: { cpu: 1, memory: 2Gi }
  limits:   { cpu: 2, memory: 4Gi }

asg:
  env: staging
  logLevel: INFO
  jwtSecret: \${ASG_JWT_SECRET}   # из Sealed Secrets / Vault
  redis:
    enabled: true
    persistence: { size: 8Gi, storageClass: yc-network-ssd }
  postgresql:
    enabled: true
    persistence: { size: 20Gi, storageClass: yc-network-ssd }
    auth:
      username: asg
      database: asg
      existingSecret: asg-postgres-secret

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: asg-staging.smev.ru
      paths: [{ path: /, pathType: Prefix }]
  tls:
    - secretName: asg-staging-tls
      hosts: [asg-staging.smev.ru]

prometheus:
  serviceMonitor:
    enabled: true
    namespace: monitoring
EOF

# 2. Линт чарта
helm dependency update
helm lint . -f values.yaml -f values-staging.yaml

# 3. Template — проверить сгенерированные манифесты
helm template asg . -f values.yaml -f values-staging.yaml > /tmp/asg-staging-rendered.yaml
kubectl apply --dry-run=client -f /tmp/asg-staging-rendered.yaml
```

### 2.3. Деплой через ArgoCD (GitOps)

ArgoCD-приложение описано декларативно в `helm/templates/argocd-app.yaml`.
CI пайплайн обновляет только `spec.source.targetRevision` в этом манифесте
на свежий SHA после merge в `main`.

```bash
# 1. Убедиться, что ArgoCD знает о приложении
kubectl -n argocd get application asg-staging

# 2. Ручной sync (если auto-sync выключен)
argocd app sync asg-staging --prune

# 3. Мониторинг sync-статуса
argocd app wait asg-staging --sync --timeout 300
```

### 2.4. Деплой через Helm напрямую (fallback)

Если ArgoCD недоступен:

```bash
helm upgrade --install asg ./helm \
  --namespace asg-staging --create-namespace \
  -f helm/values.yaml -f helm/values-staging.yaml \
  --set image.tag=staging-$(git rev-parse --short HEAD) \
  --wait --timeout 5m
```

### 2.5. Проверка деплоя

```bash
# 1. Pods healthy
kubectl -n asg-staging get pods -l app.kubernetes.io/name=asg
# Все 3 реплики должны быть Ready (1/1) и Running.

# 2. Services
kubectl -n asg-staging get svc

# 3. Ingress
kubectl -n asg-staging get ingress

# 4. Health через Ingress
curl -fsS https://asg-staging.smev.ru/api/v1/health | jq
```

---

## 3. Production (Yandex Cloud Managed K8s)

Production-кластер: Yandex Cloud Managed K8s (multi-AZ, 3 master-nodes
`managed-master`, 5 worker-nodes `standard-v3` 8 vCPU / 16 GiB RAM).
Неймспейс: `asg-prod`.

### 3.1. Создание кластера через Terraform

```bash
cd terraform

# 1. Инициализация провайдеров
terraform init

# 2. Plan (preview изменений)
terraform plan -var-file=environments/prod.tfvars -out=prod.tfplan

# 3. Apply (создание кластера ~25 минут)
terraform apply -auto-approve prod.tfplan

# 4. Получить kubeconfig
yc managed-kubernetes cluster get-credentials asg-prod --external --force

# 5. Проверка доступа
kubectl cluster-info
kubectl get nodes
# Все 5 worker-nodes должны быть Ready.
```

### 3.2. Bootstrap инфраструктуры

```bash
# 1. Namespace
kubectl apply -f https://raw.githubusercontent.com/argoproj/argo-cd/v2.12.0/manifests/install.yaml
kubectl -n argocd wait deployment/argocd-server --for=condition=Available --timeout=300s

# 2. Sealed Secrets (Bitnami) — для шифрования секретов в git
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v2.15.0/controller.yaml

# 3. Ingress-nginx + cert-manager (Let's Encrypt)
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.service.annotations."yandex\.cloud/load-balancer-type"=Internal
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace --set installCRDs=true

# 4. Yandex Cloud Secret CSI driver (опционально — для Lockbox)
kubectl apply -f https://github.com/yandex-cloud/k8s-csi-s3/releases/download/v0.40.0/csi-s3.yaml
```

### 3.3. Шифрование секретов (Sealed Secrets)

```bash
# 1. Зашифровать секрет в git (не хранить plaintext!)
echo -n 'jwt_secret_value' | kubectl create secret generic asg-jwt-secret \
  --dry-run=client --from-literal=ASG_JWT_SECRET=- -o yaml | \
  kubeseal --controller-namespace=kube-system -o yaml > helm/templates/sealed-asg-jwt.yaml

# 2. Закоммитить helm/templates/sealed-asg-jwt.yaml
git add helm/templates/sealed-asg-jwt.yaml
git commit -m "feat(secrets): add sealed ASG_JWT_SECRET for prod"
```

### 3.4. Деплой Helm-чарта

```bash
cd helm

# 1. Подтянуть dependency-чарты (bitnami/redis, bitnami/postgresql)
helm dependency update

# 2. Линт
helm lint . -f values.yaml -f values-prod.yaml

# 3. Деплой (через ArgoCD — рекомендуется)
kubectl -n argocd apply -f helm/templates/argocd-app.yaml
argocd app sync asg-prod --prune

# 4. Или напрямую Helm (fallback)
helm upgrade --install asg ./helm \
  --namespace asg-prod --create-namespace \
  -f values.yaml -f values-prod.yaml \
  --set image.tag=prod-$(git rev-parse --short HEAD) \
  --wait --timeout 10m

# 5. Проверить rollout
kubectl -n asg-prod rollout status deployment/asg-core --timeout=600s
```

### 3.5. Настройка Ingress + TLS

```bash
# 1. Issuer для Let's Encrypt (cert-manager)
cat <<EOF | kubectl -n asg-prod apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata: { name: letsencrypt-prod }
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: security@smev.ru
    privateKeySecretRef: { name: letsencrypt-prod-key }
    solvers:
      - http01: { ingress: { class: nginx } }
EOF

# 2. Проверить сертификат
kubectl -n asg-prod get certificate
# STATUS должен быть Ready.
```

### 3.6. Production Smoke-тест

```bash
# 1. Health check
curl -fsS https://asg.smev.ru/api/v1/health | jq

# 2. Translate через REST
curl -fsS -X POST https://asg.smev.ru/api/v1/translate \
  -H "Authorization: Bearer $ASG_PROD_JWT" \
  -H 'Content-Type: application/json' \
  -d '{"sourceOntologyId":"smev:registration:v1","targetOntologyId":"smev:tax:v1","query":"∃ hasTaxId.Taxpayer"}' | jq

# 3. Нагрузочный тест (10% от peak в течение 5 мин — разминка)
k6 run -e K6_BASE_URL=https://asg.smev.ru -e K6_AUTH_TOKEN=$ASG_PROD_JWT tests/k6/basic-load.js \
  --env SOAK_DURATION=5m
```

---

## 4. Пост-деплойная верификация

После каждого деплоя (staging или prod) выполняется чек-лист:

### 4.1. Health checks

```bash
# 1. Liveness + readiness
curl -fsS https://asg-staging.smev.ru/api/v1/health | jq -e '.status == "UP"'
curl -fsS https://asg-staging.smev.ru/api/v1/ready | jq -e '.ready == true'

# 2. Все поды healthy (livenessProbe + readinessProbe pass)
kubectl -n asg-staging get pods -l app.kubernetes.io/name=asg \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.phase}{"\n"}{end}'
# Все pods должны быть в фазе Running.

# 3. HPA корректно отслейвил
kubectl -n asg-staging get hpa asg-core
# TARGETS должен показать текущую нагрузку < 80%.
```

### 4.2. Smoke-тесты API

```bash
# 1. Аутентификация — invalid token должен вернуть 401
curl -sS -o /dev/null -w '%{http_code}\n' \
  -X POST https://asg-staging.smev.ru/api/v1/translate \
  -H 'Authorization: Bearer INVALID' \
  -H 'Content-Type: application/json' \
  -d '{"sourceOntologyId":"a","targetOntologyId":"b","query":"x"}'
# → 401

# 2. Valid token — успешная трансляция
curl -fsS -X POST https://asg-staging.smev.ru/api/v1/translate \
  -H "Authorization: Bearer $ASG_STAGING_JWT" \
  -H 'Content-Type: application/json' \
  -d '{"sourceOntologyId":"smev:registration:v1","targetOntologyId":"smev:tax:v1","query":"∃ hasTaxId.Taxpayer"}' \
  | jq -e '.accepted == true and .confidence > 0'

# 3. Cache-hit (второй запрос тем же payload — p95 < 50 ms)
time curl -fsS -X POST https://asg-staging.smev.ru/api/v1/translate \
  -H "Authorization: Bearer $ASG_STAGING_JWT" \
  -H 'Content-Type: application/json' \
  -d '{"sourceOntologyId":"smev:registration:v1","targetOntologyId":"smev:tax:v1","query":"∃ hasTaxId.Taxpayer"}'
# Время должно быть < 100 ms.
```

### 4.3. Observability checks

```bash
# 1. Prometheus видит asg-core target (up == 1)
curl -fsS 'https://prometheus.asg-staging.smev.ru/api/v1/query?query=up{job="asg-core"}' | jq -e '.data.result[0].value[1] == "1"'

# 2. Grafana дашборд загружается
curl -fsS -u admin:$GRAFANA_PASS https://grafana.asg-staging.smev.ru/api/dashboards/uid/asg-operational | jq -e '.dashboard.title'

# 3. Логи идут в Loki
curl -fsS -G 'https://loki.asg-staging.smev.ru/loki/api/v1/query' \
  --data-urlencode 'query={job="asg-core"} |= "error"' | jq '.data.result | length'

# 4. Трейсы в Jaeger
curl -fsS 'https://jaeger.asg-staging.smev.ru/api/services' | jq -e '.data[] | select(. == "asg-core")'
```

### 4.4. SHACL-валидация в runtime

```bash
# Делаем запрос с заведомо некорректной DL-формулой — должна вернуться
# ошибка 422 + SHACL-отчёт
curl -sS -X POST https://asg-staging.smev.ru/api/v1/translate \
  -H "Authorization: Bearer $ASG_STAGING_JWT" \
  -H 'Content-Type: application/json' \
  -d '{"sourceOntologyId":"smev:registration:v1","targetOntologyId":"smev:tax:v1","query":"INVALID DL SYNTAX ###"}' \
  | jq
# Ожидается: 422 + {"error":"shacl_validation_failed", "violations":[...]}
```

---

## 5. Процедура отката (Rollback)

### 5.1. Rollback через ArgoCD (рекомендуемый способ)

```bash
# 1. Найти историю sync
argocd app history asg-staging

# 2. Откатиться к предыдущей ревизии (например, к SHA abc1234)
argocd app rollback asg-staging <REVISION-ID>

# 3. Или через git revert + push (ArgoCD auto-sync подхватит)
git revert <bad-commit-sha>
git push origin main
# ArgoCD увидит расхождение с live state и синхронизирует автоматически.
```

### 5.2. Rollback через Helm

```bash
# 1. История релизов
helm -n asg-staging history asg

# 2. Rollback к предыдущей ревизии
helm -n asg-staging rollback asg <REVISION-NUMBER>

# 3. Проверить, что откат прошёл
kubectl -n asg-staging rollout status deployment/asg-core --timeout=300s
```

### 5.3. Экстренный rollback (pods eviction)

Если новый образ вообще не стартует и readiness probe не проходит:

```bash
# 1. Уменьшить replicas до 0 (приложить existing deployment)
kubectl -n asg-staging scale deployment/asg-core --replicas=0

# 2. Откатить образ вручную
kubectl -n asg-staging set image deployment/asg-core \
  asg-core=ghcr.io/smev/asg-core:staging-previous-sha

# 3. Восстановить replicas
kubectl -n asg-staging scale deployment/asg-core --replicas=3

# 4. Проверить
kubectl -n asg-staging rollout status deployment/asg-core --timeout=600s
```

### 5.4. Rollback базы данных (только критический случай)

> **ВНИМАНИЕ:** Откат PostgreSQL схемы = потеря данных. Применять только при
> несовместимых миграциях. См. `docs/security.md` §Backup strategy.

```bash
# 1. Перевести asg-core в read-only mode (через ConfigMap)
kubectl -n asg-staging edit configmap asg-core-config
#   asg.readonly: "true"

# 2. Сделать PITR-восстановление из Yandex Cloud Backup
yc managed-postgresql cluster restore asg-pg-staging \
  --time "2026-08-11T12:00:00Z" \
  --name asg-pg-staging-restored

# 3. Переключить ASG на восстановленный кластер (через Sealed Secret)
# ... затем выполнить Helm upgrade.
```

---

## 6. Шаблон деплой-тикета

```markdown
## Deploy: ASG v0.2.0 → staging

**Ветка:** release/0.2.0
**SHA:** abc1234
**Дата:** 2026-08-15

### Pre-deploy checklist
- [ ] Все CI проверки зелёные (ci.yml, shacl-validate.yml)
- [ ] Lean 4 verification прошла (lean-verify.yml)
- [ ] CHANGELOG.md обновлён
- [ ] Migration script применён к staging БД (dry-run)
- [ ] Backups созданы (PG + Redis)

### Deploy
- [ ] `git merge release/0.2.0 → main` → push
- [ ] ArgoCD auto-sync запустился
- [ ] `kubectl -n asg-staging rollout status deployment/asg-core` — Success

### Post-deploy verification
- [ ] `/api/v1/health` — UP
- [ ] Smoke-test translate — 200
- [ ] Cache-hit latency < 50 ms (2-й запрос)
- [ ] Prometheus target up
- [ ] Grafana dashboard загружается
- [ ] Loki видит новые логи
- [ ] Jaeger видит новые трейсы

### Rollback plan
- [ ] SHA предыдущего релиза: def5678
- [ ] `argocd app rollback asg-staging <REVISION>` — готов
- [ ] DB migration down-script готов (если применимо)

### Sign-off
- [ ] SRE engineer: ___
- [ ] Dev lead: ___
- [ ] Product owner: ___
```
