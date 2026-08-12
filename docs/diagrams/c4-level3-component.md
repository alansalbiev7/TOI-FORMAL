# C4 Level 3 — Компоненты asg-core

## Назначение

Диаграмма **Component** (C4 Level 3) детализирует внутреннее
устройство контейнера `asg-core` до уровня отдельных Scala/Akka
компонентов. Здесь показаны 4 агента (`MatcherAgent`,
`ValidatorAgent`, `ArbiterAgent`, `LearnerAgent`), два реестра
(`OntologyRegistry`, `MappingRegistry`) и менеджер кэша, а также
три верификатора (`ShaclValidator`, `Owl2RlReasoner`,
`SparqlVerifier`) и два вспомогательных компонента HOTL-контура
(`HotlContour`, `ProvORecorder`).

## Диаграмма

```mermaid
graph TB
  classDef agent   fill:#1168BD,stroke:#0B4884,color:#FFFFFF
  classDef verify  fill:#6A1B9A,stroke:#4A148C,color:#FFFFFF
  classDef registry fill:#2E8B57,stroke:#1B5E20,color:#FFFFFF
  classDef hotl    fill:#EF6C00,stroke:#BF360C,color:#FFFFFF
  classDef infra   fill:#607D8B,stroke:#37474F,color:#FFFFFF
  classDef api     fill:#00838F,stroke:#004D40,color:#FFFFFF

  subgraph API["API-слой"]
    Rest["RestApi<br/>(http4s, OpenAPI 3)"]:::api
    Grpc["GrpcServer<br/>(grpc-java, СМЭВ 3.5)"]:::api
  end

  subgraph Agents["Агенты (Akka Typed)"]
    Matcher["MatcherAgent<br/>-----<br/>поиск кандидатов отображений"]:::agent
    Validator["ValidatorAgent<br/>-----<br/>3-уровневая верификация"]:::agent
    Arbiter["ArbiterAgent<br/>-----<br/>выбор валидного кандидата"]:::agent
    Learner["LearnerAgent<br/>-----<br/>адаптация метрик matching'a"]:::agent
  end

  subgraph Registries["Реестры и кэш"]
    OntoReg["OntologyRegistry<br/>-----<br/>загрузка .owl/.ttl"]:::registry
    MapReg["MappingRegistry<br/>-----<br/>постоянные отображения"]:::registry
    CacheMgr["CacheManager<br/>-----<br/>Redis LRU, TTL=300s"]:::registry
  end

  subgraph Verifiers["Верификаторы (3 уровня)"]
    Shacl["ShaclValidator<br/>-----<br/>уровень 1 (SHACL)"]:::verify
    Owl2Rl["Owl2RlReasoner<br/>-----<br/>уровень 2 (OWL2 RL)"]:::verify
    Sparql["SparqlVerifier<br/>-----<br/>уровень 3 (SS-1, SS-2')"]:::verify
  end

  subgraph HotlLayer["HOTL-контур и provenance"]
    Hotl["HotlContour<br/>-----<br/>эскалация оператору"]:::hotl
    ProvO["ProvORecorder<br/>-----<br/>W3C PROV-O тройки"]:::hotl
  end

  Rest --> Matcher
  Grpc --> Matcher

  Matcher -->|кандидаты m1..mN| Validator
  Validator --> Shacl
  Validator --> Owl2Rl
  Validator --> Sparql
  Validator -->|Valid / Invalid / Warning| Arbiter
  Arbiter -->|accepted| MapReg
  Arbiter -->|no candidate| Hotl
  Hotl -->|OperatorDecision| Arbiter
  Arbiter --> Learner
  Learner -.->|весовые коэффициенты| Matcher

  Matcher --> CacheMgr
  Matcher --> OntoReg
  Arbiter --> MapReg
  MapReg --> Postgres[("PostgreSQL")]
  CacheMgr --> Redis[("Redis")]
  OntoReg --> Jena[("Jena Fuseki<br/>SPARQL 1.1")]
  Sparql --> Jena

  Arbiter -.->|audit| ProvO
  Hotl -.->|audit| ProvO
  Validator -.->|audit| ProvO
  ProvO --> Postgres
```

## Описание компонентов

### Агенты (Akka Typed)

| Компонент         | Ответственность                                                |
|-------------------|----------------------------------------------------------------|
| `MatcherAgent`    | Поиск N кандидатов отображений по запросу; использует `OntologyRegistry` и кэш `CacheManager` |
| `ValidatorAgent`  | Запуск трёхуровневой верификации (SHACL → OWL2RL → SPARQL); возвращает `Valid`/`Invalid`/`Warning` |
| `ArbiterAgent`    | Перебор кандидатов от `MatcherAgent` до первого `Valid`; при отсутствии — эскалация в HOTL |
| `LearnerAgent`    | Адаптация весов matching'a по результатам `ArbiterAgent` (онлайн-обучение) |

### Реестры и кэш

| Компонент            | Хранилище       | Назначение                              |
|----------------------|-----------------|------------------------------------------|
| `OntologyRegistry`   | Jena Fuseki     | Загрузка `.owl`/`.ttl`, refresh без рестарта |
| `MappingRegistry`    | PostgreSQL      | Постоянные отображения, история версий    |
| `CacheManager`       | Redis           | LRU кандидатов `MatcherAgent`, TTL=300s  |

### Верификаторы (см. [`three-tier-verification.md`](./three-tier-verification.md))

| Компонент         | Уровень | Технология                                    |
|-------------------|---------|------------------------------------------------|
| `ShaclValidator`  | 1       | Apache Jena SHACL, shapes из `shapes/*.ttl`    |
| `Owl2RlReasoner`  | 2       | OWL2 RL profile, pellet-like rules              |
| `SparqlVerifier`  | 3       | SPARQL 1.1, запросы `sparql/ss1-verify.rq`, `sparql/ss2-verify.rq` |

### HOTL-контур и provenance

| Компонент       | Назначение                                                    |
|-----------------|---------------------------------------------------------------|
| `HotlContour`   | Эскалация неоднозначных кандидатов оператору через REST/gRPC; хранение `OperatorDecision` |
| `ProvORecorder` | Запись W3C PROV-O троек в PostgreSQL (схема `provenance`) для аудита |

## Связанные диаграммы

- Жизненный цикл обработки запроса: [`asg-fsm-s0-s3.md`](./asg-fsm-s0-s3.md)
- Детали верификации: [`three-tier-verification.md`](./three-tier-verification.md)
- Внешние контейнеры: [`c4-level2-container.md`](./c4-level2-container.md)
- Исходный код: [`../../asg-core/src/main/scala/ru/smev/asg/`](../../asg-core/src/main/scala/ru/smev/asg/)

## Легенда

| Цвет         | Тип компонента                    |
|--------------|-----------------------------------|
| Синий        | Агент (Akka Typed)               |
| Фиолетовый   | Верификатор                       |
| Зелёный      | Реестр / кэш                       |
| Оранжевый    | HOTL-контур и provenance          |
| Бирюзовый    | API-слой (REST/gRPC)              |
| Серо-синий   | Хранилище (внешний контейнер)     |
