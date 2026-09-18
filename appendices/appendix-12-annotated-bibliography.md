# Источники и пределы проверки

Дата сверки программного обновления: 18.09.2026. Ссылки ведут к владельцам
стандартов, спецификаций или исходного кода. Это перечень источников
данного репозитория, а не полная библиография монографии.

1. [ГОСТ Р 55062—2021: карточка Росстандарта](https://protect.gost.ru/gost/details/72935e67-fb1c-49d0-8a5e-af66fb7ba1c5).
   Идентификация базового стандарта. Само наличие ссылки не удостоверяет
   соответствие реализации всем его положениям.
2. [SHACL, W3C Recommendation 20.07.2017](https://www.w3.org/TR/2017/REC-shacl-20170720/).
   Целевые узлы, формы, ограничения и отчёт проверки; используется ограниченный набор Core.
3. [SPARQL 1.1 Query Language, W3C Recommendation 21.03.2013](https://www.w3.org/TR/2013/REC-sparql11-query-20130321/).
   Семантика запросов; в CLI используется локальный SELECT и синтетический граф.
4. [Apache Jena: inference support](https://jena.apache.org/documentation/inference/).
   Ограничения встроенных OWL reasoners; они не приравниваются полному решателю OWL 2 RL/DL.
5. [Lean: официальная установка](https://lean-lang.org/install/).
   Версия проекта закреплена отдельно в `lean-toolchain`.
6. [mathlib v4.19.0, точный коммит](https://github.com/leanprover-community/mathlib4/tree/c44e0c8ee63ca166450922a373c7409c5d26b00b).
   Прочитаны и использованы определения `Order.Ideal`, `ClosureOperator`,
   `BoundedLatticeHom`; их доказательства входят в зависимости формальной части.
7. [Gradle 8.10.2: checksum дистрибутива](https://services.gradle.org/distributions/gradle-8.10.2-bin.zip.sha256)
   и [checksum wrapper](https://services.gradle.org/distributions/gradle-8.10.2-wrapper.jar.sha256).
   Сверены с файлами инструментов, добавлена автоматическая проверка wrapper.

Научная редакция монографии и приложение по методологии предоставлены автором;
их версии фиксируются в `docs/source-manifest.json`. Публичный DOI не назначается
на основании одного маршрута Zenodo uploads. Правовые проекты и требования
отраслевой сертификации не объявлены проверенными данным программным CI.
