# Приложение 4. Каталог SHACL-форм и SPARQL-запросов

> Настоящий каталог описывает программные артефакты трёхуровневой
> верификации Адаптивного семантического шлюза (АСШ): четыре SHACL-формы
> (структурный уровень) и два SPARQL-запроса (инвариантный уровень).
> Для каждой формы и запроса указаны: файл в репозитории, проверяемая
> аксиома (см. [Приложение 2](appendix-02-axioms-catalog.md)), уровень
> критичности (`sh:severity`), целевой класс (`sh:targetClass`),
> пример нарушения и формальное условие. Связь с монографией — §2.5.

---

## 1. Сводная таблица

| #  | Тип    | Файл                          | Проверяемая аксиома | Severity       |
|----|--------|-------------------------------|---------------------|----------------|
| 1  | SHACL  | `shapes/om1-hierarchy.ttl`     | OM-1                | `sh:Violation` |
| 2  | SHACL  | `shapes/om2-union.ttl`        | OM-2 (sup)         | `sh:Violation` |
| 3  | SHACL  | `shapes/om2-intersection.ttl` | OM-2 (inf)         | `sh:Violation` |
| 4  | SHACL  | `shapes/om3-role.ttl`          | OM-3 (∃, ∀)         | `sh:Violation` |
| 5  | SPARQL | `sparql/ss1-verify.rq`          | SS-1                | Informational  |
| 6  | SPARQL | `sparql/ss2-verify.rq`          | SS-2'               | Informational  |

Все файлы используют общий набор префиксов, объявляемых в
`shapes/om1-hierarchy.ttl` через объект `<http://example.org/toi/prefixes>`
(OWL-онтология, декларирующая префиксы для SPARQL через `sh:declare`).

---

## 2. SHACL-форма OM-1 (сохранение иерархии)

### 2.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Файл              | `shapes/om1-hierarchy.ttl`                          |
| Проверяемая аксиома | OM-1: `C ⊑ D ⟹ m(C) ⊑ m(D)`                    |
| Целевой класс     | `toi:OntologyMorphism`                              |
| Severity          | `sh:Violation`                                       |
| Реализация        | `sh:sparql` (SPARQL-based constraint)               |
| Валидатор         | `ShaclValidator` (Apache Jena SHACL 4.10)            |

### 2.2. Условие проверки

Для каждой пары отображений `(C → m(C), D → m(D))`, объявленных
через свойство `toi:hasMapping`, проверяется:
- если в `O₁` выполнено `C ⊑ D` (через `rdfs:subClassOf+`),
- то должно выполняться `m(C) ⊑ m(D)` (через `rdfs:subClassOf+`).

### 2.3. SPARQL-запрос (встроенный)

```sparql
SELECT $this ?C ?D ?mC ?mD
WHERE {
  $this toi:hasMapping ?mapC, ?mapD .
  ?mapC toi:sourceConcept ?C ;
        toi:targetConcept ?mC .
  ?mapD toi:sourceConcept ?D ;
        toi:targetConcept ?mD .
  ?C rdfs:subClassOf+ ?D .
  FILTER NOT EXISTS { ?mC rdfs:subClassOf+ ?mD }
}
```

### 2.4. Пример нарушения

Дано:
- `tax:Taxpayer ⊑ tax:Person` (в `O₁`)
- `tax:Taxpayer → reg:Person`
- `tax:Person → reg:Address`

Тогда `m(tax:Taxpayer) = reg:Person`, `m(tax:Person) = reg:Address`.
Поскольку `reg:Person ⊑ reg:Address` ложно (Person и Address — разные
классы), OM-1 нарушен. Валидатор выдаёт:

```
Violation: OM-1: иерархия концептов не сохраняется отображением m
  $this = <https://smev.ru/toi/ontology#morphism_1>
  ?C    = tax:Taxpayer
  ?D    = tax:Person
  ?mC   = reg:Person
  ?mD   = reg:Address
```

### 2.5. Связанные формы

В файле также определена вспомогательная форма `toi:MappingShape` —
проверка, что каждое отображение имеет ровно один `sourceConcept` и
ровно один `targetConcept` (через `sh:minCount 1`, `sh:maxCount 1`).

---

## 3. SHACL-форма OM-2 (объединение)

### 3.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Файл              | `shapes/om2-union.ttl`                              |
| Проверяемая аксиома | OM-2 (sup): `m(C ⊔ D) = m(C) ⊔ m(D)`             |
| Целевой класс     | `toi:OntologyMorphism`                              |
| Severity          | `sh:Violation`                                       |
| Реализация        | `sh:sparql`                                          |

### 3.2. Условие проверки

Для каждой пары отображений `(C → m(C), D → m(D))` и концепта
`C ⊔ D` (через `owl:unionOf`) проверяется:
- существует отображение `(C ⊔ D) → ?mUnion`,
- `?mUnion = m(C) ⊔ m(D)` (через `owl:unionOf` с `mC` и `mD`).

### 3.3. SPARQL-запрос (встроенный)

```sparql
SELECT $this ?C ?D ?mC ?mD ?mUnion ?expectedMUnion
WHERE {
  # Отображения C → m(C) и D → m(D)
  $this toi:hasMapping ?mapC .
  ?mapC toi:sourceConcept ?C ;
        toi:targetConcept ?mC .
  $this toi:hasMapping ?mapD .
  ?mapD toi:sourceConcept ?D ;
        toi:targetConcept ?mD .

  # C ⊔ D в O₁ (через owl:unionOf)
  ?CUnion toi:unionOf ?listCD .
  ?listCD  rdf:first ?C ;
           rdf:rest/rdf:first ?D .

  # Ожидаемое m(C) ⊔ m(D) = expectedMUnion
  ?expectedMUnion toi:unionOf ?listMCMD .
  ?listMCMD  rdf:first ?mC ;
             rdf:rest/rdf:first ?mD .

  # Существующее отображение C ⊔ D → ?mUnion
  $this toi:hasMapping ?mapUnion .
  ?mapUnion toi:sourceConcept ?CUnion ;
            toi:targetConcept ?mUnion .

  # Нарушение: mUnion ≠ expectedMUnion
  FILTER(?mUnion != ?expectedMUnion)
}
```

### 3.4. Пример нарушения

Дано:
- `tax:Taxpayer ⊔ tax:Employer = tax:EntityWithTaxId`
- `m(tax:Taxpayer) = reg:Person`
- `m(tax:Employer) = reg:Organization`
- `m(tax:EntityWithTaxId) = reg:Address`

Ожидается: `m(tax:Taxpayer ⊔ tax:Employer) = reg:Person ⊔ reg:Organization`.
Нарушение: `reg:Address ≠ reg:Person ⊔ reg:Organization`.

Валидатор выдаёт:
```
Violation: OM-2 (объединение) нарушено: m(C ⊔ D) ≠ m(C) ⊔ m(D)
  ?C    = tax:Taxpayer
  ?D    = tax:Employer
  ?mC   = reg:Person
  ?mD   = reg:Organization
  ?mUnion = reg:Address
  ?expectedMUnion = reg:Person ⊔ reg:Organization
```

---

## 4. SHACL-форма OM-2 (пересечение)

### 4.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Файл              | `shapes/om2-intersection.ttl`                      |
| Проверяемая аксиома | OM-2 (inf): `m(C ⊓ D) = m(C) ⊓ m(D)`             |
| Целевой класс     | `toi:OntologyMorphism`                              |
| Severity          | `sh:Violation`                                       |
| Реализация        | `sh:sparql`                                          |

### 4.2. Условие проверки

Аналогично проверке объединения, но через `owl:intersectionOf`:
для каждой пары `(C, D)` с `C ⊓ D = ?CIntersection` проверяется
существование отображения `?CIntersection → ?mIntersection` с
`?mIntersection = m(C) ⊓ m(D)`.

### 4.3. SPARQL-запрос (фрагмент)

```sparql
SELECT $this ?C ?D ?mC ?mD ?mIntersection ?expectedMIntersection
WHERE {
  $this toi:hasMapping ?mapC, ?mapD .
  ?mapC toi:sourceConcept ?C ; toi:targetConcept ?mC .
  ?mapD toi:sourceConcept ?D ; toi:targetConcept ?mD .
  ?CIntersection owl:intersectionOf ?listCD .
  ?listCD rdf:first ?C ; rdf:rest/rdf:first ?D .
  ?expectedMIntersection owl:intersectionOf ?listMCMD .
  ?listMCMD rdf:first ?mC ; rdf:rest/rdf:first ?mD .
  $this toi:hasMapping ?mapInter .
  ?mapInter toi:sourceConcept ?CIntersection ;
            toi:targetConcept ?mIntersection .
  FILTER(?mIntersection != ?expectedMIntersection)
}
```

### 4.4. Пример нарушения

Дано:
- `health:Patient ⊓ health:Doctor = health:PatientDoctor` (гипотетический случай)
- `m(health:Patient) = reg:Person`
- `m(health:Doctor) = reg:Official`
- `m(health:PatientDoctor) = reg:Person`

Ожидается: `reg:Person ⊓ reg:Official`.
Нарушение: `reg:Person ⊇ reg:Person ⊓ reg:Official`, но не равно (если
`reg:Person ⊓ reg:Official` определён как отдельный класс).

Валидатор выдаёт:
```
Violation: OM-2 (пересечение) нарушено
```

---

## 5. SHACL-форма OM-3 (ролевые ограничения)

### 5.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Файл              | `shapes/om3-role.ttl`                               |
| Проверяемая аксиома | OM-3 (∃): `m(∃ r.C) = ∃ m(r).m(C)`              |
|                    | OM-3 (∀): `m(∀ r.C) = ∀ m(r).m(C)`              |
| Целевой класс     | `toi:OntologyMorphism`                              |
| Severity          | `sh:Violation`                                       |
| Реализация        | две `sh:sparql` формы: `OM3RoleExistsShape` и
                    `OM3RoleForallShape`                                |

### 5.2. Условие проверки (для ∃)

Для каждого отображения `r → m(r)` (через `toi:hasRoleMapping`) и
отображения концепта `C → m(C)`, проверяется:
- концепт `∃ r.C` представлен в `O₁` как `owl:Restriction` с
  `owl:onProperty r` и `owl:someValuesFrom C`,
- существует отображение `∃ r.C → ?existsRC` через `toi:hasMapping`,
- `?existsRC` является `owl:Restriction` с
  `owl:onProperty m(r)` и `owl:someValuesFrom m(C)`.

### 5.3. SPARQL-запрос (для ∃, фрагмент)

```sparql
SELECT $this ?r ?C ?mR ?mC ?existsRC ?expectedMExists
WHERE {
  $this toi:hasRoleMapping ?roleMap .
  ?roleMap toi:sourceRole ?r ; toi:targetRole ?mR .
  $this toi:hasMapping ?mapC .
  ?mapC toi:sourceConcept ?C ; toi:targetConcept ?mC .

  # ∃ r.C в O₁
  ?existsRC a owl:Restriction ;
            owl:onProperty ?r ;
            owl:someValuesFrom ?C .

  # Ожидаемое ∃ m(r).m(C) в O₂
  ?expectedMExists a owl:Restriction ;
                   owl:onProperty ?mR ;
                   owl:someValuesFrom ?mC .

  # Существующее отображение ∃ r.C → ?mExists
  $this toi:hasMapping ?mapExists .
  ?mapExists toi:sourceConcept ?existsRC ;
             toi:targetConcept ?mExists .

  FILTER(?mExists != ?expectedMExists)
}
```

### 5.4. Пример нарушения

Дано:
- `tax:∃ registeredWith.TaxAuthority` (концепт «зарегистрирован в
  налоговом органе»)
- `m(tax:registeredWith) = reg:hasAddress`
- `m(tax:TaxAuthority) = reg:Address`
- `m(tax:∃ registeredWith.TaxAuthority) = reg:Person`

Ожидается: `reg:∃ hasAddress.Address` (концепт «имеющий адрес»).
Нарушение: `reg:Person ≠ reg:∃ hasAddress.Address` (Person может не
иметь адрес, если адрес не обязателен).

Валидатор выдаёт:
```
Violation: OM-3 (∃) нарушено: m(∃ r.C) ≠ ∃ m(r).m(C)
```

### 5.5. Дополнительная проверка ∀

Аналогичная форма `OM3RoleForallShape` проверяет:
- `∀ r.C` в `O₁` представлено как `owl:Restriction` с
  `owl:onProperty r` и `owl:allValuesFrom C`,
- `m(∀ r.C) = ∀ m(r).m(C)` — аналогично через `owl:allValuesFrom` в
  целевой онтологии.

---

## 6. SPARQL-запрос SS-1 (семантическая инвариантность)

### 6.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Файл              | `sparql/ss1-verify.rq`                               |
| Проверяемая аксиома | SS-1: `O₁ ⊨ q ⟺ O₂ ⊨ m(q)`                       |
| Severity          | Informational (не блокирующая)                       |
| Исполнение        | `arq --data=... --query=sparql/ss1-verify.rq`        |

### 6.2. Условие проверки

Для каждого морфизма `?morphism` и каждого класса `?query` (как
запроса в `O₁`):
- проверяется, выполним ли `?query` в `O₁` (существует ли
  индивид типа `?query`),
- проверяется, выполним ли `m(?query) = ?mQuery` в `O₂`,
- если выполнимость различается (`?satisfiableInO1 != ?satisfiableInO2`),
  выдаётся нарушение SS-1.

### 6.3. Запрос (фрагмент)

```sparql
SELECT ?morphism ?query ?satisfiableInO1 ?satisfiableInO2 ?mQuery
       (IF(?satisfiableInO1 != ?satisfiableInO2, "SS-1 VIOLATION", "OK") AS ?status)
WHERE {
  ?morphism a toi:OntologyMorphism .
  ?query a owl:Class .
  ?morphism toi:hasMapping ?mapQ .
  ?mapQ toi:sourceConcept ?query ;
        toi:targetConcept ?mQuery .
  OPTIONAL { ?instance1 a ?query . }
  BIND(BOUND(?instance1) AS ?satisfiableInO1)
  OPTIONAL { ?instance2 a ?mQuery . }
  BIND(BOUND(?instance2) AS ?satisfiableInO2)
  FILTER(?satisfiableInO1 != ?satisfiableInO2)
}
ORDER BY ?status ?query
```

### 6.4. Пример нарушения

Дано:
- `tax:Employer ⊑ tax:Taxpayer` (т.е. любой работодатель —
  налогоплательщик)
- `m(tax:Employer) = reg:Document`
- `m(tax:Taxpayer) = reg:Person`

В `O₁`:
- `tax:Taxpayer` выполним (есть экземпляры `tax:Taxpayer_12345...`),
- `tax:Employer` выполним (есть экземпляры).

В `O₂`:
- `reg:Person` выполним,
- `reg:Document` выполним.

Однако если рассмотреть запрос `q = ¬tax:Employer` (концепт «не
работодатель»), то в `O₁` он выполним (есть люди, не являющиеся
работодателями), а `m(q) = ¬reg:Document` в `O₂` выполним (есть
не-документы). Формально — нет нарушения.

**Реальное нарушение** возникает, когда `q` невыполним в `O₁`, но
`m(q)` выполним в `O₂` (или наоборот). Например:
- `q = tax:∃ registeredWith.NonexistentOrg` (несогласованный концепт,
  невыполним в `O₁`),
- `m(q) = reg:∃ hasAddress.Address` (выполним в `O₂`, если адрес
  существует).

Тогда `?satisfiableInO1 = false`, `?satisfiableInO2 = true`, и
запрос выдаёт `SS-1 VIOLATION`.

### 6.5. Ограничения подхода

Приближённая проверка SS-1 через SPARQL имеет следующие ограничения:
1. Проверяется только выполнимость на данных (ABox), а не на всех
   моделях TBox. Для полного доказательства SS-1 требуется reasoner.
2. Не учитываются `owl:equivalentClass` и `owl:disjointWith` в полном
   объёме (только при материализации через OWL 2 RL).
3. Для запросов с кванторами ∀ и кардинальными ограничениями
   проверка приближённая.

Эти ограничения мотивируют использование OWL 2 RL reasoner (второй
уровень верификации) и формальное доказательство SS-1 на Lean 4
(третий уровень — `TOI/Theorems/T11_Finite.lean`).

---

## 7. SPARQL-запрос SS-2' (сохранение инконсистентности)

### 7.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Файл              | `sparql/ss2-verify.rq`                               |
| Проверяемая аксиома | SS-2': `O₁ ⊢ C ≡ ⊥ ⟺ O₂ ⊢ m(C) ≡ ⊥`             |
| Severity          | Informational                                        |
| Исполнение        | `arq --data=... --query=sparql/ss2-verify.rq`        |

### 7.2. Условие проверки

Для каждого отображения `C → m(C)`:
- проверяется, несогласован ли `C` в `O₁` (через `owl:equivalentClass owl:Nothing` или отсутствие экземпляров),
- проверяется, несогласован ли `m(C)` в `O₂` (аналогично),
- если статус согласованности различается, выдаётся нарушение SS-2'.

### 7.3. Запрос (фрагмент)

```sparql
SELECT ?morphism ?C ?mC ?inconsistentInO1 ?inconsistentInO2
       (IF(?inconsistentInO1 != ?inconsistentInO2, "SS-2' VIOLATION", "OK") AS ?status)
WHERE {
  ?morphism a toi:OntologyMorphism .
  ?morphism toi:hasMapping ?mapC .
  ?mapC toi:sourceConcept ?C ; toi:targetConcept ?mC .

  # Несогласованность в O₁
  OPTIONAL {
    { ?C owl:equivalentClass owl:Nothing . }
    UNION { ?C rdfs:subClassOf owl:Nothing . }
    UNION { FILTER NOT EXISTS { ?instance1 a ?C } ?C a owl:Class . }
  }
  BIND((BOUND(?_) || NOT EXISTS { ?instance1 a ?C }) AS ?inconsistentInO1)

  # Несогласованность в O₂
  OPTIONAL {
    { ?mC owl:equivalentClass owl:Nothing . }
    UNION { ?mC rdfs:subClassOf owl:Nothing . }
    UNION { FILTER NOT EXISTS { ?instance2 a ?mC } ?mC a owl:Class . }
  }
  BIND((BOUND(?_) || NOT EXISTS { ?instance2 a ?mC }) AS ?inconsistentInO2)

  FILTER(?inconsistentInO1 != ?inconsistentInO2)
}
ORDER BY ?status ?C
```

### 7.4. Пример нарушения

Дано:
- `tax:TaxDeduction ⊑ tax:∃ deductionType.DeductionType` (каждый вычет
  имеет тип),
- в `O₁` определены все 5 типов вычета (standard, social, property,
  investment, professional), и `tax:TaxDeduction` согласован,
- `m(tax:TaxDeduction) = reg:Address` (ошибочное отображение),
- `reg:Address` согласован в `O₂`.

Тогда `?inconsistentInO1 = false`, `?inconsistentInO2 = false` —
нет нарушения.

**Реальное нарушение** SS-2':
- В `O₁` концепт `tax:∃ deductionType.NonexistentType` (с
  несуществующим типом) несогласован: `?inconsistentInO1 = true`.
- Его образ `m(...)` может быть согласован в `O₂`, если `m`
  отобразил его в нечто выполнимое: `?inconsistentInO2 = false`.
- Тогда `?status = "SS-2' VIOLATION"`.

### 7.5. Двусторонность проверки

SS-2' требует **обоих** направлений:
- `O₁ ⊢ C ≡ ⊥ ⟹ O₂ ⊢ m(C) ≡ ⊥` (сохранение инконсистентности при
  трансляции — направление `⟹`),
- `O₂ ⊢ m(C) ≡ ⊥ ⟹ O₁ ⊢ C ≡ ⊥` (отражение инконсистентности — `⟸`).

SPARQL-запрос `FILTER(?inconsistentInO1 != ?inconsistentInO2)` ловит
оба направления. Контрмодель C (см. [Приложение 2](appendix-02-axioms-catalog.md),
раздел 7) демонстрирует, что нарушение одного направления не
эквивалентно нарушению SS-1.

---

## 8. Использование в ASG

### 8.1. Pipeline валидации

Архитектура `ValidatorAgent` (см. §3.2 монографии) применяет формы и
запросы в следующем порядке:

```
translate(request) →
   Шаг 1. ShaclValidator.validate(graph, shapes) — все 4 формы OM-1/2/3
   Шаг 2. Owl2RlReasoner.checkConsistency(graph) — материализация и консистентность
   Шаг 3. SparqlVerifier.verify(graph, "sparql/ss1-verify.rq")
          + SparqlVerifier.verify(graph, "sparql/ss2-verify.rq")
```

Если Шаг 1 выдаёт `sh:Violation` — трансляция отклоняется немедленно
(ранний возврат). Если Шаг 2 обнаруживает несогласованность — то же
самое. Шаг 3 логируется как `Warning` и не блокирует трансляцию, но
повышает уровень «доверия» к решению.

### 8.2. Кэширование

Результаты SHACL-валидации кэшируются на 5 минут (TTL) в Redis.
Кэш-ключ — хэш от `(sourceOntologyId, targetOntologyId, query)`.
Cache-hit ratio в пилоте — 85–92 % (см. [Приложение 10](appendix-10-slo-metrics.md)).

### 8.3. Мониторинг

Prometheus-метрики:
- `asg_shacl_violations_total{axiom="OM-1"}` — счётчик нарушений OM-1,
- `asg_shacl_violations_total{axiom="OM-2-union"}`,
- `asg_shacl_violations_total{axiom="OM-2-intersection"}`,
- `asg_shacl_violations_total{axiom="OM-3"}`,
- `asg_sparql_violations_total{axiom="SS-1"}`,
- `asg_sparql_violations_total{axiom="SS-2'"}`.

Alertmanager-правила: если `asg_shacl_violations_total{axiom="OM-1"}`
превышает 10 в течение 5 минут — alert `ASG-SHACL-OM1-HIGH` (severity:
P2). Полный список алертов — в `prometheus/rules.yml`.

### 8.4. Инструмент командной строки

Для запуска валидации вручную:

```bash
# SHACL
apache-jena-4.10.0/bin/shacl validate \
  --shapes shapes/om1-hierarchy.ttl \
  --shapes shapes/om2-union.ttl \
  --shapes shapes/om2-intersection.ttl \
  --shapes shapes/om3-role.ttl \
  --data ontologies/cross-domain-mapping.ttl

# SPARQL
apache-jena-4.10.0/bin/arq \
  --data ontologies/cross-domain-mapping.ttl \
  --data ontologies/tax/v1.0.owl \
  --data ontologies/healthcare/v1.0.owl \
  --data ontologies/registration/v1.0.owl \
  --query sparql/ss1-verify.rq

apache-jena-4.10.0/bin/arq \
  --data ontologies/cross-domain-mapping.ttl \
  --query sparql/ss2-verify.rq
```

---

## 9. Сравнение с формальной верификацией Lean 4

SHACL и SPARQL обеспечивают **практическую** проверку аксиом на
конкретных данных. Формальная верификация в Lean 4 (см. `TOI/`)
доказывает **универсальную** истинность аксиом:

| Уровень             | Что проверяется                    | Гарантия                       |
|---------------------|------------------------------------|--------------------------------|
| SHACL (Уровень 1)   | OM-1, OM-2, OM-3 для конкретного `m` | Структурная корректность       |
| OWL 2 RL (Уровень 2) | Консистентность `O₁ ∪ O₂ ∪ m`      | Отсутствие противоречий       |
| SPARQL (Уровень 3a) | SS-1 для конкретных запросов `q`    | Приближённая семантическая инвариантность |
| Lean 4 (Уровень 3b) | Теорема 1.1 для всех `m`, `q`       | Универсальная SS-1             |

См. [Приложение 2](appendix-02-axioms-catalog.md), раздел 11 — таблица
соответствия аксиом и механизмов проверки.

---

## 10. Библиографические ссылки

1. W3C. **SHACL Shapes Constraint Language.** W3C Recommendation, 20 July 2017. — основная спецификация.
2. Knublauch H., Kontokostas D. **Shapes Constraint Language (SHACL).** W3C, 2017. — авторская спецификация.
3. Apache Jena. **SHACL Tutorial.** https://jena.apache.org/documentation/shacl/ — реализация SHACL в Apache Jena.
4. Cyganiak R., Wood D., Lanthaler M. **RDF 1.1 Concepts and Abstract Syntax.** W3C Recommendation, 25 February 2014. — RDF-модель.
5. Harris S., Seaborne A. **SPARQL 1.1 Query Language.** W3C Recommendation, 21 March 2013. — SPARQL.
6. Hitzler P., Krötzsch M., Parsia B., Patel-Schneider P. F., Rudolph S. **OWL 2 Web Ontology Language: Primer.** W3C Recommendation, 27 October 2009.

Полный аннотированный список — в
[Приложении 12](appendix-12-annotated-bibliography.md).
