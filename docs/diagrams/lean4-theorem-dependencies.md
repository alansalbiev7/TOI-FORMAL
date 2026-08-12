# Граф зависимостей теорем Lean 4 (TOI)

## Назначение

Диаграмма показывает структуру зависимостей формальных результатов
теории интероперабельности (TOI — Theory of Interoperability),
формализованных в Lean 4. Корень зависимости — `Axioms.lean`,
содержащий аксиомы семантической консистентности. От него
расходятся зависимости к леммам 1.1–1.4 и далее к основным
теоремам T11_Finite, T11_Infinite, T12, T21, T22, T31.
Контрмодели A–E демонстрируют независимость аксиом (для каждой
аксиомы существует контрмодель, в которой все остальные аксиомы
выполняются, а эта — нет).

## Диаграмма

```mermaid
graph TD
  classDef axiom  fill:#37474F,stroke:#263238,color:#FFFFFF
  classDef lemma  fill:#00838F,stroke:#004D40,color:#FFFFFF
  classDef thm    fill:#1168BD,stroke:#0B4884,color:#FFFFFF
  classDef dual   fill:#6A1B9A,stroke:#4A148C,color:#FFFFFF
  classDef counter fill:#EF6C00,stroke:#BF360C,color:#FFFFFF

  Ax["Axioms.lean<br/>-----<br/>Axiom 1: Preservation of ⊤<br/>Axiom 2: Preservation of ¬<br/>Axiom 3: Boolean extensionality<br/>Axiom 4: Role restriction"]:::axiom

  L11["Lemma 1.1<br/>TopPreserved.lean"]:::lemma
  L12["Lemma 1.2<br/>NegPreserved.lean"]:::lemma
  L13["Lemma 1.3<br/>BoolExt.lean"]:::lemma
  L14["Lemma 1.4<br/>RoleRestrict.lean"]:::lemma

  T11F["T11_Finite.lean<br/>-----<br/>Soundness: finite case"]:::thm
  T11I["T11_Infinite.lean<br/>-----<br/>Soundness: infinite case<br/>(uses Priestley duality)"]:::dual
  T12["T12.lean<br/>-----<br/>Completeness"]:::thm
  T21["T21.lean<br/>-----<br/>Decidability of SS-1"]:::thm
  T22["T22.lean<br/>-----<br/>Decidability of SS-2'"]:::thm
  T31["T31.lean<br/>-----<br/>Complexity bound<br/>(polynomial)"]:::thm

  Priestley{{"Priestley duality<br/>(внешний результат,<br/>Distributive Lattice)}}:::dual

  CA["Countermodel A<br/>-----<br/>Axiom 1 нарушена,<br/>остальные выполняются"]:::counter
  CB["Countermodel B<br/>-----<br/>Axiom 2 нарушена"]:::counter
  CC["Countermodel C<br/>-----<br/>Axiom 3 нарушена"]:::counter
  CD["Countermodel D<br/>-----<br/>Axiom 4 нарушена"]:::counter
  CE["Countermodel E<br/>-----<br/>Независимость T11_Infinite<br/>от Priestley"]:::counter

  Ax --> L11
  Ax --> L12
  Ax --> L13
  Ax --> L14

  L11 --> T11F
  L12 --> T11F
  L13 --> T11F
  L14 --> T11F

  T11F --> T11I
  Priestley --> T11I

  Ax --> T12
  T11F --> T12
  T11I --> T12

  Ax --> T21
  L11 --> T21
  L12 --> T21

  Ax --> T22
  L13 --> T22

  Ax --> T31
  T21 --> T31
  T22 --> T31

  CA -.->|demonstrates<br/>independence| Ax
  CB -.->|demonstrates<br/>independence| Ax
  CC -.->|demonstrates<br/>independence| Ax
  CD -.->|demonstrates<br/>independence| Ax
  CE -.->|demonstrates<br/>independence| T11I
```

## Описание узлов

### Аксиомы (`Axioms.lean`)

Четыре аксиомы теории интероперабельности TOI:

1. **Axiom 1** — сохранение верхней грани `⊤` при отображении
   (соответствует инварианту SS-1 в SPARQL-верификации).
2. **Axiom 2** — сохранение отрицания `¬` (соответствует SS-2').
3. **Axiom 3** — булева экстенсиональность.
4. **Axiom 4** — ролевое ограничение (RoleRestriction).

См. [`TOI/Axioms.lean`](../../TOI/Axioms.lean).

### Леммы

| Лемма     | Файл                              | Доказана из    |
|-----------|-----------------------------------|----------------|
| Lemma 1.1 | `Lemmas/TopPreserved.lean`        | Axiom 1        |
| Lemma 1.2 | `Lemmas/NegPreserved.lean`        | Axiom 2        |
| Lemma 1.3 | `Lemmas/BoolExt.lean`             | Axiom 3        |
| Lemma 1.4 | `Lemmas/RoleRestrict.lean`        | Axiom 4        |

### Теоремы

| Теорема        | Назначение                                |
|----------------|-------------------------------------------|
| `T11_Finite`   | Корректность SS-1/SS-2' для конечных моделей |
| `T11_Infinite` | Расширение на бесконечный случай (Priestley duality) |
| `T12`          | Полнота: верификация → семантическая корректность |
| `T21`          | Разрешимость SS-1 (через T11_Finite + Lemma 1.1) |
| `T22`          | Разрешимость SS-2' (через T11_Finite + Lemma 1.3) |
| `T31`          | Полиномиальная верхняя граница сложности (через T21, T22) |

### Контрмодели A–E

Контрмодели демонстрируют **независимость** аксиом и теорем:
- A, B, C, D — нарушают ровно одну из 4 аксиом (остальные
  выполняются), что доказывает их независимость.
- E — модель, в которой `T11_Infinite` не выполняется без
  предположения Priestley duality, что демонстрирует его
  необходимость для бесконечного случая.

См. [`TOI/Countermodels/`](../../TOI/Countermodels/).

## Связанные документы

- Исходники теорем: [`TOI/`](../../TOI/)
- Lake-файл: [`lakefile.lean`](../../lakefile.lean)
- Лекция по формальным основам: [`education/lectures/lecture-04-formal-foundations.md`](../../education/lectures/lecture-04-formal-foundations.md)
- Связь с SPARQL-верификацией: [`three-tier-verification.md`](./three-tier-verification.md)

## Легенда

| Цвет         | Тип узла                                |
|--------------|-----------------------------------------|
| Серо-синий   | Аксиомы (фундамент)                     |
| Бирюзовый    | Леммы                                    |
| Синий        | Основные теоремы                        |
| Фиолетовый   | Внешние результаты (Priestley duality)  |
| Оранжевый    | Контрмодели (демонстрация независимости)|
