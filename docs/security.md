# Безопасность ASG

> Документ описывает модель безопасности Адаптивного семантического шлюза:
> аутентификацию JWT, RBAC-авторизацию, управление секретами, сетевые
> политики, защиту от OWASP Top 10, SBOM (CycloneDX) и ауд-логирование
> через PROV-O.

## Содержание

- [Аутентификация](#аутентификация)
- [Авторизация (RBAC)](#авторизация-rbac)
- [Управление секретами](#управление-секретами)
- [Сетевые политики (NetworkPolicies)](#сетевые-политики-networkpolicies)
- [OWASP Top 10 — митигация](#owasp-top-10--митигация)
- [SBOM (CycloneDX)](#sbom-cyclonedx)
- [Аудит-логирование (PROV-O)](#аудит-логирование-prov-o)

---

## Аутентификация

### JWT (HS256)

Все запросы к REST/gRPC API asg-core (кроме `/health`, `/ready`, `/metrics`)
должны содержать валидный Bearer JWT в заголовке `Authorization`:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJmbnMtaXNtcyIsImlhdCI6MTcyMzM1MzYwMCwiZXhwIjoxNzIzMzU3MjAwLCJzY29wZSI6WyJhc2c6dHJhbnNsYXRlIl19.signature
```

**Алгоритм:** HS256 (HMAC-SHA256) с симметричным ключом `ASG_JWT_SECRET`.

**Срок жизни токена:** 1 час (3600 с) для standard-tier, 10 минут для
privileged-операций (административные endpoints).

**Claims (проверяются на сервере):**

| Claim       | Тип       | Проверка                                                 |
|-------------|-----------|----------------------------------------------------------|
| `sub`       | string    | Должен присутствовать (consumer_id)                      |
| `iss`       | string    | Должен быть в whitelist (`smev-idp`, `keycloak`)          |
| `iat`       | integer   | `iat ≤ now + 60s` (clock skew tolerance)                |
| `exp`       | integer   | `exp > now` (token не истёк)                             |
| `scope`     | string[]  | Должен содержать требуемый scope (`asg:translate`)       |
| `consumer_id`| string   | Должен быть зарегистрирован в `asg:consumers`            |
| `tier`      | string    | `standard` \| `premium` \| `internal` (влияет на rate-limit)|

### Key rotation

Ключ `ASG_JWT_SECRET` ротируется **раз в 90 дней**. Поддерживается
**grace-период 24 часа**, в течение которого валидны и старый, и новый ключи:

```hocon
# application.conf
asg {
  jwt {
    secret          = ${ASG_JWT_SECRET}            # текущий ключ
    previousSecret  = ${ASG_JWT_SECRET_PREVIOUS?}  # предыдущий (24h grace)
    gracePeriodMs   = 86400000                     # 24h
    issuerWhitelist = ["smev-idp", "keycloak"]
    clockSkewSec    = 60
  }
}
```

**Процедура ротации:**

1. **T-0 (день ротации):**
   - Сгенерировать новый ключ: `openssl rand -base64 64`.
   - Обновить `ASG_JWT_SECRET` в Vault / Sealed Secret.
   - Записать старое значение в `ASG_JWT_SECRET_PREVIOUS`.
2. **T+0 (deploy):**
   - ASG начинает принимать токены, подписанные обоими ключами.
3. **T+24h:**
   - Удалить `ASG_JWT_SECRET_PREVIOUS`.
   - Следующий deploy — только новый ключ.

### Утечка ключа

Если ключ скомпрометирован:

1. **Немедленно** сгенерировать новый ключ, обновить `ASG_JWT_SECRET` (без
   previousSecret — grace-период отключается).
2. Деплой forced-restart всех реплик asg-core.
3. Сообщить всем consumer'ам перевыпустить токены.
4. Включить audit log search: найти все запросы с подозрительных
   `consumer_id` / IP за период утечки.
5. Записать инцидент в `docs/incidents/<date>-jwt-leak.md`.

---

## Авторизация (RBAC)

### Роли

| Роль       | Описание                                     | Scopes                                    |
|------------|----------------------------------------------|-------------------------------------------|
| `admin`    | SRE-инженер, полный доступ + операции        | `asg:admin`, `asg:translate`, `asg:ontology:*`, `asg:mapping:*` |
| `operator` | Оператор онтологий — управление маппингами   | `asg:translate`, `asg:ontology:read`, `asg:mapping:write` |
| `observer` | Только чтение (мониторинг, аудит)            | `asg:ontology:read`, `asg:audit:read`    |
| `consumer` | Ведомственная ИС — только translate          | `asg:translate`                          |

### Endpoints × Scopes

| Endpoint                      | Method | Required scope           |
|-------------------------------|--------|--------------------------|
| `/api/v1/translate`           | POST   | `asg:translate`          |
| `/api/v1/ontology/{id}`       | GET    | `asg:ontology:read`      |
| `/api/v1/ontology`             | POST   | `asg:ontology:write`    |
| `/api/v1/mapping`              | POST   | `asg:mapping:write`     |
| `/api/v1/mapping/{id}`        | DELETE | `asg:mapping:delete`    |
| `/api/v1/audit`                | GET    | `asg:audit:read`         |
| `/api/v1/admin/cache/invalidate`| POST  | `asg:admin`              |
| `/api/v1/health`               | GET    | (none — public)          |
| `/api/v1/ready`                | GET    | (none — public)          |
| `/api/v1/metrics`              | GET    | (none — restricted by NetworkPolicy to prometheus namespace) |

### Policy enforcement (Scala)

```scala
// asg-core/src/main/scala/ru/smev/asg/security/RbacPolicy.scala
final case class Principal(consumerId: String, role: String, scopes: Set[String])

object RbacPolicy:
  def authorize(principal: Principal, requiredScope: String): Boolean =
    principal.scopes.contains(requiredScope) ||
    principal.scopes.contains("asg:admin")   // admin = god mode

  def authorizeEndpoint(principal: Principal, endpoint: String, method: String): Either[String, Unit] =
    val scope = (endpoint, method) match
      case ("/api/v1/translate", "POST")          => "asg:translate"
      case ("/api/v1/ontology", "POST")             => "asg:ontology:write"
      case ("/api/v1/mapping", "POST")             => "asg:mapping:write"
      case ("/api/v1/admin/cache/invalidate", "POST") => "asg:admin"
      case (e, "GET") if e.startsWith("/api/v1/ontology/") => "asg:ontology:read"
      case _ => return Left("forbidden")
    if authorize(principal, scope) then Right(())
    else Left(s"missing scope: $scope")
```

---

## Управление секретами

### Vault (Production)

В production (Yandex Cloud Managed K8s) для хранения секретов используется
HashiCorp Vault + Vault Agent Injector. Секреты **не хранятся** в git-репозитории
или Kubernetes Secret (plaintext в etcd).

```yaml
# helm/templates/deployment.yaml (фрагмент)
spec:
  template:
    metadata:
      annotations:
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "asg-core"
        vault.hashicorp.com/agent-inject-secret-ASG_JWT_SECRET: "kv/data/asg/jwt"
        vault.hashicorp.com/agent-inject-template-ASG_JWT_SECRET: |
          {{- with secret "kv/data/asg/jwt" -}}
          export ASG_JWT_SECRET="{{ .Data.data.secret }}"
          {{- end }}
    spec:
      containers:
        - name: asg-core
          env:
            - name: ASG_JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: asg-jwt-vault-injected
                  key: ASG_JWT_SECRET
```

### Sealed Secrets (Staging / Dev)

В staging и локальном dev-окружении используется Bitnami Sealed Secrets —
секреты шифруются закрытым ключом (только кластер может расшифровать),
зашифрованный plaintext можно хранить в git.

```bash
# Зашифровать секрет
echo -n 'my-jwt-secret' | kubectl create secret generic asg-jwt-secret \
  --dry-run=client --from-literal=ASG_JWT_SECRET=- -o yaml | \
  kubeseal --controller-namespace=kube-system -o yaml > helm/templates/sealed-asg-jwt.yaml

# Закоммитить helm/templates/sealed-asg-jwt.yaml в git
git add helm/templates/sealed-asg-jwt.yaml
git commit -m "feat(secrets): add sealed ASG_JWT_SECRET for staging"
```

### Backup strategy

| Тип секрета               | Где хранится           | Backup                          |
|---------------------------|------------------------|---------------------------------|
| `ASG_JWT_SECRET`           | Vault `kv/data/asg/jwt`| Vault snapshot (ежедневный)    |
| `ASG_POSTGRES_PASSWORD`    | Vault                  | Vault snapshot                  |
| Vault root token           | Yandex Lockbox         | KMS-encrypted backup в S3       |
| TLS private keys           | cert-manager + Vault  | Certificate Transparency logs  |
| Sealed Secrets private key | K8s secret in kube-system | `kubectl get secret -n kube-system sealed-secrets-key -o yaml` (ежедневный cron backup в S3) |

---

## Сетевые политики (NetworkPolicies)

Применяется default-deny + явные allow-правила:

```yaml
# helm/templates/networkpolicy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: asg-core-deny-all
  namespace: asg-prod
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: asg
  policyTypes:
    - Ingress
    - Egress
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: asg-core-allow-ingress
  namespace: asg-prod
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: asg
  policyTypes: [Ingress]
  ingress:
    # REST/gRPC только из namespace ingress-nginx
    - from:
        - namespaceSelector:
            matchLabels: { kubernetes.io/metadata.name: ingress-nginx }
      ports:
        - protocol: TCP
          port: 8080   # REST
        - protocol: TCP
          port: 9090   # gRPC
    # Prometheus scrape только из namespace monitoring
    - from:
        - namespaceSelector:
            matchLabels: { kubernetes.io/metadata.name: monitoring }
      ports:
        - protocol: TCP
          port: 8080
        - protocol: TCP
          port: 9090   # metrics если отдельный порт
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: asg-core-allow-egress
  namespace: asg-prod
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: asg
  policyTypes: [Egress]
  egress:
    # DNS
    - to: [{ namespaceSelector: { matchLabels: { kubernetes.io/metadata.name: kube-system } } }]
      ports: [{ protocol: UDP, port: 53 }]
    # Redis (in-cluster)
    - to: [{ podSelector: { matchLabels: { app.kubernetes.io/name: redis } } }]
      ports: [{ protocol: TCP, port: 6379 }]
    # PostgreSQL (in-cluster)
    - to: [{ podSelector: { matchLabels: { app.kubernetes.io/name: postgresql } } }]
      ports: [{ protocol: TCP, port: 5432 }]
    # Jena Fuseki (in-cluster)
    - to: [{ podSelector: { matchLabels: { app.kubernetes.io/name: jena-fuseki } } }]
      ports: [{ protocol: TCP, port: 3030 }]
    # OTLP → Jaeger (in-cluster)
    - to: [{ podSelector: { matchLabels: { app.kubernetes.io/name: jaeger } } }]
      ports:
        - { protocol: TCP, port: 4317 }
        - { protocol: TCP, port: 4318 }
    # HTTPS to external IdP (Keycloak) — через egress NAT gateway
    - to: [{ ipBlock: { cidr: 0.0.0.0/0, except: [10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16] } }]
      ports: [{ protocol: TCP, port: 443 }]
```

### Service Mesh (опционально)

Для mTLS между сервисами можно включить Istio / Linkerd. В MVP —
NetworkPolicies достаточно (L3/L4). mTLS — Sprint 3.

---

## OWASP Top 10 — митигация

| #   | OWASP Top 10 (2021)            | Mitigation in ASG                                                          |
|-----|--------------------------------|---------------------------------------------------------------------------|
| A01 | Broken Access Control          | RBAC (4 роли × 6 scopes); JWT scope-проверка на каждый endpoint          |
| A02 | Cryptographic Failures         | HS256 для JWT (HMAC, не RSA — для внешних потребителей); TLS 1.3 в Ingress; Secrets в Vault/Sealed Secrets (не plaintext) |
| A03 | Injection                       | Doobie parameterized SQL (no string concat); SPARQL via ParameterizedSparqlBuilder; SHACL violation reports sanitized |
| A04 | Insecure Design                 | STRIDE threat modeling (см. ниже); three-tier escalation = graceful degradation |
| A05 | Security Misconfiguration       | Helm lint + kyverno policies (см. ниже); NetworkPolicy default-deny; readOnlyRootFilesystem in pod spec |
| A06 | Vulnerable & Outdated Components| Dependabot weekly + SBOM scan (Trivy) в CI; Renovate для Helm dependencies |
| A07 | Identification & Authentication Failures | JWT exp ≤ 1h, refresh-токен ≤ 24h; rate-limit на /translate (429 после 100 RPS на consumer) |
| A08 | Software & Data Integrity Failures | Cosign подпись Docker-образов; Helm chart подпись (Sigstore); SBOM генерится в CI на каждый build |
| A09 | Security Logging & Monitoring Failures | Все запросы логируются в Loki (JSON-structured) + audit-trail PROV-O в PostgreSQL; алерты P1/P2 в PagerDuty/Slack |
| A10 | Server-Side Request Forgery    | Egress NetworkPolicy: только разрешённые destination (Redis, Postgres, Jena, Keycloak HTTPS); URL-fetch из онтологий отключён |

### Kyverno policies (дополнительно)

В production дополнительно enforced через Kyverno admission-контроллер:

```yaml
# kyverno/disallow-root.yaml
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata: { name: disallow-root-user }
spec:
  validationFailureAction: enforce
  rules:
    - name: require-non-root-user
      match: { resources: { kinds: [Pod] } }
      validate:
        message: "Containers must not run as root."
        pattern:
          spec:
            containers:
              - securityContext:
                  runAsNonRoot: true
```

### STRIDE threat model (summary)

| Threat                  | Where                        | Mitigation                          |
|-------------------------|------------------------------|-------------------------------------|
| Spoofing                | JWT фальсификация            | HS256 + key rotation                |
| Tampering               | Подмена маппинга в БД        | PostgreSQL row-level security + audit log |
| Repudiation             | Consumer отказывается от запроса | PROV-O audit trail (immutable)     |
| Information Disclosure  | Утечка DL-формул в логах      | Structured logging; PII не логируется |
| Denial of Service       | Flood запросами              | Rate-limit (Redis token-bucket)     |
| Elevation of Privilege  | JWT с лишним scope            | Scope whitelist на каждый endpoint |

---

## SBOM (CycloneDX)

Для каждого Docker-образа в CI генерируется SBOM (Software Bill of Materials)
в формате CycloneDX 1.5 (JSON). Это позволяет:
- Отслеживать все транзитивные зависимости.
- Реагировать на CVE в конкретной версии библиотеки.
- Аудит-проверки (внешний пентест).

### Генерация в CI

Stage 1 (`build`) в `.github/workflows/ci.yml`:

```yaml
- name: Generate SBOM (CycloneDX) for asg-core image
  uses: anchore/sbom-action@v0
  with:
    image: ghcr.io/smev/asg-core:0.1.0-${{ github.sha }}
    format: json
    output-file: asg-core-sbom.json
    dependency-snapshot: true   # GitHub Dependency Graph integration

- name: Upload SBOM artefact
  uses: actions/upload-artifact@v4
  with:
    name: asg-core-sbom-${{ github.sha }}
    path: asg-core-sbom.json
    retention-days: 30
```

### Vulnerability scan (Trivy)

Дополнительный шаг в CI (в stage `static`):

```bash
# Сканирование образа на известные CVE
trivy image --severity HIGH,CRITICAL --exit-code 1 \
  ghcr.io/smev/asg-core:0.1.0-${{ github.sha }}
```

Если найдены HIGH/CRITICAL CVE → CI падает. Для неотложных релизов можно
завести исключения через `.trivyignore` (с обоснованием и датой).

### SBOM структура (фрагмент)

```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.5",
  "version": 1,
  "metadata": {
    "component": {
      "type": "container",
      "name": "asg-core",
      "version": "0.1.0",
      "purl": "pkg:docker/ghcr.io/smev/asg-core@0.1.0-abc1234"
    }
  },
  "components": [
    { "type": "library", "group": "org.scala-lang", "name": "scala3-library_3", "version": "3.3.3" },
    { "type": "library", "group": "com.typesafe.akka", "name": "akka-actor-typed_3", "version": "2.8.5" },
    { "type": "library", "group": "org.apache.jena", "name": "jena-shacl", "version": "4.10.0" },
    { "type": "library", "group": "io.lettuce", "name": "lettuce-core", "version": "6.3.2.RELEASE" }
  ],
  "dependencies": [ /* ... */ ]
}
```

---

## Аудит-логирование (PROV-O)

Все запросы к asg-core логируются как **PROV-O**-активности в PostgreSQL.
PROV-O (W3C Provenance Ontology) — стандарт для описания происхождения данных.

### Схема

```turtle
@prefix prov: <http://www.w3.org/ns/prov#> .
@prefix asg:  <https://smev.ru/asg/prov#> .
@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

# Activity: один запрос translate
asg:activity-01HV7X9K2Y3M1N4P5Q6R7S8T9V a prov:Activity ;
  prov:startedAtTime  "2026-08-11T10:23:45.123Z"^^xsd:dateTime ;
  prov:endedAtTime    "2026-08-11T10:23:45.246Z"^^xsd:dateTime ;
  prov:wasAssociatedWith asg:agent-asg-core-7d6f5e4 ;
  prov:used           asg:mapping-registration-tax-inn-v1 ;
  prov:used           asg:ontology-registration-v1 ;
  prov:used           asg:ontology-tax-v1 ;
  prov:wasInformedBy  asg:activity-cache-lookup-01HV7X9K2 ;
  prov:wasInformedBy  asg:activity-shacl-validate-01HV7X9K2 ;
  asg:consumerId     "fns-isms" ;
  asg:requestId       "req-01HV7X9K2Y3M1N4P5Q6R7S8T9V" ;
  asg:outcome         "valid" ;
  asg:confidence      "0.92"^^xsd:double ;
  asg:contour         "hot-l" ;
  asg:tookMs          "123"^^xsd:long ;
  asg:traceId         "5f4dcc3b5aa765d61d8327deb882cf99" .

# Agent: экземпляр asg-core
asg:agent-asg-core-7d6f5e4 a prov:SoftwareAgent ;
  prov:actedOnBehalfOf asg:organization-smev ;
  asg:podName         "asg-core-7d6f5e4" ;
  asg:version         "0.1.0" .

# Entity: использованный маппинг
asg:mapping-registration-tax-inn-v1 a prov:Entity ;
  asg:sourceOntology "smev:registration:v1" ;
  asg:targetOntology "smev:tax:v1" ;
  asg:updatedAt      "2026-07-15T12:00:00Z" .
```

### Хранение

PROV-O-записи сохраняются в PostgreSQL (таблица `asg_audit.prov_activities`):

```sql
CREATE TABLE asg_audit.prov_activities (
  activity_id        TEXT PRIMARY KEY,
  consumer_id        TEXT NOT NULL,
  request_id         TEXT NOT NULL,
  trace_id           TEXT,
  started_at         TIMESTAMPTZ NOT NULL,
  ended_at           TIMESTAMPTZ,
  outcome            TEXT NOT NULL CHECK (outcome IN ('valid','invalid','escalated','error')),
  confidence         DOUBLE PRECISION,
  contour            TEXT NOT NULL CHECK (contour IN ('hot-l','hot-l-r','learner')),
  took_ms            BIGINT,
  agent_pod          TEXT,
  agent_version      TEXT,
  used_mappings      JSONB,
  used_ontologies    JSONB,
  jwt_claims         JSONB,                -- sub, iss, scope (БЕЗ signature)
  client_ip          INET,
  request_payload    JSONB,                -- ЕСЛИ включён audit-payload (опционально)
  CONSTRAINT activity_id_format CHECK (activity_id ~ '^act:01[A-Z0-9]+$')
);

CREATE INDEX idx_prov_activities_consumer_time
  ON asg_audit.prov_activities(consumer_id, started_at DESC);
CREATE INDEX idx_prov_activities_trace ON asg_audit.prov_activities(trace_id);
CREATE INDEX idx_prov_activities_outcome ON asg_audit.prov_activities(outcome, started_at DESC);
```

### Retention

| Тип записи                  | Retention   | Storage  |
|-----------------------------|-------------|----------|
| `asg_audit.prov_activities` | 7 лет (ФЗ)  | Hot 30 days → Cold (S3 Glacier) |
| Loki logs (structured)      | 30 days hot | 1 year cold |
| Jaeger traces              | 14 days     | (in-cluster, не persistence) |
| Prometheus metrics         | 15 days hot | 1 year cold (VictoriaMetrics) |

> 7-летний retention_prov_activities соответствует требованиям
> ФЗ-149 «Об информации, ИТ и о защите информации» (для гос. систем).

### Доступ к ауд-логам

Чтение `asg_audit.*` таблиц требует scope `asg:audit:read` (роль `admin`
или `observer`). Все SELECT'ы сами логируются (meta-audit) в
`asg_audit.audit_access_log`.

### Пример запроса

```sql
-- Все запросы от consumer "fns-isms" за последний час с outcome=invalid
SELECT activity_id, request_id, started_at, took_ms, request_payload->'query' AS dl_query
FROM asg_audit.prov_activities
WHERE consumer_id = 'fns-isms'
  AND outcome = 'invalid'
  AND started_at > NOW() - INTERVAL '1 hour'
ORDER BY started_at DESC
LIMIT 100;
```

```sparql
# Через Jena Fuseki (если провёнанс выгружен в RDF)
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX asg:  <https://smev.ru/asg/prov#>
SELECT ?activity ?consumer ?outcome ?tookMs WHERE {
  ?activity a prov:Activity ;
            asg:consumerId ?consumer ;
            asg:outcome ?outcome ;
            asg:tookMs ?tookMs .
  FILTER(?consumer = "fns-isms" && ?outcome = "invalid")
}
ORDER BY DESC(?tookMs) LIMIT 50
```
