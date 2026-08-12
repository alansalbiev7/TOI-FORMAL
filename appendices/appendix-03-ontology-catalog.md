# Приложение 3. Каталог онтологий СМЭВ

> Настоящий каталог описывает онтологии, использованные в пилотной зоне
> Адаптивного семантического шлюза (АСШ / ASG). Приведены три предметные
> онтологии (налоги, здравоохранение, регистрация) и одна кросс-доменная
> онтология отображений. Для каждой онтологии указаны: идентификатор IRI,
> версия, число классов, свойств и TBox-аксиом, путь к файлу в
> репозитории, краткое описание предметной области и ключевые
> концепты-экземпляры. Связь с монографией — §2.4, §3.5.

---

## 1. Сводная таблица

| #  | Домен             | Имя файла OWL                    | Базовый IRI                                              | Версия | Классы | Свойства | TBox-аксиом |
|----|-------------------|----------------------------------|----------------------------------------------------------|--------|--------|----------|-------------|
| 1  | Налоги (ФНС)      | `ontologies/tax/v1.0.owl`        | `https://smev.ru/ontologies/asg/tax`                     | 1.0    | 9      | 11       | 38          |
| 2  | Здравоохранение   | `ontologies/healthcare/v1.0.owl`  | `https://smev.ru/ontologies/asg/healthcare`              | 1.0    | 11     | 13       | 45          |
| 3  | Регистрация (МВД) | `ontologies/registration/v1.0.owl`| `https://smev.ru/ontologies/asg/registration`           | 1.0    | 7      | 9        | 29          |
| 4  | Кросс-доменная    | `ontologies/cross-domain-mapping.ttl` | `https://smev.ru/ontologies/asg/cross-domain-mapping` | 1.0    | 0      | 0        | 22          |
|    | **Итого**         |                                  |                                                          |        | **27** | **33**   | **134**     |

> Примечание. Кросс-доменная онтология не вводит новых классов, а
> специфицирует только `owl:equivalentClass` /
> `owl:equivalentProperty` между концептами трёх предметных онтологий.
> Число TBox-аксиом в ней соответствует числу декларированных
> эквивалентностей (22 пары эквивалентностей).

---

## 2. Онтология «Налоги» (ФНС)

### 2.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Идентификатор     | `smev:tax:v1`                                       |
| Базовый IRI       | `https://smev.ru/ontologies/asg/tax`                |
| Версия            | `1.0`                                               |
| Дата публикации   | 2026-08-01                                          |
| Автор             | Рабочая группа ASG-TOI                              |
| Лицензия          | Apache License 2.0                                  |
| Профиль OWL       | OWL 2 DL (подмножество OWL 2 RL)                    |
| Файл              | `ontologies/tax/v1.0.owl`                           |
| Размер            | 6.4 KB                                              |

### 2.2. Классы (9)

| Локальное имя          | Полный IRI                                       | Аннотация                            |
|------------------------|--------------------------------------------------|--------------------------------------|
| `Taxpayer`             | `tax:Taxpayer`                                   | Налогоплательщик — физлицо или организация |
| `TaxAuthority`         | `tax:TaxAuthority`                               | Налоговый орган (ИФНС)               |
| `Employer`             | `tax:Employer`                                   | Работодатель                         |
| `TaxDeclaration`       | `tax:TaxDeclaration`                             | Налоговая декларация                 |
| `TaxDeduction`         | `tax:TaxDeduction`                               | Налоговый вычет                      |
| `IncomeSource`         | `tax:IncomeSource`                               | Источник дохода                      |
| `TaxYear`              | `tax:TaxYear`                                    | Налоговый период                     |
| `DeductionType`        | `tax:DeductionType`                              | Тип вычета (стандартный, социальный, имущественный, инвестиционный, профессиональный) |
| `DeclarationStatus`     | `tax:DeclarationStatus`                          | Статус декларации (draft, submitted, accepted, rejected) |

### 2.3. Свойства (11)

| Локальное имя          | Тип             | Домен             | Диапазон                |
|------------------------|-----------------|-------------------|-------------------------|
| `inn`                  | Datatype        | `tax:Taxpayer`, `tax:Employer` | `xsd:string` (12 или 10 цифр) |
| `kpp`                  | Datatype        | `tax:Employer`    | `xsd:string` (9 цифр)   |
| `ifnsCode`             | Datatype        | `tax:TaxAuthority`| `xsd:string` (4 цифры)   |
| `fullName`             | Datatype        | `tax:Taxpayer`    | `xsd:string`            |
| `registeredWith`       | Object          | `tax:Taxpayer`    | `tax:TaxAuthority`      |
| `employs`              | Object          | `tax:Employer`    | `tax:Taxpayer`          |
| `filesDeclaration`     | Object          | `tax:Taxpayer`    | `tax:TaxDeclaration`     |
| `declarationForYear`   | Object          | `tax:TaxDeclaration` | `tax:TaxYear`         |
| `declaresIncomeFrom`   | Object          | `tax:TaxDeclaration` | `tax:IncomeSource`    |
| `deductionType`        | Object          | `tax:TaxDeduction` | `tax:DeductionType`     |
| `amountRub`            | Datatype        | `tax:TaxDeduction`| `xsd:decimal`           |

### 2.4. TBox-аксиомы (38)

- `Taxpayer ≡ ∃ inn.⊤ ⊓ ∃ registeredWith.TaxAuthority` — определение класса «налогоплательщик».
- `Employer ⊑ Taxpayer` — работодатель является налогоплательщиком.
- `Employer ⊥ TaxAuthority`, `Employer ⊥ Taxpayer` (для соответствующих сущностей) — декларация дизъюнктности.
- `filesDeclaration` `Domain: Taxpayer`, `Range: TaxDeclaration`.
- Кардинальные ограничения: `TaxDeclaration` имеет `declarationForYear` (1), `declaresIncomeFrom` (≥1), `declarationStatus` (1); `TaxDeduction` имеет `deductionType` (1), `amountRub` (1); `Taxpayer` имеет `inn` (1), `fullName` (1), `registeredWith` (1); `Employer` имеет `inn` 10 цифр (1), `kpp` (1); `TaxAuthority` имеет `ifnsCode` (1); `TaxYear` имеет `year` (1).

### 2.5. Ключевые инстансы (примеры)

```
tax:Taxpayer_123456789012  a           tax:Taxpayer ;
                            tax:inn     "123456789012" ;
                            tax:fullName "Иванов Иван Иванович" ;
                            tax:registeredWith tax:IFNS_7701 .
tax:IFNS_7701              a           tax:TaxAuthority ;
                            tax:ifnsCode "7701" .
```

---

## 3. Онтология «Здравоохранение» (ЕГИСЗ)

### 3.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Идентификатор     | `smev:health:v1`                                    |
| Базовый IRI       | `https://smev.ru/ontologies/asg/healthcare`         |
| Версия            | `1.0`                                               |
| Дата публикации   | 2026-08-01                                          |
| Автор             | Рабочая группа ASG-TOI                              |
| Лицензия          | Apache License 2.0                                  |
| Профиль OWL       | OWL 2 DL (подмножество OWL 2 RL)                    |
| Файл              | `ontologies/healthcare/v1.0.owl`                   |
| Размер            | 7.1 KB                                              |

### 3.2. Классы (11)

| Локальное имя            | Полный IRI                                       | Аннотация                          |
|--------------------------|--------------------------------------------------|------------------------------------|
| `Patient`                | `health:Patient`                                 | Пациент (застрахованное лицо)      |
| `MedicalOrganization`   | `health:MedicalOrganization`                     | Медицинская организация             |
| `Doctor`                 | `health:Doctor`                                  | Врач                                |
| `MedicalRecord`          | `health:MedicalRecord`                            | Медицинская запись                  |
| `Diagnosis`              | `health:Diagnosis`                               | Диагноз (код МКБ-10)               |
| `Treatment`              | `health:Treatment`                               | Лечение                             |
| `Prescription`           | `health:Prescription`                            | Рецепт                              |
| `MedicalService`         | `health:MedicalService`                          | Медицинская услуга                  |
| `Specialization`         | `health:Specialization`                          | Специализация врача                 |
| `InsurancePolicy`        | `health:InsurancePolicy`                         | Полис ОМС                           |
| `VisitType`              | `health:VisitType`                               | Тип визита (первичный, повторный, экстренный) |

### 3.3. Свойства (13)

| Локальное имя          | Тип             | Домен                | Диапазон                  |
|------------------------|-----------------|----------------------|---------------------------|
| `inn`                  | Datatype        | `health:Patient`     | `xsd:string` (12 цифр)    |
| `snils`                | Datatype        | `health:Patient`     | `xsd:string` (11 цифр)    |
| `policyNumber`         | Datatype        | `health:InsurancePolicy` | `xsd:string` (16 цифр) |
| `fullName`             | Datatype        | `health:Patient`, `health:Doctor` | `xsd:string`  |
| `birthDate`            | Datatype        | `health:Patient`     | `xsd:date`                |
| `icd10Code`            | Datatype        | `health:Diagnosis`   | `xsd:string` (`^[A-Z][0-9]{2}(\.[0-9]{1,2})?$`) |
| `diagnosedWith`        | Object          | `health:MedicalRecord` | `health:Diagnosis`     |
| `treatedBy`            | Object          | `health:Patient`      | `health:Doctor`           |
| `worksAt`              | Object          | `health:Doctor`       | `health:MedicalOrganization` |
| `hasSpecialization`    | Object          | `health:Doctor`       | `health:Specialization`   |
| `prescribedFor`        | Object          | `health:Prescription` | `health:MedicalRecord`    |
| `visitType`            | Object          | `health:MedicalRecord` | `health:VisitType`       |
| `recordFor`            | Object          | `health:MedicalRecord` | `health:Patient`         |

### 3.4. TBox-аксиомы (45)

- `Patient ≡ ∃ inn.⊤ ⊓ ∃ snils.⊤ ⊓ ∃ policyNumber.InsurancePolicy`
- `Doctor ⊑ ∃ worksAt.MedicalOrganization ⊓ ∃ hasSpecialization.Specialization`
- `MedicalRecord ⊑ ∃ recordFor.Patient ⊓ ∃ diagnosedWith.Diagnosis`
- `Prescription ⊑ ∃ prescribedFor.MedicalRecord`
- 4 пары `disjointWith`: `Patient ⊥ Doctor`, `Patient ⊥ MedicalOrganization`, `Doctor ⊥ MedicalOrganization`, `MedicalOrganization ⊥ Diagnosis`.
- Кардинальные ограничения: `Patient` (1 inn, 1 snils, 1 fullName, 1 birthDate), `Doctor` (1 fullName, 1 worksAt, 1 hasSpecialization), `MedicalOrganization` (1 fullName), `Diagnosis` (1 icd10Code), `InsurancePolicy` (1 policyNumber), `MedicalRecord` (1 recordFor, 1 diagnosedWith, 1 visitType).

### 3.5. Ключевые инстансы (примеры)

```
health:Patient_987654321098  a            health:Patient ;
                             health:inn   "987654321098" ;
                             health:snils "112-233-445 95" ;
                             health:fullName "Петров Пётр Петрович" ;
                             health:birthDate "1980-05-12"^^xsd:date .
health:Diagnosis_J20         a            health:Diagnosis ;
                             health:icd10Code "J20.9" .
```

---

## 4. Онтология «Регистрация» (МВД)

### 4.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Идентификатор     | `smev:registration:v1`                              |
| Базовый IRI       | `https://smev.ru/ontologies/asg/registration`       |
| Версия            | `1.0`                                               |
| Дата публикации   | 2026-08-01                                          |
| Автор             | Рабочая группа ASG-TOI                              |
| Лицензия          | Apache License 2.0                                  |
| Профиль OWL       | OWL 2 DL (подмножество OWL 2 RL)                    |
| Файл              | `ontologies/registration/v1.0.owl`                 |
| Размер            | 5.2 KB                                              |

### 4.2. Классы (7)

| Локальное имя          | Полный IRI                                       | Аннотация                          |
|------------------------|--------------------------------------------------|------------------------------------|
| `Person`               | `reg:Person`                                     | Физическое лицо (гражданин)         |
| `Address`              | `reg:Address`                                    | Адрес регистрации                   |
| `RegistrationRecord`   | `reg:RegistrationRecord`                         | Запись о регистрации по месту жительства |
| `Document`             | `reg:Document`                                   | Документ, удостоверяющий личность  |
| `DocumentType`         | `reg:DocumentType`                              | Тип документа (паспорт РФ, загранпаспорт, СНИЛС) |
| `RegistrationAuthority`| `reg:RegistrationAuthority`                     | Орган регистрации (ОВМ МВД)         |
| `Region`               | `reg:Region`                                     | Субъект РФ                          |

### 4.3. Свойства (9)

| Локальное имя          | Тип             | Домен                | Диапазон                  |
|------------------------|-----------------|----------------------|---------------------------|
| `inn`                  | Datatype        | `reg:Person`         | `xsd:string` (12 цифр)    |
| `snils`                | Datatype        | `reg:Person`         | `xsd:string` (11 цифр)    |
| `fullName`             | Datatype        | `reg:Person`         | `xsd:string`              |
| `birthDate`            | Datatype        | `reg:Person`         | `xsd:date`                |
| `hasAddress`           | Object          | `reg:Person`         | `reg:Address`             |
| `registeredAt`         | Object          | `reg:RegistrationRecord` | `reg:RegistrationAuthority` |
| `documentType`         | Object          | `reg:Document`       | `reg:DocumentType`        |
| `issuedFor`            | Object          | `reg:Document`       | `reg:Person`              |
| `inRegion`             | Object          | `reg:Address`        | `reg:Region`              |

### 4.4. TBox-аксиомы (29)

- `Person ≡ ∃ inn.⊤ ⊓ ∃ fullName.⊤ ⊓ ∃ birthDate.⊤ ⊓ ∃ hasAddress.Address`
- `RegistrationRecord ⊑ ∃ registeredAt.RegistrationAuthority ⊓ ∃ inRegion.Region`
- `Document ⊑ ∃ documentType.DocumentType ⊓ ∃ issuedFor.Person`
- 3 пары `disjointWith`: `Person ⊥ Document`, `Person ⊥ Address`, `Address ⊥ Document`.
- Кардинальные ограничения: `Person` (1 inn, 1 fullName, 1 birthDate, ≥1 hasAddress), `Document` (1 documentType, 1 issuedFor), `RegistrationRecord` (1 registeredAt, 1 inRegion), `Address` (1 inRegion).

### 4.5. Ключевые инстансы (примеры)

```
reg:Person_111111111111  a              reg:Person ;
                         reg:inn         "111111111111" ;
                         reg:fullName    "Сидоров Сидор Сидорович" ;
                         reg:birthDate   "1990-03-15"^^xsd:date ;
                         reg:hasAddress  reg:Address_Moscow_Tverskaya_1 .
reg:Address_Moscow_Tverskaya_1  a     reg:Address ;
                                 reg:inRegion reg:Region_77 .
reg:Region_77           a              reg:Region ;
                        rdfs:label     "г. Москва" .
```

---

## 5. Кросс-доменная онтология отображений

### 5.1. Метаданные

| Поле             | Значение                                            |
|------------------|-----------------------------------------------------|
| Идентификатор     | `smev:cross-mapping:v1`                             |
| Базовый IRI       | `https://smev.ru/ontologies/asg/cross-domain-mapping` |
| Версия            | `1.0`                                               |
| Дата публикации   | 2026-08-01                                          |
| Автор             | Рабочая группа ASG-TOI                              |
| Лицензия          | Apache License 2.0                                  |
| Профиль OWL       | OWL 2 DL (`owl:imports` трёх предметных онтологий)  |
| Файл              | `ontologies/cross-domain-mapping.ttl`              |
| Размер            | 7.8 KB                                              |

### 5.2. Структура

Кросс-доменная онтология:
1. Импортирует три предметные онтологии через `owl:imports`.
2. Декларирует три `owl:AnnotationProperty`:
   - `asg:matchCondition` — текстовое описание условия соответствия (например, «значение ИНН совпадает, 12 цифр для физлица»).
   - `asg:confidence` — числовое значение уверенности в соответствии, от `0.0` до `1.0` (тип `xsd:decimal`).
   - `asg:alignmentRole` — роль в выравнивании (`equivalent`, `broader`, `narrower`, `related`).
3. Устанавливает 22 пары эквивалентностей (см. ниже).

### 5.3. Отображения концептов (5 пар)

| Исходный концепт      | Целевой концепт         | Уверенность | Условие соответствия                          |
|-----------------------|-------------------------|-------------|------------------------------------------------|
| `reg:Person`          | `health:Patient`        | 0.95        | Совпадение inn и/или snils                     |
| `reg:Person`          | `tax:Taxpayer`          | 0.95        | Совпадение inn                                  |
| `health:Patient`      | `tax:Taxpayer`          | 0.90        | Совпадение inn                                  |
| `reg:RegistrationAuthority` | `tax:TaxAuthority` | 0.80        | Один и тот же ОГРН, но разные функции          |
| `reg:Document`        | `health:InsurancePolicy`| 0.60        | Подмножество (только полис ОМС)                |

### 5.4. Отображения свойств (10 пар)

| Исходное свойство     | Целевое свойство        | Уверенность | Условие                       |
|-----------------------|-------------------------|-------------|-------------------------------|
| `reg:inn`             | `health:inn`            | 1.00        | Полное совпадение формата     |
| `reg:inn`             | `tax:inn`               | 1.00        | Полное совпадение формата     |
| `health:inn`          | `tax:inn`               | 1.00        | Полное совпадение формата     |
| `reg:snils`           | `health:snils`          | 1.00        | Полное совпадение формата     |
| `reg:fullName`        | `health:fullName`       | 1.00        | Полное совпадение             |
| `reg:fullName`        | `tax:fullName`          | 1.00        | Полное совпадение             |
| `health:fullName`     | `tax:fullName`          | 1.00        | Полное совпадение             |
| `reg:birthDate`       | `health:birthDate`      | 1.00        | Полное совпадение (xsd:date)  |
| `reg:hasAddress`      | `tax:registeredWith`    | 0.50        | Слабое соответствие (адрес ≠ орган) |
| `reg:documentType`    | `health:visitType`      | 0.30        | Семантически различны          |

### 5.5. Аннотированный пример (Turtle)

```turtle
@prefix owl:  <http://www.w3.org/2002/07/owl#> .
@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix asg:  <https://smev.ru/ontologies/asg/cross-domain-mapping#> .
@prefix reg:  <https://smev.ru/ontologies/asg/registration#> .
@prefix tax:  <https://smev.ru/ontologies/asg/tax#> .
@prefix health: <https://smev.ru/ontologies/asg/healthcare#> .

<https://smev.ru/ontologies/asg/cross-domain-mapping>
  a owl:Ontology ;
  owl:imports <https://smev.ru/ontologies/asg/registration> ,
              <https://smev.ru/ontologies/asg/healthcare> ,
              <https://smev.ru/ontologies/asg/tax> ;
  rdfs:label "Кросс-доменная онтология отображений СМЭВ"@ru ;
  rdfs:comment "Cross-domain mapping ontology for ASG/ТОИ pilots"@en .

# Классовые эквивалентности
reg:Person  owl:equivalentClass  health:Patient ;
            asg:matchCondition   "совпадение inn и/или snils"@ru ;
            asg:confidence       "0.95"^^xsd:decimal ;
            asg:alignmentRole    "equivalent" .

reg:Person  owl:equivalentClass  tax:Taxpayer ;
            asg:matchCondition   "совпадение inn (12 цифр)"@ru ;
            asg:confidence       "0.95"^^xsd:decimal ;
            asg:alignmentRole    "equivalent" .

# Свойственные эквивалентности
reg:inn     owl:equivalentProperty  health:inn ;
            asg:matchCondition   "значение ИНН совпадает"@ru ;
            asg:confidence       "1.00"^^xsd:decimal ;
            asg:alignmentRole    "equivalent" .

reg:inn     owl:equivalentProperty  tax:inn ;
            asg:confidence       "1.00"^^xsd:decimal ;
            asg:alignmentRole    "equivalent" .
```

---

## 6. Статистика по триплетам

| Онтология                  | Утверждённые триплеты | Импортированные триплеты | Всего |
|----------------------------|----------------------|--------------------------|-------|
| `tax/v1.0.owl`             | 142                  | 0                        | 142   |
| `healthcare/v1.0.owl`      | 178                  | 0                        | 178   |
| `registration/v1.0.owl`    | 116                  | 0                        | 116   |
| `cross-domain-mapping.ttl` | 130                  | 436 (3 предметные)       | 566   |
| **Итого**                  | **566**              | **436**                  | **1002** |

---

## 7. Соответствие профилю OWL 2 RL

Все три предметные онтологии соответствуют профилю OWL 2 RL. Это
означает:
1. Все классы и свойства — именованные (`owl:Class`, `owl:ObjectProperty`,
   `owl:DatatypeProperty`), без анонимных.
2. Используются только полиномиально проверяемые конструкции:
   `rdfs:subClassOf`, `owl:equivalentClass`, `owl:disjointWith`,
   `owl:someValuesFrom`, `owl:allValuesFrom`, `owl:cardinality`,
   `owl:minCardinality`, `owl:maxCardinality`, `owl:hasValue`.
3. Не используются: `owl:complementOf`, `owl:oneOf` для классов
   (только для `DocumentType`, `DeductionType`, `DeclarationStatus`,
   `VisitType` — через подклассы, а не через `oneOf`), вложенные
   `owl:unionOf`/`owl:intersectionOf` глубины > 2.

Проверка соответствия OWL 2 RL выполняется в `Owl2RlReasoner` через
Apache Jena `OWLMicroReasoner` + правила OWL 2 RL (`OWLRLRules`).

---

## 8. Связь с пилотной зоной ASG

В пилотной зоне ASG (`asg-core`) три предметные онтологии загружаются
при старте сервиса в `OntologyRegistry`:

```scala
val registry = OntologyRegistry(
  Map(
    "smev:tax:v1"          -> loadOntology("ontologies/tax/v1.0.owl"),
    "smev:health:v1"       -> loadOntology("ontologies/healthcare/v1.0.owl"),
    "smev:registration:v1"  -> loadOntology("ontologies/registration/v1.0.owl")
  )
)
```

Кросс-доменная онтология загружается в `MappingRegistry` и обеспечивает
начальный набор соответствий, который пополняется `MatcherAgent` в
процессе работы:

```scala
val mappings = MappingRegistry(
  loadTurtle("ontologies/cross-domain-mapping.ttl")
)
```

---

## 9. Расширение онтологий (дорожная карта)

| Домен             | Планируемая версия | Классы | Свойства | Целевой квартал |
|-------------------|--------------------|--------|----------|-----------------|
| Налоги (ФНС)      | `v2.0`             | 14 (+5)| 16 (+5)  | Q4 2026         |
| Здравоохранение   | `v2.0`             | 18 (+7)| 20 (+7)  | Q1 2027         |
| Регистрация       | `v2.0`             | 12 (+5)| 13 (+4)  | Q1 2027         |
| Социальная защита  | `v1.0` (новая)     | ~10    | ~12      | Q2 2027         |
| Образование       | `v1.0` (новая)     | ~8     | ~10      | Q3 2027         |
| Транспорт (ГИС)   | `v1.0` (новая)     | ~12    | ~15      | Q3 2027         |

Расширение до 10 онтологий — цель Sprint 3 (см. `README.md`,
раздел «Дорожная карта»).

---

## 10. Библиографические ссылки

1. W3C. **OWL 2 Web Ontology Language.** W3C Recommendation, 27 October 2009. — спецификация OWL 2.
2. W3C. **OWL 2 Profiles.** W3C Recommendation, 27 October 2009. — профиль OWL 2 RL.
3. Минздрав России. **МКБ-10. Международная статистическая классификация болезней и проблем, связанных со здоровьем, 10-го пересмотра.** ВОЗ, 1990 (в РФ — с 1999 г.).
4. Приказ ФНС России от 08.10.2024 № ЕД-7-3/1183@. **Об утверждении формата представления налоговой декларации в электронном виде.** — основа онтологии `tax`.
5. Приказ Минздрава России от 30.12.2014 № 786н. **Об утверждении Порядка оказания медицинской помощи.** — основа онтологии `healthcare`.
6. Постановление Правительства РФ от 17.07.1995 № 713. **Правила регистрации и снятия граждан РФ с регистрационного учёта.** — основа онтологии `registration`.

Полный аннотированный список источников — в
[Приложении 12](appendix-12-annotated-bibliography.md).
