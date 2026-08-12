# Приложение 12. Аннотированная библиография

> Настоящее приложение содержит 20 ключевых источников, на которые
> опирается монография «Технология интероперабельности» (ТОИ). Каждый
> источник снабжён полной библиографической ссылкой (по ГОСТ Р
> 7.0.100-2018) и аннотацией (3–5 предложений), объясняющей
> релевантность для монографии. Источники упорядочены по тематическим
> блокам: (A) FAIR и научная коммуникация; (B) теория решёток и
> двойственности; (C) модальная логика и p-морфизмы; (D) дескрипционные
> логики и семантический веб; (E) информационный поиск и машинное
> обучение; (F) нормативно-правовые источники. Связь с монографией —
> глава 5.

---

## Блок A. FAIR-принципы и научная коммуникация

### 1. Wilkinson M. D., Dumontier M., Aalbersberg I. J. et al.
**The FAIR Guiding Principles for Scientific Data Management and
Stewardship // Scientific Data.** — 2016. — Vol. 3. — Article 160018.
— DOI: [10.1038/sdata.2016.18](https://doi.org/10.1038/sdata.2016.18).

**Аннотация.** Статья вводит FAIR-принципы (Findable, Accessible,
Interoperable, Reusable) — 15 подпунктов (4 + 4 + 3 + 4),
формулирующие требования к научным данным и артефактам. Принципы
были разработаны на совместной встрече FORCE11, Netherland eScience
Center и других организаций в 2015 г. и к 2024 г. приняты как
обязательные многими фондами (NIH, Horizon Europe, РФФИ). В
монографии FAIR-принципы используются как методологическая основа
политики публикации артефактов ASG-TOI (см. §5.1, гл. 5).

### 2. Smith A. M., Katz D. S., Niemeyer K. E. et al.
**Software Citation Principles // PeerJ Computer Science.** — 2016.
— Vol. 2. — Article e86. — DOI: [10.7717/peerj-cs.86](https://doi.org/10.7717/peerj-cs.86).

**Аннотация.** Статья устанавливает шесть принципов цитирования
программного обеспечения: важность, кредит и атрибуция,
уникальная идентификация, доступность, долговечность, специфичность.
Принципы разработаны рабочей группой FORCE11 Software Citation
Implementation Working Group и в 2019 г. приняты как стандарт APA,
MLA, Chicago. В монографии (см. §5.2) эти принципы применяются к
репозиторию ASG-TOI через `CITATION.cff` и DOI-имена, регистрируемые
в Zenodo.

### 3. Brase J.
**DataCite: Global Persistent Identifiers for Shared Information
Resources // D-Lib Magazine.** — 2009. — Vol. 15, no. 7/8.
— DOI: [10.1045/july2009-brase](https://doi.org/10.1045/july2009-brase).

**Аннотация.** Статья описывает инфраструктуру DataCite —
международный реестр постоянных идентификаторов DOI для научных
данных. DataCite регистрирует DOI-имена для наборов данных,
программного обеспечения и других исследовательских артефактов,
обеспечивая их FAIR-совместимость. В монографии (см. §5.2)
обсуждается интеграция ASG-TOI с Zenodo (который использует DataCite
для присвоения DOI каждому релизу), что делает артефакты шлюза
цитируемыми в академической литературе.

---

## Блок B. Теория решёток и двойственности

### 4. Birkhoff G.
**On the Combination of Subalgebras // Mathematical Proceedings of the
Cambridge Philosophical Society.** — 1933. — Vol. 29, no. 4. —
P. 441–464. — DOI: [10.1017/S0305004100011514](https://doi.org/10.1017/S0305004100011514).

**Аннотация.** Статья Гаррета Биркгофа вводит теорему о представлении
для конечных дистрибутивных решёток: любая конечная дистрибутивная
решётка `L` изоморфна решётке нижних множеств `J(L)` множества её
неразложимых элементов. Дополнительно установлена характеризация:
решётка дистрибутивна iff она не содержит подрешёток M₃ (ромб) и
N₅ (пентагон). Эти результаты — теоретическая основа аксиомы OE-1
монографии (см. §1.2.3, Прил. 2).

### 5. Stone M. H.
**Topological Representation of Distributive Lattices and Brouwerian
Logics // Časopis pro pěstování matematiky a fysiky.** — 1937. —
Vol. 67, no. 1. — P. 1–25.
— DOI: [10.21136/CPMF.1938.124080](https://doi.org/10.21136/CPMF.1938.124080).

**Аннотация.** Статья Маршалла Стоуна устанавливает двойственность
между булевыми алгебрами и стоуновскими пространствами
(компактными, нульмерными, вполне несвязными). Эта двойственность
позже была обобщена Х. Пристли на случай дистрибутивных решёток.
В монографии (см. §1.4, Прил. 13) двойственность Стоуна–Пристли
используется для геометрической интерпретации онтологических
морфизмов через непрерывные отображения пространств простых
фильтров.

### 6. Priestley H. A.
**Representation of Distributive Lattices by Means of Ordered Stone
Spaces // Bulletin of the London Mathematical Society.** — 1970. —
Vol. 2, no. 2. — P. 186–190.
— DOI: [10.1112/blms/2.2.186](https://doi.org/10.1112/blms/2.2.186).

**Аннотация.** Статья Хилари Пристли обобщает двойственность Стоуна
на дистрибутивные (не обязательно булевы) решётки: категория
`DLat` дистрибутивных решёток с гомоморфизмами контравариантно
эквивалентна категории `Pries` пространств Пристли. Эта
двойственность — основной геометрический инструмент монографии для
построения и анализа онтологических морфизмов (см. §1.4,
Прил. 13). Пристли позже (1972) расширила результат на случай
произвольных дистрибутивных решёток с операцией бесконечного
соединения.

### 7. Funayama N., Nakayama T.
**On the Distributivity of a Lattice of Congruence Relations // Journal
of the Mathematical Society of Japan.** — 1949. — Vol. 1, no. 1. —
P. 1–11. — DOI: [10.2969/jmsj/00110001](https://10.2969/jmsj/00110001).

**Аннотация.** Статья вводит контрпример Фунамы–Накаямы,
демонстрирующий, что решётка конгруэнций произвольной решётки `L`
не является, вообще говоря, дистрибутивной. В монографии (см.
§1.5, теорема 1.2) этот результат используется для доказательства,
что категория `CLat` полных решёток не является декартово замкнутой
(CCC), что мотивирует переход к декартово замкнутой подкатегории
`TOI-Cat` в теореме 2.1.

---

## Блок C. Модальная логика и p-морфизмы

### 8. Grice H. P.
**Logic and Conversation // Syntax and Semantics.** — 1975. —
Vol. 3. — P. 41–58. — Reprinted in: Grice H. P. Studies in the Way of
Words. Harvard University Press, 1989. — P. 22–40.

**Аннотация.** Статья Пола Грайса вводит принцип кооперации и
максимы беседы (Quantity, Quality, Relation, Manner), образующие
основу прагматической семантики естественного языка. В монографии
(см. §1.5, Прил. 13) максимы Грайса используются как
философско-лингвистический фон для различения «синтаксической»,
«семантической» и «прагматической» интероперабельности на уровнях
LISI L2, L3 и L4 соответственно.

### 9. van Benthem J. F. A. K.
**Modal Correspondence Theory.** — PhD thesis, University of
Amsterdam, 1976. — 175 p. — (Mathematical Centre Tracts, no. 89).

**Аннотация.** Диссертация Иоганна ван Бентема систематизирует теорию
соответствий между модальными формулами и условиями первого порядка
на фреймы Крипке. Вводится понятие p-морфизма (bounded morphism) —
ограниченного отображения фреймов, сохраняющего структуру
доступности. В монографии (см. §1.5, Прил. 13) p-морфизмы
рассматриваются как контрвариантная редукция онтологических
морфизмов: любой p-морфизм фреймов индуцирует онтологический морфизм
в смысле аксиом OM-1/2/3.

---

## Блок D. Дескрипционные логики и семантический веб

### 10. Baader F., Calvanese D., McGuinness D., Nardi D., Patel-Schneider P.
(eds.)
**The Description Logic Handbook: Theory, Implementation and
Applications.** — 2nd ed. — Cambridge University Press, 2010. — 592 p.
— ISBN 978-0-521-87261-4.

**Аннотация.** Справочник по дескрипционным логикам (DL) — основной
источник по синтаксису и семантике ALC, SROIQ, OWL 2 DL. Содержит
формальные определения интерпретаций `I = (Δ^I, ·^I)`, отношения
выполнимости `⊨`, профилей DL (EL, RL, DL). В монографии (см.
§1.2.3, Прил. 1, 2) нотация DL используется как формальный язык
для записи аксиом OE/OM/SS и определения запросов `Query(O)`.

### 11. Euzenat J., Shvaiko P.
**Ontology Matching.** — 2nd ed. — Berlin: Springer-Verlag, 2013. —
511 p. — ISBN 978-3-642-38720-3.
— DOI: [10.1007/978-3-642-38721-0](https://doi.org/10.1007/978-3-642-38721-0).

**Аннотация.** Монография Иером Эузената и Павла Швайко — основная
систематизация методов сопоставления онтологий (ontology matching):
строковые, лингвистические, структурные, семантические.
Описаны EDO и OAEI-бенчмарки, карты классификации matchers,
оценка качества (precision, recall, F1). В монографии (см. §5.4,
Прил. 13) подход ТОИ сопоставляется с моделью Euzenat–Shvaiko;
ключевое отличие: ТОИ требует не просто сходства концептов, а
сохранения формальной семантики через аксиомы OM/SS.

### 12. Knublauch H., Kontokostas D.
**Shapes Constraint Language (SHACL).** — W3C Recommendation, 20 July
2017. — URL: https://www.w3.org/TR/shacl/.

**Аннотация.** Рекомендация W3C, определяющая SHACL — язык для
описания структурных ограничений на RDF-графы через формы (`sh:NodeShape`,
`sh:PropertyShape`). Поддерживает SPARQL-based ограничения (`sh:sparql`),
уровни критичности (`sh:Violation` / `sh:Warning` / `sh:Info`). В
монографии (см. §2.5, Прил. 4) SHACL используется как основной
механизм проверки аксиом OM-1, OM-2, OM-3; конкретные формы
находятся в `shapes/om1-hierarchy.ttl`, `om2-union.ttl`,
`om2-intersection.ttl`, `om3-role.ttl`.

### 13. Cyganiak R., Wood D., Lanthaler M.
**RDF 1.1 Concepts and Abstract Syntax.** — W3C Recommendation, 25
February 2014. — URL: https://www.w3.org/TR/rdf11-concepts/.

**Аннотация.** Рекомендация W3C, определяющая абстрактный синтаксис
RDF: модель троек `(subject, predicate, object)`, IRIs, литералы,
графы данных. Описаны основные сериализации: Turtle, N-Triples,
RDF/XML, JSON-LD. В монографии (см. §2.3) RDF — графовая модель
данных онтологий СМЭВ; основные сериализации — Turtle (для
человекочитаемости) и RDF/XML (для совместимости с OWL API).

### 14. Harris S., Seaborne A.
**SPARQL 1.1 Query Language.** — W3C Recommendation, 21 March 2013.
— URL: https://www.w3.org/TR/sparql11-query/.

**Аннотация.** Рекомендация W3C, определяющая SPARQL 1.1 — язык
запросов к RDF-данным. Поддерживает SELECT, CONSTRUCT, ASK, DESCRIBE;
агрегатные функции (COUNT, SUM, AVG); OPTIONAL, UNION, FILTER NOT
EXISTS, property paths. В монографии (см. §2.5, Прил. 4) SPARQL
используется для приближённой проверки аксиом SS-1 и SS-2' через
запросы `sparql/ss1-verify.rq` и `sparql/ss2-verify.rq`.

### 15. Hitzler P., Krötzsch M., Rudolph S.
**Foundations of Semantic Web Technologies.** — Boca Raton: CRC
Press, 2010. — 427 p. — ISBN 978-1-4200-9050-5.

**Аннотация.** Учебник по семантическому вебу: RDF, RDFS, OWL 2,
SPARQL, OWL-RL правила материализации, рассуждение (reasoning),
ограничения вычислительной сложности. Подробно описаны профили OWL 2
(RL, EL, QL, DL) и связь с дескрипционными логиками. В монографии
(см. §2.5, §3.4) используется как основной учебный и справочный
источник; профиль OWL 2 RL лежит в основе реализации `Owl2RlReasoner`.

---

## Блок E. Информационный поиск и машинное обучение

### 16. Robertson S., Zaragoza H.
**The Probabilistic Relevance Framework: BM25 and Beyond // Foundations
and Trends in Information Retrieval.** — 2009. — Vol. 3, no. 4. —
P. 333–389. — DOI: [10.1561/1500000019](https://doi.org/10.1561/1500000019).

**Аннотация.** Статья Стивена Робертсона и Уго Сарагосы —
систематизация вероятностного подхода к информационному поиску:
модель BM25, BM25F, оценка релевантности. Функция BM25 — стандарт
полнотекстового поиска, используемая в Lucene, Elasticsearch, Sphinx.
В монографии (см. §3.2) BM25 применяется в `MatcherAgent` для
ранжирования кандидатов соответствий концептов; порог срабатывания —
0.6 (при меньших значениях вызывается BERT-fallback).

### 17. Devlin J., Chang M.-W., Lee K., Toutanova K.
**BERT: Pre-training of Deep Bidirectional Transformers for Language
Understanding // Proceedings of NAACL-HLT 2019.** — 2019. —
P. 4171–4186. — DOI: [10.18653/v1/N19-1423](https://doi.org/10.18653/v1/N19-1423).
— arXiv: [1810.04805](https://arxiv.org/abs/1810.04805).

**Аннотация.** Статья вводит BERT (Bidirectional Encoder
Representations from Transformers) — модель предобучения языковых
представлений на основе двунаправленного Transformer. BERT
установил state-of-the-art результаты на задачах GLUE, SQuAD,
NER. В монографии (см. §3.2) BERT используется как fallback-матчер
в `MatcherAgent` для семантического сходства имён концептов, когда
BM25-оценка ниже порога; используется предобученная модель
`cointegrated/rubert-tiny2`, дообученная на парах концептов СМЭВ.

---

## Блок F. Нормативно-правовые и лицензионные источники

### 18. ГОСТ Р 55062-2021.
**Информационные технологии. Интероперабельность. Основные положения.**
— Введён 30.04.2022. — М.: Стандартинформ, 2022. — 16 с.
— URL: https://cntd.ru/document/1303234150.

**Аннотация.** Действующий национальный стандарт РФ, устанавливающий
терминологию и общие принципы интероперабельности информационных
систем. Разработан ТК 164 «Искусственный интеллект». Стандарт
определяет три уровня интероперабельности (семантический,
технический, нормативно-правовой), соответствующие трёхуровневой
эталонной модели ТЭМ-2021 монографии (см. §1.6, Прил. 5). В
монографии стандарт используется как нормативная основа для проектов
Прил. 5–8.

### 19. ISO 16290:2013.
**Space systems — Definition of the Technology Readiness Levels (TRLs)
and their criteria of assessment.** — Geneva: ISO, 2013. — 18 p.
— URL: https://www.iso.org/standard/56064.html.

**Аннотация.** Международный стандарт ISO, определяющий шкалу уровней
готовности технологии (TRL-1 — TRL-9), первоначально разработанную
NASA (1995). Стандарт используется для оценки зрелости разрабатываемых
технологий в космической отрасли, позже — в других отраслях. В
монографии (см. §3.7) шкала TRL применяется для оценки зрелости
подсистем ASG-TOI: Lean 4-формализация соответствует TRL-6 (пилот),
SHACL-валидация — TRL-7 (опытная эксплуатация), cross-domain-mapping
— TRL-8 (готов к эксплуатации).

### 20. Apache Software Foundation.
**Apache License, Version 2.0.** — 2004. — URL:
https://www.apache.org/licenses/LICENSE-2.0.

**Аннотация.** Стандартная permissive open-source лицензия,
разработанная Apache Software Foundation. Совместима с GPLv3,
BSD, MIT; содержит явное патентное прекращение (patent grant) и
защиту от патентных исков (patent retaliation clause). В монографии
(см. §5.3) эта лицензия используется как основная для всех
артефактов репозитория ASG-TOI (Scala-код, Lean-формализация,
онтологии, SHACL-формы, документация). Выбор Apache 2.0 мотивирован
также тем, что Akka 2.7.0 (октябрь 2025) возвращена под эту
лицензию, что снимает лицензионные ограничения для проекта.

---

## Блок G. Дополнительные источники

> Источники 21–24 приведены как дополнительные; аннотации к ним
> короче (1–2 предложения).

### 21. Moura L. de, Ullrich S.
**The Lean 4 Theorem Prover and Programming Language.** — In:
Automated Deduction — CADE 28. Springer, 2021. — P. 625–635.
— DOI: [10.1007/978-3-030-79876-5_37](https://doi.org/10.1007/978-3-030-79876-5_37).

**Аннотация.** Описание Lean 4 — системы автоматического
доказательства теорем и языка программирования, разработанной
Леонарду де Моурой и Себастьяном Ульрихом в Microsoft Research и
Amazon Web Services. В монографии Lean 4 — основной инструмент
формальной верификации Теоремы 1.1; библиотека Mathlib4
содержит формализации теории решёток, категорий и топологии.

### 22. Mathlib Community.
**The Lean Mathematical Library.** — 2020. — arXiv: [1911.02023](https://arxiv.org/abs/1911.02023).

**Аннотация.** Описание библиотеки Mathlib4 — коллективного проекта
по формализации математики на Lean 4, включающего более 1.5 млн
строк кода (на 2024 г.). В ASG-TOI используются модули
`Mathlib.Order.CompleteLattice`, `Mathlib.Order.Lattice`,
`Mathlib.Data.Set.Basic` для формализации OE-1 и OM-2'.

### 23. Brown S.
**The C4 Model for Visualising Software Architecture.** — Leanpub,
2018. — URL: https://c4model.com/.

**Аннотация.** C4 Model — подход к визуализации архитектуры
программного обеспечения через четыре уровня детализации (Context,
Container, Component, Code). В ASG-TOI используется для описания
архитектуры в `docs/architecture.md` и в [Прил. 11](appendix-11-architecture-diagrams.md)
(10 диаграмм в SVG).

### 24. Beyer B., Jones C., Petoff J., Murphy N. R. (eds.)
**Site Reliability Engineering: How Google Runs Production Systems.**
— O'Reilly, 2016. — 524 p. — ISBN 978-1-4919-2391-4.

**Аннотация.** Книга Google SRE-команды, систематизирующая практики
обеспечения надёжности крупномасштабных систем: SLI, SLO, SLA,
error budget, blameless postmortem. В ASG-TOI (см. §3.6, Прил. 10)
применяются SLO ≥ 99.5 %, error budget 216 мин/мес, многооконный
burn rate alerting.

---

## Перекрёстные ссылки

| Источник  | Где используется в монографии                |
|-----------|---------------------------------------------|
| [1] Wilkinson 2016       | §5.1, Гл. 5                  |
| [2] Smith 2016           | §5.2                          |
| [3] Brase 2009           | §5.2                          |
| [4] Birkhoff 1933        | §1.2.3, §1.4, Прил. 2, 13    |
| [5] Stone 1937           | §1.4, Прил. 13                |
| [6] Priestley 1970       | §1.4, Прил. 2, 13             |
| [7] Funayama–Nakayama 1949 | §1.5, Теорема 1.2          |
| [8] Grice 1975           | §1.6 (LISI L4), Прил. 13      |
| [9] van Benthem 1976     | §1.5, Прил. 13                |
| [10] Baader et al. 2010  | §1.2.3, Прил. 1, 2            |
| [11] Euzenat–Shvaiko 2013 | §5.4, Прил. 13               |
| [12] Knublauch–Kontokostas 2017 (SHACL) | §2.5, Прил. 4    |
| [13] Cyganiak–Wood–Lanthaler 2014 (RDF) | §2.3            |
| [14] Harris–Seaborne 2013 (SPARQL)      | §2.5, Прил. 4    |
| [15] Hitzler et al. 2010 | §2.5, §3.4                    |
| [16] Robertson–Zaragoza 2009 | §3.2 (BM25)               |
| [17] Devlin et al. 2019 (BERT) | §3.2 (MatcherAgent)      |
| [18] ГОСТ Р 55062-2021   | §1.6, §4.4, §4.5, Прил. 7, 8 |
| [19] ISO 16290:2013      | §3.7 (TRL)                    |
| [20] Apache License 2.0  | §5.3                          |
| [21] Moura–Ullrich 2021 (Lean 4) | §1.7, Гл. 2 (формализация) |
| [22] Mathlib Community 2020 | §1.7, Гл. 2                 |
| [23] Brown 2018 (C4)     | §3.1, Прил. 11                |
| [24] Beyer et al. 2016 (SRE) | §3.6, Прил. 10             |

---

## Дополнительная литература

Помимо указанных 20 ключевых источников, монография опирается на
следующие материалы (полные библиографические записи — в
соответствующих главах):

- Приказы Минцифры, Минздрава, ФНС России, на которые опираются
  предметные онтологии (см. [Прил. 3](appendix-03-ontology-catalog.md),
  раздел 10).
- ФЗ-149, ФЗ-152, ФЗ-210 (см. [Прил. 5](normative/fz-interoperability-draft.md),
  раздел «Нормативные ссылки»).
- Проекты ГОСТ Р (см. [Прил. 7](normative/gost-autonomous-systems-draft.md),
  [Прил. 8](normative/gost-ai-interoperability-draft.md)).
- Документация по технологиям: Akka Typed, Apache Jena, Scala 3,
  Prometheus, Grafana, Loki, Jaeger, Redis, PostgreSQL, Helm,
  Terraform, ArgoCD (URL указаны в соответствующих частях
  монографии).

---

## Список сокращений

- **CCC** — Cartesian Closed Category (декартово замкнутая категория)
- **DL** — Description Logic (дескрипционная логика)
- **DOI** — Digital Object Identifier
- **FAIR** — Findable, Accessible, Interoperable, Reusable
- **FORCE11** — Future of Research Communication and e-Scholarship
- **LISI** — Levels of Information Systems Interoperability
- **M3, N5** — запрещённые подрешётки в дистрибутивных решётках
- **OAEI** — Ontology Alignment Evaluation Initiative
- **RDF** — Resource Description Framework
- **SHACL** — Shapes Constraint Language
- **SLO/SLI/SLA** — Service Level Objective/Indicator/Agreement
- **TRL** — Technology Readiness Level
- **W3C** — World Wide Web Consortium

---

*Замечание.* Все DOI и URL проверены на 2026-08-13. В случае изменения
URL авторы рекомендуют обращаться к цифровым архивам (Internet
Archive Wayback Machine) или к Zenodo-зеркалам репозитория ASG-TOI.
