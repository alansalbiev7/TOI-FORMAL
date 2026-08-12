# Конечный автомат ASG: S0 → S1 → S2 → S3

## Назначение

Диаграмма показывает конечный автомат (FSM) обработки
межведомственного запроса в ASG. Состояния соответствуют этапам
жизненного цикла: ожидание → matching → верификация → эскалация
HOTL. Автомат реализован в `ArbiterAgent` (см.
[`asg-core/src/main/scala/ru/smev/asg/agents/ArbiterAgent.scala`](../../asg-core/src/main/scala/ru/smev/asg/agents/ArbiterAgent.scala)).

## Диаграмма

```mermaid
stateDiagram-v2
  direction TB

  [*] --> S0

  S0: S0 — Idle (ожидание)
  S1: S1 — Matching (поиск кандидатов)
  S2: S2 — Validating (3-уровневая проверка)
  S3: S3 — Escalated (HOTL-эскалация)

  S0 --> S1: StartTranslation(request)
  S1 --> S2: MatcherResponse(candidates m1..mN)
  S2 --> S0: Valid / Accepted (candidate committed)
  S2 --> S2: Invalid (try next candidate)
  S2 --> S3: Warning OR all candidates exhausted
  S3 --> S0: OperatorDecision(commit/reject)
  S0 --> [*]: Shutdown

  note right of S1
    MatcherAgent опрашивает
    CacheManager (Redis) и
    OntologyRegistry (Jena).
    Возвращает N кандидатов.
  end note

  note right of S2
    ValidatorAgent запускает
    SHACL → OWL2RL → SPARQL.
    ArbiterAgent перебирает
    кандидаты до первого Valid.
  end note

  note right of S3
    HotlContour уведомляет
    оператора через REST.
    ProvORecorder фиксирует
    все шаги.
  end note

  note left of S0
    Любое состояние --> S0
    по срабатыванию Timeout
    (T=300s по умолчанию,
    конфигурируется в asg-config.yaml)
  end note

  S1 --> S0: Timeout
  S2 --> S0: Timeout
  S3 --> S0: Timeout
```

## Описание состояний

### S0 — Idle

- **Вход**: инициализация `ArbiterAgent` при старте или завершение
  предыдущего цикла.
- **Действие**: ожидание сообщения `StartTranslation(request)`.
- **Таймаут**: нет (состояние покоя).

### S1 — Matching

- **Вход**: `StartTranslation`.
- **Действие**: `MatcherAgent` опрашивает `CacheManager` (Redis),
  при miss — `OntologyRegistry` (Jena SPARQL), формирует список
  кандидатов `m1..mN` (N ≤ 5).
- **Выход**: `MatcherResponse(candidates)` → S2.
- **Таймаут**: 60s (если `MatcherAgent` не ответил, → S0 с ошибкой).

### S2 — Validating

- **Вход**: `MatcherResponse`.
- **Действие**: `ValidatorAgent` запускает трёхуровневую верификацию
  (см. [`three-tier-verification.md`](./three-tier-verification.md)).
- **Возможные переходы**:
  - `Valid` → S0 (кандидат коммитится в `MappingRegistry`).
  - `Invalid` → остаются ещё кандидаты? Если да, повторно S2
    со следующим кандидатом.
  - `Warning` → S3 (эскалация).
  - Все кандидаты исчерпаны → S3.
- **Таймаут**: 120s.

### S3 — Escalated (HOTL)

- **Вход**: `Warning` или исчерпание кандидатов.
- **Действие**: `HotlContour` уведомляет оператора (REST-вебхук +
  polling-конечная точка); `ProvORecorder` пишет PROV-O тройки.
- **Выход**: `OperatorDecision(commit|reject)` → S0.
- **Таймаут**: 24h (если оператор не ответил, запрос закрывается
  со статусом `Rejected` и записью в `audit_log`).

## Глобальный таймаут

Время жизни запроса ограничено `T=300s` (настраивается в
`asg-config.yaml` → `asg.request.timeout`). При срабатывании
глобального таймаута текущее состояние завершается с
`ProvORecorder.audit` и автомат возвращается в S0.

## Связанные документы

- Исходный код FSM: [`../../asg-core/src/main/scala/ru/smev/asg/agents/ArbiterAgent.scala`](../../asg-core/src/main/scala/ru/smev/asg/agents/ArbiterAgent.scala)
- Структура верификации: [`three-tier-verification.md`](./three-tier-verification.md)
- HOTL-контур: [`../../asg-core/src/main/scala/ru/smev/asg/hotl/HotlContour.scala`](../../asg-core/src/main/scala/ru/smev/asg/hotl/HotlContour.scala)
- Компонентная диаграмма: [`c4-level3-component.md`](./c4-level3-component.md)

## Легенда

| Состояние | Цвет (в рендере) | Семантика                          |
|-----------|------------------|-------------------------------------|
| S0        | зелёный          |_idle, завершён                     |
| S1        | синий            | активный поиск                      |
| S2        | жёлтый           | проверка (потенциально долгий)     |
| S3        | красный          | требует вмешательства человека     |
