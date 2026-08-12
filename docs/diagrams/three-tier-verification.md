# Трёхуровневая верификация отображений

## Назначение

Flowchart показывает конвейер верификации кандидата отображения в
`ValidatorAgent`. Три уровня проверки строго упорядочены по
возрастанию сложности: ранний выход (early exit) на максимально
дешёвом уровне. Это обеспечивает O(1) отклонение заведомо
непригодных кандидатов и запускает дорогую SPARQL-верификацию
только для уже синтаксически и логически корректных отображений.

## Диаграмма

```mermaid
flowchart TD
  classDef input   fill:#00838F,stroke:#004D40,color:#FFFFFF
  classDef level   fill:#6A1B9A,stroke:#4A148C,color:#FFFFFF
  classDef exit    fill:#C62828,stroke:#B71C1C,color:#FFFFFF
  classDef ok      fill:#2E7D32,stroke:#1B5E20,color:#FFFFFF
  classDef warn    fill:#EF6C00,stroke:#BF360C,color:#FFFFFF
  classDef out     fill:#37474F,stroke:#263238,color:#FFFFFF

  In(["Вход: mapping candidate m_i<br/>(source_ontology, target_ontology, alignment)"]):::input

  subgraph L1["Уровень 1 — SHACL (синтаксис / cardinality)"]
    direction TB
    L1Run["ShaclValidator.validate(m_i)<br/>shapes: om1-hierarchy.ttl,<br/>om2-union.ttl, om2-intersection.ttl, om3-role.ttl"]:::level
    L1Decision{"Violations?"}
    L1Run --> L1Decision
  end

  subgraph L2["Уровень 2 — OWL2 RL (логическая консистентность)"]
    direction TB
    L2Run["Owl2RlReasoner.check(m_i)<br/>OWL 2 RL profile rules"]:::level
    L2Decision{"Inconsistent?"}
    L2Run --> L2Decision
  end

  subgraph L3["Уровень 3 — SPARQL (семантические инварианты)"]
    direction TB
    L3Run1["SparqlVerifier.run(ss1-verify.rq)"]:::level
    L3Run2["SparqlVerifier.run(ss2-verify.rq)"]:::level
    L3Agg["Aggregate violations<br/>v1 = count(ss1)<br/>v2 = count(ss2)"]:::level
    L3Run1 --> L3Agg
    L3Run2 --> L3Agg
    L3Decision{"v1 + v2 = 0?"}
    L3Agg --> L3Decision
  end

  OutInvalid(["Invalid<br/>(return to ArbiterAgent,<br/>try next candidate)"]):::exit
  OutValid(["Valid<br/>(commit to MappingRegistry)"]):::ok
  OutWarning(["Warning<br/>(escalate to HOTL-контур)"]):::warn

  In --> L1Run

  L1Decision -- "Yes (violations > 0)" --> OutInvalid
  L1Decision -- "No" --> L2Run

  L2Decision -- "Yes (inconsistent)" --> OutInvalid
  L2Decision -- "No" --> L3Run1

  L3Decision -- "Yes (no violations)" --> OutValid
  L3Decision -- "No, but only warnings<br/>(v2 > 0, v1 = 0)" --> OutWarning
  L3Decision -- "No, hard errors<br/>(v1 > 0)" --> OutInvalid
```

## Описание уровней

### Уровень 1 — SHACL

- **Компонент**: `ShaclValidator`
  ([`asg-core/.../verification/ShaclValidator.scala`](../../asg-core/src/main/scala/ru/smev/asg/verification/ShaclValidator.scala)).
- **Шейпы**: `shapes/om1-hierarchy.ttl`, `shapes/om2-union.ttl`,
  `shapes/om2-intersection.ttl`, `shapes/om3-role.ttl`.
- **Что проверяет**: кардинальности, типы, обязательные свойства,
  соответствие иерархии классов (OM1), объединению/пересечению (OM2),
  ролям (OM3).
- **Стоимость**: O(|shapes| × |m_i|), обычно ≤ 10 ms.
- **Early exit**: при любом нарушении — немедленный `Invalid`.

### Уровень 2 — OWL2 RL

- **Компонент**: `Owl2RlReasoner`
  ([`asg-core/.../verification/Owl2RlReasoner.scala`](../../asg-core/src/main/scala/ru/smev/asg/verification/Owl2RlReasoner.scala)).
- **Профиль**: OWL 2 RL (rule-based reasoning, полиномиальная
  сложность).
- **Что проверяет**: логическую консистентность объединённой
  A-box (исходная + целевая онтологии + candidate mapping).
- **Стоимость**: O(|triples|²), обычно 50–200 ms.
- **Early exit**: при `Inconsistent = true` — немедленный `Invalid`.

### Уровень 3 — SPARQL

- **Компонент**: `SparqlVerifier`
  ([`asg-core/.../verification/SparqlVerifier.scala`](../../asg-core/src/main/scala/ru/smev/asg/verification/SparqlVerifier.scala)).
- **Запросы**:
  - [`sparql/ss1-verify.rq`](../../sparql/ss1-verify.rq) —
    проверка семантического инварианта SS-1
    (сохранение верхней грани `⊤`).
  - [`sparql/ss2-verify.rq`](../../sparql/ss2-verify.rq) —
    проверка SS-2' (сохранение отрицания, булевое расширение).
- **Что проверяет**: тонкие семантические инварианты теории
  интероперабельности TOI (см. [`TOI/`](../../TOI/)).
- **Стоимость**: зависит от размера A-box, обычно 100–500 ms.
- **Агрегация нарушений**:
  - `v1 = count(SS-1 violations)` — критические,
  - `v2 = count(SS-2' violations)` — мягкие (warning).
- **Решение**:
  - `v1 = 0` и `v2 = 0` → **Valid**;
  - `v1 = 0` и `v2 > 0` → **Warning** (HOTL);
  - `v1 > 0` → **Invalid**.

## Связанные документы

- Конечный автомат обработки: [`asg-fsm-s0-s3.md`](./asg-fsm-s0-s3.md)
- Компоненты верификаторов: [`c4-level3-component.md`](./c4-level3-component.md)
- Руководство по верификации: [`../verification-guide.md`](../verification-guide.md)
- Формальные основы (TOI): [`../../TOI/`](../../TOI/)
