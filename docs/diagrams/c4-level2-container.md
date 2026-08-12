# C4 Level 2 — Контейнеры ASG

## Назначение

Диаграмма **Container** (C4 Level 2) показывает внутреннее устройство
системы ASG на уровне независимо развёртываемых единиц (контейнеров):
основной сервис ASG (Scala/Akka), хранилище онтологий Apache Jena
Fuseki, кэш Redis, базу PostgreSQL, а также стек наблюдаемости
(Prometheus, Grafana, Loki, Jaeger). Внешние системы (СМЭВ, ЕГИСЗ,
ФНС и т.д.) здесь представлены как контексты — подробно они раскрыты
в [`c4-level1-system-context.md`](./c4-level1-system-context.md).

## Диаграмма

```mermaid
graph TB
  classDef person fill:#08427B,stroke:#052E56,color:#FFFFFF
  classDef extsys fill:#999999,stroke:#6B6B6B,color:#FFFFFF
  classDef asg    fill:#1168BD,stroke:#0B4884,color:#FFFFFF
  classDef store  fill:#F5F5F5,stroke:#666666,color:#333333
  classDef obs    fill:#2E8B57,stroke:#1B5E20,color:#FFFFFF
  classDef ont    fill:#8A6D3B,stroke:#5C4318,color:#FFFFFF

  subgraph Boundary["Граница системы ASG (k8s namespace: asg)"]
    direction TB

    ASGCore["asg-core<br/>-----<br/>[Scala 3 · Akka Typed · cats-effect]<br/>gRPC + REST API<br/>контур агентов + верификаторов"]:::asg

    Jena["Apache Jena Fuseki<br/>-----<br/>[SPARQL endpoint]<br/>ontologies/*.owl<br/>cross-domain-mapping.ttl"]:::ont

    Redis[("Redis 7<br/>-----<br/>LRU cache, TTL=300s<br/>mapping candidates")]:::store
    Postgres[("PostgreSQL 15<br/>-----<br/>mapping_registry<br/>audit_log, prov-o triples")]:::store

    subgraph Observability["Стек наблюдаемости"]
      direction LR
      Prom["Prometheus<br/>-----<br/>metrics scrape 15s"]:::obs
      Grafana["Grafana<br/>-----<br/>dashboard-*"]:::obs
      Loki["Loki<br/>-----<br/>structured logs"]:::obs
      Jaeger["Jaeger<br/>-----<br/>OpenTelemetry traces"]:::obs
    end
  end

  Citizen["🧑 Гражданин"]:::person
  Operator["👷 Оператор ведомства"]:::person
  Admin["🔧 Администратор ASG"]:::person

  SMEV{{"СМЭВ"}}:::extsys
  EGISZ{{"ЕГИСЗ (FHIR)"}}:::extsys
  FNS{{"ФНС"}}:::extsys
  MVD{{"МВД"}}:::extsys

  Citizen -->|HTTP/JSON| SMEV
  Operator -->|gRPC| SMEV
  SMEV <-->|СМЭВ 3.5 SOAP/REST| ASGCore
  EGISZ <-->|REST/FHIR R4| ASGCore
  FNS <-->|СМЭВ 3.5| ASGCore
  MVD <-->|СМЭВ 3.5| ASGCore

  Admin -->|REST/OpenAPI 3 + kubectl| ASGCore

  ASGCore <-->|RESP3, pipelining| Redis
  ASGCore <-->|JDBC + HikariCP| Postgres
  ASGCore <-->|SPARQL 1.1 over HTTP| Jena

  ASGCore -.->|/metrics Prometheus exposition| Prom
  ASGCore -.->|JSON structured stdout| Loki
  ASGCore -.->|OTLP/gRPC spans| Jaeger
  Grafana -.->|query| Prom
  Grafana -.->|query| Loki
  Grafana -.->|query| Jaeger
```

## Описание контейнеров

### 1. `asg-core` — основной сервис ASG

- **Стек**: Scala 3.3, Akka Typed 2.8, cats-effect 3, http4s, grpc-java.
- **Развёртывание**: Kubernetes `StatefulSet`, 3 реплики, anti-affinity.
- **Образ**: `ghcr.io/smev/asg-core:${VERSION}` (см. `asg-core/Dockerfile`).
- **Порты**:
  - `8080/tcp` — REST/OpenAPI 3 (для администраторов и эксплуататоров);
  - `9090/tcp` — gRPC (для ведомств и СМЭВ-адаптера);
  - `9100/tcp` — `/metrics` (Prometheus exposition).
- **Ресурсы**: requests `cpu=500m, mem=1Gi`; limits `cpu=2000m, mem=4Gi`.
- **JVM флаги**: `-XX:+UseZGC -XX:MaxRAMPercentage=75`.

### 2. `Redis 7` — кэш кандидатов отображений

- **Назначение**: LRU-кэш результатов `MatcherAgent`, TTL=300s.
- **Развёртывание**: `StatefulSet` с persistence `appendonly.aof`.
- **Протокол**: RESP3 (pipelining + client-side caching).
- **Структуры**: hash `mappings:{source}:{target}`, set `candidates:{req_id}`.

### 3. `PostgreSQL 15` — хранилище MappingRegistry

- **Назначение**: постоянное хранилище онтологических отображений,
  журнала аудита, Prov-O-троек, конфигурации HOTL-контура.
- **Развёртывание**: `StatefulSet`, primary + read-replica, WAL архивирование.
- **Схемы**: `mapping_registry`, `audit`, `provenance`, `config`.

### 4. `Apache Jena Fuseki` — SPARQL endpoint

- **Назначение**: триплстор онтологий (`ontologies/*.owl`,
  `ontologies/cross-domain-mapping.ttl`), выполняет запросы
  `sparql/ss1-verify.rq` и `sparql/ss2-verify.rq` на уровне 3
  верификации.
- **Развёртывание**: `Deployment`, 1 реплика, persistent volume.
- **Порт**: `3030/tcp` (SPARQL over HTTP).

### 5. Стек наблюдаемости

- **Prometheus** — метрики ASG (15s scrape), alerting через
  `prometheus/rules.yml`.
- **Grafana** — дашборды:
  `dashboard-service.json`, `dashboard-operational.json`,
  `dashboard-business.json`.
- **Loki** — structured logs (JSON), 30 дней retention.
- **Jaeger** — OpenTelemetry distributed tracing, sampling 1% в prod,
  100% в staging.

## Связанные диаграммы

- Внешний контекст: [`c4-level1-system-context.md`](./c4-level1-system-context.md)
- Внутренние компоненты asg-core: [`c4-level3-component.md`](./c4-level3-component.md)
- Развёртывание (Helm/K8s): [`ci-cd-pipeline.md`](./ci-cd-pipeline.md)
- Словесное описание: [`../architecture.md`](../architecture.md)

## Легенда

| Цвет         | Тип контейнера                       |
|--------------|--------------------------------------|
| Синий        | Основной сервис asg-core             |
| Коричневый   | Хранилище онтологий (Jena Fuseki)    |
| Светло-серый | Хранилище данных (Redis, PostgreSQL)|
| Зелёный      | Стек наблюдаемости                   |
| Тёмно-синий  | Человек                              |
| Серый        | Внешняя система                       |
