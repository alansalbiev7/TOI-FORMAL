# Приложение 11. Архитектурные диаграммы

> Настоящий каталог описывает архитектурные диаграммы Адаптивного
> семантического шлюза (АСШ / ASG). Диаграммы выполнены в нотации C4
> Model (Context / Container / Component) с расширениями для
> потоков данных и верификационного контура. Файлы диаграмм
> располагаются в каталоге `docs/diagrams/`; исходные векторные
> диаграммы — в формате SVG (масштабируемые без потерь). Связь с
> монографией — §3.1, §3.2.

---

## 1. Сводная таблица диаграмм

| #  | Название                              | Файл                              | Формат | Уровень C4 |
|----|---------------------------------------|-----------------------------------|--------|------------|
| 1  | Системный контекст ASG                | `docs/diagrams/01-system-context.svg` | SVG | Context   |
| 2  | Контейнеры ASG                        | `docs/diagrams/02-containers.svg`  | SVG | Container |
| 3  | Компоненты asg-core                   | `docs/diagrams/03-components.svg` | SVG | Component |
| 4  | Многоагентная архитектура             | `docs/diagrams/04-multi-agent.svg` | SVG | Component |
| 5  | Трёхуровневая верификация             | `docs/diagrams/05-verification.svg`| SVG | Component |
| 6  | Контур Hot-L (эскалация)              | `docs/diagrams/06-hotl-contour.svg` | SVG | Component |
| 7  | Поток данных запроса                  | `docs/diagrams/07-data-flow.svg`   | SVG | Sequence  |
| 8  | FSM ArbiterAgent                      | `docs/diagrams/08-arbiter-fsm.svg` | SVG | State     |
| 9  | Развёртывание K8s                     | `docs/diagrams/09-k8s-deployment.svg`| SVG | Deployment |
| 10 | Наблюдаемость (observability stack)   | `docs/diagrams/10-observability.svg`| SVG | Container |

> Примечание. Файлы SVG генерируются из Mermaid-исходников в
> `docs/architecture.md` через инструмент `mmdc` (Mermaid CLI).
> Скрипт регенерации: `docs/diagrams/regenerate.sh`.

---

## 2. Диаграмма 1. Системный контекст ASG

### Назначение
Демонстрирует место ASG в экосистеме СМЭВ: какие внешние системы
потребляют или предоставляют данные, какие роли пользователей
взаимодействуют со шлюзом, какие инфраструктурные сервисы
поддерживают работу.

### Ключевые компоненты

- **Оператор ВИС** — конечный пользователь, инициирующий запрос
  через ведомственную ИС.
- **Ведомственные ИС** (ФНС, ФОМС, МВД) — потребители и поставщики
  данных в СМЭВ.
- **СМЭВ-шина** — транспортный слой взаимодействия ВИС (SOAP/JMS).
- **ASG / АСШ** — центральный компонент, обеспечивающий
  семантическую трансляцию запросов.
- **Identity Provider (Keycloak / SAML)** — провайдер идентичности
  для административных сессий.
- **Observability stack** (Grafana / Prometheus / Loki / Jaeger) —
  мониторинг, логирование, трассировка.
- **Git-репозиторий** — источник онтологий и SHACL-шейпов (GitOps).
- **Администратор ASG** (SRE-команда) — операционная поддержка.
- **Разработчик онтологий** — онтолог СМЭВ, обновляющий онтологии в
  Git.

### Связи

| Связь                              | Тип           | Протокол              |
|------------------------------------|---------------|------------------------|
| ВИС → СМЭВ-шина                   | sync          | SOAP/JMS               |
| СМЭВ-шина ↔ ASG                    | sync          | REST (HTTPS) / gRPC    |
| ASG → IdP                          | sync          | OAuth 2.0 / SAML       |
| ASG → Observability                | async         | OTLP (HTTP/gRPC)       |
| Git-репозиторий → ASG              | event-driven  | Webhook / git pull     |
| Администратор → ASG                | sync          | REST (HTTPS)           |
| Администратор → Observability      | sync          | HTTPS (Grafana UI)     |
| Разработчик онтологий → Git        | sync          | SSH / HTTPS (git)      |

### Файл
`docs/diagrams/01-system-context.svg` — 1280×800 px, векторный.

---

## 3. Диаграмма 2. Контейнеры ASG

### Назначение
Описывает внутреннюю структуру ASG как набор взаимодействующих
контейнеров (в локальном docker-compose — 8 сервисов, в production
K8s — соответствующие Pod'ы).

### Ключевые компоненты

| Контейнер              | Технология                   | Порт | Назначение                          |
|------------------------|------------------------------|------|-------------------------------------|
| `asg-core`             | Scala 3 + Akka Typed         | 8080 (REST), 9090 (gRPC) | Основной сервис: API + agents   |
| `postgres`            | PostgreSQL 16                | 5432 | MappingRegistry, PROV-O audit      |
| `redis`               | Redis 7                      | 6379 | LRU-кэш трансляций                  |
| `jena-fuseki`         | Apache Jena 4.10             | 3030 | SPARQL endpoint, OWL онтологии     |
| `prometheus`          | Prometheus 2.54              | 19090 | Сбор метрик                       |
| `grafana`             | Grafana 11                   | 3000  | Визуализация                       |
| `loki`                | Loki 3.1                     | 3100  | Агрегация логов                    |
| `jaeger`              | Jaeger 1.60                  | 16686 | Distributed tracing                |

### Связи между контейнерами

- `asg-core` → `postgres` (JDBC, порт 5432): чтение/запись mappings и
  PROV-O.
- `asg-core` → `redis` (RESP, порт 6379): LRU-кэш.
- `asg-core` → `jena-fuseki` (HTTP, порт 3030): SPARQL и онтологии.
- `asg-core` → `prometheus` (HTTP, порт 19090): публикация метрик
  через `/metrics`.
- `asg-core` → `loki` (HTTP, порт 3100): push-логирование через
  Promtail.
- `asg-core` → `jaeger` (OTLP/gRPC, порт 4317): трассировка.
- `grafana` → `prometheus`, `loki`, `jaeger`: запросы для
  визуализации.

### Файл
`docs/diagrams/02-containers.svg` — 1600×1000 px.

---

## 4. Диаграмма 3. Компоненты asg-core

### Назначение
Детализирует внутреннюю структуру `asg-core` как набора
взаимодействующих Scala-компонентов (пакетов и классов).

### Ключевые компоненты

- `Main.scala` — точка входа, инициализация Akka-системы.
- `api/RestApi.scala` — REST-маршруты (Akka HTTP).
- `api/GrpcServer.scala` — gRPC-сервер (Akka gRPC).
- `agents/MatcherAgent.scala` — BM25 + BERT для ранжирования
  кандидатов соответствий.
- `agents/ArbiterAgent.scala` — конечный автомат, координирующий
  агентов.
- `agents/ValidatorAgent.scala` — оркестратор SHACL + OWL2RL +
  SPARQL.
- `agents/LearnerAgent.scala` — обучаемый агент для эскалации.
- `ontology/OntologyRegistry.scala` — загрузка и кэширование
  OWL-онтологий.
- `ontology/MappingRegistry.scala` — управление cross-domain
  mappings.
- `ontology/CacheManager.scala` — LRU-кэш Redis + Caffeine.
- `verification/ShaclValidator.scala` — SHACL-валидация через
  Apache Jena.
- `verification/Owl2RlReasoner.scala` — OWL 2 RL консистентность.
- `verification/SparqlVerifier.scala` — SS-1 / SS-2' через SPARQL.
- `hotl/HotlContour.scala` — трёхуровневый эскалационный контур.
- `provenance/ProvORecorder.scala` — PROV-O аудита трансляций.

### Связи (упрощённо)

```
RestApi / GrpcServer
   ↓
ArbiterAgent (FSM: S0 → S1 → S2 → S3 → S0)
   ↓                 ↓                ↓
MatcherAgent  ValidatorAgent    LearnerAgent
   ↓                 ↓                ↓
MappingRegistry  ShaclValidator   (PPO training)
   ↓                 ↓
OntologyRegistry  Owl2RlReasoner
   ↓                 ↓
Jena Fuseki      SparqlVerifier
                   ↓
              (SS-1, SS-2')

CacheManager (Redis) — общий для всех
ProvORecorder (Postgres) — общий для всех
```

### Файл
`docs/diagrams/03-components.svg` — 1600×1200 px.

---

## 5. Диаграмма 4. Многоагентная архитектура

### Назначение
Фокусируется на взаимодействии четырёх агентов (Matcher, Arbiter,
Validator, Learner) как независимых Akka-акторов, обменивающихся
сообщениями.

### Ключевые компоненты

- **MatcherAgent** — получает `MatchRequest`, возвращает
  `MatchResponse(candidates: List[Candidate])`. Использует
  BM25 (для лексического совпадения) и BERT (для семантического
  совпадения) с порогом 0.6.
- **ArbiterAgent** — координатор. Принимает `TranslateRequest`,
  делегирует Matcher, затем Validator, при необходимости —
  Learner. Реализован как FSM с состояниями S0 (idle), S1
  (matching), S2 (validating), S3 (escalating).
- **ValidatorAgent** — получает `ValidateRequest`, возвращает
  `ValidateResponse(verdict, violations)`. Применяет три уровня
  проверки последовательно (ранний возврат при первом `Violation`).
- **LearnerAgent** — получает `EscalationRequest`, использует
  обучаемую модель для предложения нового mapping'а. Записывает
  feedback в БД, раз в 100 feedback'ов обновляет веса (PPO).

### Поток сообщений

```
TranslateRequest → Arbiter (S0)
Arbiter → Matcher: MatchRequest (S0 → S1)
Matcher → Arbiter: MatchResponse(candidates) (S1 → S2)
Arbiter → Validator: ValidateRequest (S2)
Validator → Arbiter: ValidateResponse(OK | Violation | Warning)
  ├── OK → Arbiter: вернуть результат (S2 → S0)
  ├── Violation → Arbiter: вернуть 409 (S2 → S0)
  └── Warning → Arbiter → Learner: EscalationRequest (S2 → S3)
                Learner → Arbiter: EscalationResponse (S3 → S0)
```

### Файл
`docs/diagrams/04-multi-agent.svg` — 1600×800 px.

---

## 6. Диаграмма 5. Трёхуровневая верификация

### Назначение
Иллюстрирует три уровня проверки запросов в ASG: (1) SHACL для
структурных ограничений (OM-1, OM-2, OM-3); (2) OWL 2 RL для
консистентности онтологии; (3a) SPARQL для семантических
инвариантов (SS-1, SS-2'); (3b) Lean 4 для универсальной
верификации Теоремы 1.1 (offline).

### Ключевые компоненты

| Уровень   | Технология             | Что проверяется                          | Время (p95) |
|-----------|------------------------|------------------------------------------|-------------|
| 1         | SHACL (Apache Jena)    | OM-1, OM-2, OM-3 на конкретных данных    | 150 ms      |
| 2         | OWL 2 RL (Jena Rules)  | Консистентность O₁ ∪ O₂ ∪ m              | 250 ms      |
| 3a        | SPARQL (Apache Jena ARQ) | SS-1, SS-2' приближённо                 | 80 ms       |
| 3b        | Lean 4 + Mathlib4     | Теорема 1.1 — универсальная SS-1         | offline     |

### Поток выполнения

```
TranslateRequest → ValidatorAgent
   ↓
Шаг 1: ShaclValidator.validate(graph, shapes)
       ├── violations found → return 409 Conflict
       └── no violations → continue
   ↓
Шаг 2: Owl2RlReasoner.checkConsistency(graph)
       ├── inconsistent → return 422 Unprocessable Entity
       └── consistent → continue
   ↓
Шаг 3: SparqlVerifier.verify(graph, "sparql/ss1-verify.rq")
       SparqlVerifier.verify(graph, "sparql/ss2-verify.rq")
       ├── violations found → log Warning, continue
       └── no violations → continue
   ↓
return 200 OK + translated query
```

### Файл
`docs/diagrams/05-verification.svg` — 1600×1000 px.

---

## 7. Диаграмма 6. Контур Hot-L

### Назначение
Описывает трёхуровневый эскалационный контур Hot-L: (1) синхронная
валидация; (2) асинхронная с кэшированием; (3) обучение.

### Ключевые компоненты

- **Hot-L (tier 1)** — синхронная SHACL + OWL2RL проверка, ≤ 500 ms
  p95. Покрывает ~95 % запросов.
- **Hot-L+R (tier 2)** — асинхронная SPARQL-верификация, кэшируется
  на 5 мин. Покрывает ~4 % запросов, требующих глубокой проверки.
- **Learner (tier 3)** — обучаемый агент для оставшихся ~1 %
  запросов, не покрываемых первыми двумя уровнями. Использует PPO
  для обновления политики перевода.

### Эскалация

```
Hot-L (≤ 500 ms)
   ├── OK → return success
   ├── Violation → return 409
   └── Warning → escalate to Hot-L+R

Hot-L+R (≤ 5 s)
   ├── OK (cached for 5 min) → return success
   ├── Violation → return 409
   └── Warning → escalate to Learner

Learner (≤ 60 s)
   ├── OK (feedback recorded) → return success
   ├── Need human review → return 202 Accepted (async)
   └── Timeout → return 503 Service Unavailable
```

### Файл
`docs/diagrams/06-hotl-contour.svg` — 1600×800 px.

---

## 8. Диаграмма 7. Поток данных запроса (sequence)

### Назначение
Sequence-диаграмма одного полного запроса от клиента до ответа,
показывающая взаимодействия между всеми компонентами.

### Участники

1. Client (ВИС)
2. REST API (`asg-core`)
3. ArbiterAgent
4. MatcherAgent
5. MappingRegistry (Postgres)
6. ValidatorAgent
7. ShaclValidator
8. Owl2RlReasoner
9. SparqlVerifier
10. ProvORecorder (Postgres)
11. CacheManager (Redis)
12. Jaeger (tracing)

### Краткое описание шагов

1. Client → REST API: `POST /api/v1/translate { source: tax:v1, target: reg:v1, query: "∃ hasTaxId.Taxpayer" }`
2. REST API → CacheManager: проверка кэша (cache-hit → шаг 13).
3. REST API → ArbiterAgent: `TranslateRequest`.
4. ArbiterAgent → MatcherAgent: `MatchRequest`.
5. MatcherAgent → MappingRegistry: запрос существующих mappings.
6. MatcherAgent → ArbiterAgent: `MatchResponse(candidates)`.
7. ArbiterAgent → ValidatorAgent: `ValidateRequest`.
8. ValidatorAgent → ShaclValidator: SHACL-проверка.
9. ValidatorAgent → Owl2RlReasoner: OWL 2 RL.
10. ValidatorAgent → SparqlVerifier: SS-1 + SS-2'.
11. ValidatorAgent → ArbiterAgent: `ValidateResponse(OK)`.
12. ArbiterAgent → REST API: `TranslateResponse(translated_query)`.
13. REST API → CacheManager: сохранение в кэш (TTL 5 мин).
14. REST API → ProvORecorder: запись PROV-O audit.
15. REST API → Client: `200 OK { translated_query: "∃ hasAddress.Person" }`.

Каждый шаг сопровождается Jaeger-trace span с `trace_id` для
сквозной трассировки.

### Файл
`docs/diagrams/07-data-flow.svg` — 1600×1600 px.

---

## 9. Диаграмма 8. FSM ArbiterAgent

### Назначение
State-диаграмма конечного автомата ArbiterAgent: 4 состояния (S0–S3),
переходы и таймауты.

### Состояния

- **S0 (Idle)** — ожидание нового `TranslateRequest`. Начальное
  состояние.
- **S1 (Matching)** — отправлен `MatchRequest` MatcherAgent, ожидание
  `MatchResponse`. Таймаут: 2 сек.
- **S2 (Validating)** — отправлен `ValidateRequest` ValidatorAgent,
  ожидание `ValidateResponse`. Таймаут: 5 сек.
- **S3 (Escalating)** — отправлен `EscalationRequest` LearnerAgent,
  ожидание `EscalationResponse`. Таймаут: 30 сек.

### Переходы

| Из | В | Событие                              |
|----|---|--------------------------------------|
| S0 | S1 | `TranslateRequest` получен          |
| S1 | S0 | `MatchResponse` (empty) → 404       |
| S1 | S2 | `MatchResponse` (non-empty)         |
| S1 | S0 | Timeout 2 sec → 503                  |
| S2 | S0 | `ValidateResponse(OK)` → 200        |
| S2 | S0 | `ValidateResponse(Violation)` → 409 |
| S2 | S3 | `ValidateResponse(Warning)`         |
| S2 | S0 | Timeout 5 sec → 503                  |
| S3 | S0 | `EscalationResponse(OK)` → 200      |
| S3 | S0 | `EscalationResponse(NeedHumanReview)` → 202 |
| S3 | S0 | Timeout 30 sec → 503                 |

### Файл
`docs/diagrams/08-arbiter-fsm.svg` — 1200×800 px.

---

## 10. Диаграмма 9. Развёртывание K8s

### Назначение
Deployment-диаграмма production-кластера Kubernetes (Yandex Cloud
Managed K8s): namespace, deployments, services, ingress, HPA,
configmaps, secrets.

### Ключевые объекты

| Объект                | Тип         | Назначение                                |
|-----------------------|-------------|--------------------------------------------|
| `asg-prod`           | Namespace   | Продакшен-окружение                        |
| `asg-core`           | Deployment  | ASG сервис (replicas: 4–12, HPA)           |
| `asg-core`           | Service     | ClusterIP, port 8080/9090                  |
| `asg-cache`          | StatefulSet | Redis 7 (replicas: 3, sentinel)            |
| `asg-db`             | StatefulSet | PostgreSQL 16 (replicas: 3, primary+replica)|
| `jena-fuseki`        | Deployment  | OWL/SPARQL (replicas: 2)                  |
| `asg-ingress`        | Ingress     | nginx-ingress, TLS-termination             |
| `asg-hpa`            | HPA         | target CPU 70 %, scale 4 → 12              |
| `asg-config`         | ConfigMap   | application.conf, logback.xml              |
| `asg-secrets`        | Secret      | JWT_SECRET, POSTGRES_PASSWORD              |

### Связи

- Ingress → asg-core (Service): HTTP/HTTPS с TLS.
- asg-core → asg-cache (Service): RESP на порт 6379.
- asg-core → asg-db (Service): JDBC на порт 5432.
- asg-core → jena-fuseki (Service): HTTP на порт 3030.
- HPA → asg-core (Deployment): масштабирование.

### Файл
`docs/diagrams/09-k8s-deployment.svg` — 1600×1200 px.

---

## 11. Диаграмма 10. Observability stack

### Назначение
Демонстрирует взаимодействие компонентов observability-стека ASG:
сбор метрик, логов, трассировок, алерты и визуализация.

### Ключевые компоненты

- **Prometheus** — сбор метрик через `/metrics` (scrape каждые 15 с).
- **Grafana** — визуализация (3 дашборда: operational, service,
  business).
- **Loki** — агрегация логов (push через Promtail).
- **Jaeger** — distributed tracing (OTLP).
- **Alertmanager** — маршрутизация алертов (P1/P2/P3) в Slack,
  PagerDuty, email.
- **cAdvisor** — метрики контейнеров (scrape Prometheus).
- **node-exporter** — метрики узлов K8s.

### Поток данных

```
asg-core ──(/metrics, 15s)──→ Prometheus ──→ Grafana
asg-core ──(push logs)────→ Promtail ──→ Loki ──→ Grafana
asg-core ──(OTLP)────────→ Jaeger ────→ Grafana (Trace view)
Prometheus ──(alerts)────→ Alertmanager ──→ Slack/PagerDuty/Email
cAdvisor ──(/metrics)──→ Prometheus
node-exporter ──(/metrics)──→ Prometheus
```

### Файл
`docs/diagrams/10-observability.svg` — 1600×1000 px.

---

## 12. Инструкция по регенерации SVG

Все диаграммы хранятся в двух форматах:
1. Исходный Mermaid-код — встроен в `docs/architecture.md` (блоки
   ` ```mermaid `).
2. Сгенерированные SVG — в `docs/diagrams/*.svg`.

Для регенерации SVG из Mermaid:

```bash
# Установить Mermaid CLI
npm install -g @mermaid-js/mermaid-cli

# Регенерировать все диаграммы
cd /home/z/my-project/download/asg-repository
bash docs/diagrams/regenerate.sh
```

Скрипт `regenerate.sh` (если не существует — будет создан в Sprint 3):

```bash
#!/usr/bin/env bash
set -euo pipefail

mmDC=mmdc
SRC=docs/architecture.md
OUT=docs/diagrams

declare -A DIAGRAMS=(
  ["01-system-context"]="Context"
  ["02-containers"]="Container"
  ["03-components"]="Component"
  ["04-multi-agent"]="MultiAgent"
  ["05-verification"]="Verification"
  ["06-hotl-contour"]="HotL"
  ["07-data-flow"]="DataFlow"
  ["08-arbiter-fsm"]="ArbiterFSM"
  ["09-k8s-deployment"]="K8sDeployment"
  ["10-observability"]="Observability"
)

for name in "${!DIAGRAMS[@]}"; do
  echo "Generating $name.svg ..."
  $mmDC -i $SRC -o "$OUT/$name.svg" -t neutral -b transparent
done
```

---

## 13. Связь с C4 Model

Нотация диаграмм соответствует стандарту C4 Model (Simon Brown,
2018):
- **Context** (диаграмма 1) — внешний взгляд на систему.
- **Container** (диаграмма 2, 10) — высокоуровневые компоненты
  (сервисы, базы данных, шины).
- **Component** (диаграмма 3, 4, 5, 6, 8) — внутренняя структура
  одного контейнера (asg-core).
- **Code** (не отображается) — классы и методы, описаны в исходном
  коде и автогенерируемой Scaladoc-документации.

Дополнительно:
- **Sequence** (диаграмма 7) — для временной диаграммы потока
  данных.
- **Deployment** (диаграмма 9) — для инфраструктурного
  развёртывания.

---

## 14. Библиографические ссылки

1. Brown S. **The C4 Model for Visualising Software Architecture.** Leanpub, 2018. — основа C4-нотации.
2. Richards M., Ford N. **Fundamentals of Software Architecture.** O'Reilly, 2020. — обзор архитектурных стилей.
3. Newman S. **Building Microservices.** 2nd ed. O'Reilly, 2021. — паттерны микросервисов.
4. W3C. **PROV-O: The PROV Ontology.** W3C Recommendation, 30 April 2013. — модель provenance.
5. Akka. **Akka Typed Documentation.** Lightbend, 2024. — модель акторов.

Полный аннотированный список — в [Приложении 12](appendix-12-annotated-bibliography.md).
