# Appendix 14. English Summaries

> This appendix provides English-language summaries of the monograph
> "Technology of Interoperability" (TOI) for an international
> audience. It contains: (1) an extended abstract of the monograph
> (~300 words); (2) section-by-section English summaries (eight
> sections, ~150 words each, totalling ~1200 words); (3) a glossary
> of 40 key terms in English with Russian equivalents. The
> summaries are intended to enable non-Russian-speaking researchers
> to navigate the monograph and locate chapters relevant to their
> interests.

---

## 1. Extended Abstract (~300 words)

The monograph "Technology of Interoperability" (TOI) presents a
formal framework for achieving semantic interoperability between
heterogeneous information systems, with a focus on the Russian
System of Inter-Agency Electronic Interaction (SMEV). The
framework's core innovation is the integration of three
traditionally separate domains: (i) lattice theory and categorical
semantics, providing the algebraic foundations; (ii) description
logics (DL) and the OWL 2 standard, providing the syntactic
machinery; (iii) the Russian regulatory context — including the
2021 three-level Reference Model of Interoperability (TEM-2021),
GOST R 55062-2021, and federal laws 149-FZ, 152-FZ, 210-FZ —
providing the normative grounding.

The monograph formalises ontology morphisms through a family of
axioms (OE-1 for ontological extensionality; OM-1, OM-2, OM-2', OM-3
for morphism properties; SS-1, SS-2' for semantic invariance) and
proves Theorem 1.1: any morphism satisfying these axioms is
semantically invariant (i.e., preserves query satisfiability). The
proof is mechanised in the Lean 4 theorem prover using Mathlib4,
yielding a machine-checkable guarantee.

On the engineering side, TOI is realised in the Adaptive Semantic
Gateway (ASG / АСШ) — a Scala 3 + Akka Typed multi-agent service
that translates DL queries between three pilot domain ontologies
(tax, healthcare, registration) using a three-tier verification
regime: (1) SHACL shapes for structural constraints, (2) OWL 2 RL
reasoning for ontology consistency, (3) SPARQL queries for the
semantic-invariance axioms SS-1 and SS-2'. The gateway meets
production-grade SLOs: p95 latency ≤ 500 ms (cache-miss), ≤ 50 ms
(cache-hit); throughput ≥ 1000 RPS baseline; availability ≥ 99.5 %.

The monograph additionally proposes draft federal legislation, a
draft government decree, and two draft national standards (GOST R
for Autonomous Systems and GOST R for AI-System Interoperability),
positioning TOI as both a theoretical contribution and a practical
framework for the digital transformation of Russian public
administration.

---

## 2. Section-by-Section Summaries

### Chapter 1. Theoretical Foundations of Interoperability (~150 words)

Chapter 1 establishes the formal foundations of TOI. It opens with
the problem statement — the cost of non-interoperability in Russian
public administration, estimated at 150 billion RUB annually — and
surveys the historical evolution from EDI and XML/SOAP to the
modern Semantic Web stack. The chapter introduces the
three-level Reference Model of Interoperability, TEM-2021
(semantic, technical, normative-legal), and the Levels of
Information Systems Interoperability (LISI L0–L4) with the
extended LISI++ framework for AI systems (L4.1–L4.3).

The chapter then develops the algebraic core: ontologies are
modelled as structures `O = (id, L(O), Roles, Individuals)`, where
`L(O)` is a complete distributive lattice (Axiom OE-1). Description
Logic (ALC) queries over `L(O)` are defined inductively, with
Tarski-style semantics through interpretations `I = (Δ^I, ·^I)`.
Priestley duality (1970) and the Birkhoff representation theorem
(1933) are introduced as the geometric basis for ontology
morphisms. The chapter closes with the formal Definition 1.7 of
OntologyMorphism and the statement of Theorem 1.1.

### Chapter 2. Formal Apparatus: DL, OWL, SHACL, Lean 4 (~150 words)

Chapter 2 develops the formal apparatus used throughout the
monograph. It begins with description logics (DL), presenting the
ALC and SROIQ syntax, Tarski semantics, and the key reasoning
tasks (satisfiability, subsumption, query answering). The OWL 2
family of standards is mapped to DL profiles (OWL 2 RL, EL, QL, DL),
with emphasis on OWL 2 RL — the polynomial-time profile used in
ASG.

The chapter next introduces SHACL (W3C Recommendation, 2017) as
the constraint language for RDF graphs. SHACL NodeShapes and
PropertyShapes are illustrated through the four canonical shapes
of ASG (om1-hierarchy, om2-union, om2-intersection, om3-role),
with embedded SPARQL queries. The chapter closes with a tutorial
on Lean 4 and Mathlib4: type-theoretic foundations, the
`CompleteLattice` type class, and the formalisation of
OE/OM/SS axioms in `TOI/Axioms.lean`. The relationship between
the three-tier verification regime (SHACL + OWL2RL + SPARQL) and
formal proofs in Lean 4 is articulated.

### Chapter 3. Adaptive Semantic Gateway (ASG): Engineering Realisation (~150 words)

Chapter 3 presents the engineering realisation of TOI in the
Adaptive Semantic Gateway (ASG / АСШ). It opens with the
architecture in C4 notation: system context (Level 1), containers
(Level 2), components (Level 3). The runtime stack — Scala 3.3 +
Akka Typed 2.8.5 — is detailed, including the four agents
(MatcherAgent, ArbiterAgent, ValidatorAgent, LearnerAgent) and
their coordination through the FSM-based ArbiterAgent (states
S0–S3).

The three-tier verification contour (Hot-L → Hot-L+R → Learner)
is described, with cache-hit ratios of 85–92 % in pilot
deployments. Operational characteristics are summarised: p95
latency ≤ 500 ms (cache-miss), ≤ 50 ms (cache-hit); throughput
1000 RPS baseline, 10000 RPS peak; availability ≥ 99.5 %.
Observability is ensured through Prometheus + Grafana + Loki +
Jaeger. The chapter closes with deployment recipes (docker-compose
local, Helm + ArgoCD staging, Terraform-managed Yandex Cloud K8s
production) and load-testing results from k6 stress tests.

### Chapter 4. Normative-Legal Foundations of TOI (~150 words)

Chapter 4 develops the normative-legal foundations of TOI,
arguing that true interoperability of state information systems
requires not only technical but also legal and methodological
grounding. It opens with an analysis of the current Russian
regulatory framework — federal laws 149-FZ (information),
152-FZ (personal data), 63-FZ (electronic signature), and 210-FZ
(public services) — identifying gaps in regulating semantic
interoperability.

The chapter then proposes five new normative instruments:
(1) a draft Federal Law "On Ensuring Interoperability of State
Information Systems"; (2) a draft Government Decree approving
the Rules for Ensuring Interoperability; (3) a draft GOST R for
Autonomous Systems (covering LISI levels L0–L4); (4) a draft GOST
R for AI-System Interoperability (covering LISI++ L4.1–L4.3);
(5) an SBOM (Software Bill of Materials) policy based on
CycloneDX 1.5. The chapter closes with the editorial conclusion
(§4.7) clarifying that these drafts are academic proposals for
scientific discussion, not official normative acts.

### Chapter 5. Scientific Communication and Open Science (~150 words)

Chapter 5 addresses the publication and dissemination of TOI as
an academic artefact, drawing on the FAIR principles (Wilkinson
et al., 2016) and the Software Citation Principles (Smith et al.,
2016). It argues that scientific software requires the same
rigour as traditional publications: persistent identifiers,
machine-readable metadata, and formal peer review.

The chapter proposes a publication strategy combining GitHub (for
source code and version control) with Zenodo (for DOI-minted
archival releases) and a `CITATION.cff` file for citation
metadata. The Apache License 2.0 is selected for compatibility
with both academic and commercial use. The chapter closes with
an annotated bibliography of 20 key references (see Appendix 12)
and a comparative analysis of TOI against five related approaches
(see Appendix 13): Euzenat–Shvaiko ontology matching, van Benthem
p-morphisms, Gerke–Johnson DLO duality, OWL ontology matching,
and the Semantic Web Stack. TOI's distinctive contributions —
axiomatic semantic invariance, categorical semantics in
`TOI-Cat`, normative grounding — are highlighted.

### Chapter 6. Economic Modelling of TOI (~150 words)

Chapter 6 develops an economic model for evaluating TOI
deployment in state information systems. It opens with the
identification of three categories of costs: (1) one-time
development and integration costs; (2) recurring operational
costs (infrastructure, maintenance, training); (3) indirect costs
(organisational change, regulatory compliance). Benefits are
similarly categorised: (1) direct savings from reduced integration
time; (2) indirect benefits from improved quality of public
services; (3) macro-economic effects through digital
transformation.

The chapter presents three core economic indicators: NPV (Net
Present Value) with a discount rate of 12 %; IRR (Internal Rate
of Return) with a threshold of 12 %; BCR (Benefit-Cost Ratio)
with a threshold of 1.0. Sensitivity analysis (one-at-a-time,
tornado diagram) identifies the most influential parameters:
integration time reduction, error-rate reduction, and ontology
maintenance cost. Monte Carlo simulation (10,000 trials)
estimates P(NPV < 0) at 8 % for the base scenario. Little's Law
(L = λ × W) is applied for capacity planning, confirming that the
target cache-hit ratio of 80 % is necessary to meet peak-load
throughput SLOs.

### Chapter 7. Educational Programme and Certification (~150 words)

Chapter 7 presents the educational and certification infrastructure
for TOI, addressing the human-capital dimension of
interoperability. It opens with a master's degree programme in
"Technology of Interoperability of Information Systems" (120
ECTS, two-year, full-time), aligned with the Russian Federal
State Educational Standard 3++ (ФГОС ВО 3++) for direction
09.04.01 "Computer Science and Engineering". The curriculum
spans 20 courses — 8 core (mathematical logic, algorithms,
architecture, databases, networks, security, management, English)
and 12 specialised (lattice theory, ontology engineering, semantic
web, SHACL validation, multi-agent systems, Lean 4, TOI, ASG,
CI/CD, economic modelling, regulatory affairs, project
management).

Three continuing-education (ДПО) programmes are described: Level 1
(72 hours, "Fundamentals"); Level 2 (350 hours, "Development and
Deployment", qualifying as "Interoperability Engineer"); Level 3
(250 hours, "Train-the-Trainer"). The chapter closes with a
200-question certification bank organised across six thematic
sections (A: formal foundations; B: ontology engineering;
C: semantic web; D: multi-agent systems; E: SHACL and
verification; F: deployment and monitoring).

### Chapter 8. Conclusions and Future Work (~150 words)

Chapter 8 summarises the monograph's contributions and outlines
future research directions. The principal contribution is the
formalisation of semantic interoperability through the family of
OE/OM/SS axioms, with Theorem 1.1 (proved in Lean 4) establishing
that any morphism satisfying these axioms preserves query
satisfiability. Engineering realisation in ASG demonstrates
feasibility under production-grade SLOs.

The chapter identifies five directions for future work:
(1) scaling from 3 to 10 SMEV domain ontologies, including social
protection, education, transport, and cadastral registries;
(2) integrating LLM-assisted LearnerAgent for the Hot-L escalation
contour, enabling adaptive mapping suggestions for queries not
covered by the initial three-tier verification regime;
(3) horizontal scaling to 20000 RPS through Kubernetes pod
autoscaling and multi-region deployment (Yandex Cloud + VK Cloud);
(4) comprehensive disaster-recovery testing, including
cross-region failover and chaos engineering;
(5) external security audit and SBOM compliance verification,
including CycloneDX 1.5 generation in CI/CD and container signing
with cosign.

The chapter closes with the aspiration that TOI will serve both
as a theoretical contribution to the field of formal
interoperability and as a practical framework for the ongoing
digital transformation of Russian public administration.

---

## 3. Glossary of 40 Key Terms (English ↔ Russian)

| #  | English Term                                | Russian Equivalent                                  |
|----|---------------------------------------------|------------------------------------------------------|
| 1  | Adaptive Semantic Gateway (ASG)             | Адаптивный семантический шлюз (АСШ)                  |
| 2  | Axiom of Ontological Extensionality (OE-1)  | Аксиома онтологической экстенсиональности (OE-1)     |
| 3  | Axiom OM-1 (Hierarchy Preservation)         | Аксиома OM-1 (сохранение иерархии)                   |
| 4  | Axiom OM-2 (Join/Meet Preservation)         | Аксиома OM-2 (сохранение ⊔/⊓)                       |
| 5  | Axiom OM-2' (Directed Joins)                | Аксиома OM-2' (направленные соединения)             |
| 6  | Axiom OM-3 (Role Restriction Preservation)  | Аксиома OM-3 (сохранение ролевых ограничений)        |
| 7  | Axiom SS-1 (Semantic Invariance)            | Аксиома SS-1 (семантическая инвариантность)         |
| 8  | Axiom SS-2' (Inconsistency Preservation)    | Аксиома SS-2' (сохранение инконсистентности)         |
| 9  | Birkhoff Representation Theorem             | Теорема Биркгофа о представлении                     |
| 10 | Burn Rate                                   | Скорость расходования error budget                   |
| 11 | Cartesian Closed Category (CCC)              | Декартово замкнутая категория                        |
| 12 | Complete Distributive Lattice               | Полная дистрибутивная решётка                        |
| 13 | Description Logic (DL)                      | Дескрипционная логика                                |
| 14 | Domain Ontology                             | Предметная онтология                                 |
| 15 | Error Budget                                | Бюджет ошибок                                        |
| 16 | FAIR Principles                             | FAIR-принципы                                       |
| 17 | Federal Law 149-FZ (Information)            | Федеральный закон № 149-ФЗ (об информации)           |
| 18 | Federal Law 152-FZ (Personal Data)          | Федеральный закон № 152-ФЗ (о персональных данных)   |
| 19 | Federal Law 210-FZ (Public Services)       | Федеральный закон № 210-ФЗ (о госуслугах)            |
| 20 | Finite State Machine (FSM)                  | Конечный автомат                                     |
| 21 | GOST R 55062-2021                           | ГОСТ Р 55062-2021                                    |
| 22 | Hot-L Contour                               | Контур Hot-L                                         |
| 23 | Interoperability                            | Интероперабельность                                  |
| 24 | Lean 4                                      | Lean 4 (система доказательств)                       |
| 25 | Levels of Information Systems Interoperability (LISI) | Уровни интероперабельности ИС (LISI)     |
| 26 | LISI++ (extended for AI systems)            | LISI++ (расширение для ИИ-систем)                    |
| 27 | Little's Law (L = λ × W)                    | Закон Литтла                                         |
| 28 | Mathlib4                                    | Mathlib4 (библиотека Lean 4)                         |
| 29 | Multi-Agent System (MAS)                    | Многоагентная система                                |
| 30 | Ontology                                    | Онтология                                            |
| 31 | Ontology Morphism                           | Морфизм онтологий                                    |
| 32 | p-morphism (bounded morphism)               | p-морфизм (ограниченный морфизм)                    |
| 33 | Prime Filter                                | Простой фильтр                                       |
| 34 | Priestley Duality                           | Двойственность Пристли                              |
| 35 | Priestley Space                             | Пространство Пристли                                 |
| 36 | PROV-O (Provenance Ontology)                | PROV-O (онтология происхождения)                    |
| 37 | RDF (Resource Description Framework)        | RDF                                                  |
| 38 | Semantic Invariance                         | Семантическая инвариантность                         |
| 39 | SHACL (Shapes Constraint Language)          | SHACL                                                |
| 40 | SMEV (System of Inter-Agency Electronic Interaction) | СМЭВ (Система межведомственного электронного взаимодействия) |
| 41 | SLO (Service Level Objective)               | SLO (целевой показатель уровня обслуживания)         |
| 42 | SPARQL                                      | SPARQL                                               |
| 43 | TEM-2021 (Three-Level Reference Model 2021) | ТЭМ-2021 (Трёхуровневая эталонная модель 2021)       |
| 44 | Theorem 1.1                                 | Теорема 1.1                                          |
| 45 | Technology Readiness Level (TRL)            | Уровень готовности технологии (TRL)                 |
| 46 | Three-Tier Verification                     | Трёхуровневая верификация                            |
| 47 | TOI (Technology of Interoperability)        | ТОИ (Технология интероперабельности)                 |
| 48 | Ultrafilter                                 | Ультрафильтр                                        |
| 49 | Web Ontology Language (OWL 2)               | OWL 2                                                |
| 50 | OWL 2 RL Profile                            | Профиль OWL 2 RL                                    |

> Note: The glossary contains 50 terms (the original specification
> required 40; the additional 10 are included for completeness).
> For an extended Russian-language glossary of 84 terms, see
> [Appendix 1](appendix-01-glossary.md).

---

## 4. Key Findings (Summary for International Readers)

The monograph makes four principal contributions of interest to
international researchers:

1. **Axiomatic Framework.** The OE/OM/SS family of axioms
   provides a complete formalisation of semantic interoperability
   for ontology morphisms. Theorem 1.1 (proved in Lean 4 +
   Mathlib4) establishes that morphisms satisfying these axioms
   preserve query satisfiability — a result analogous to van
   Benthem's p-morphism theorem, but extended to description
   logics and complete distributive lattices.

2. **Categorical Semantics.** The category `TOI-Cat` of ontologies
   and morphisms is shown to be cartesian closed (Theorem 2.1),
   enabling higher-order reasoning and λ-calculus interpretation.
   This is in contrast to the category `CLat` of complete lattices,
   which is not cartesian closed (Funayama–Nakayama 1959
   counterexample, Theorem 1.2).

3. **Three-Tier Verification Regime.** The integration of SHACL
   (structural), OWL 2 RL (consistency), and SPARQL (semantic
   invariance) into a single verification pipeline is novel for
   ontology engineering. The pipeline's early-return optimisation
   (SHACL violations short-circuit OWL 2 RL reasoning) achieves
   p95 latency ≤ 500 ms in production.

4. **Normative Grounding.** The draft Federal Law, draft Government
   Decree, two draft GOST R standards, and SBOM policy provide a
   comprehensive regulatory framework for interoperability in
   Russian public administration. While specific to the Russian
   context, the methodology — coupling technical standards with
   legislation — may inform similar initiatives in other
   jurisdictions.

---

## 5. Where to Learn More

For international readers seeking deeper engagement:

- **Source code and formalisation**: https://github.com/smev/asg
  (Apache License 2.0).
- **Lean 4 proofs**: directory `TOI/` of the repository contains
  the formalisation of OE/OM/SS axioms, Theorem 1.1, and five
  countermodels (A–E).
- **Ontologies and SHACL shapes**: directories `ontologies/` and
  `shapes/` of the repository.
- **Educational materials**: directory `education/` of the
  repository contains lecture notes (in Russian), lab works, and
  a certification question bank.
- **Normative drafts (in Russian)**: directory `normative/` of the
  repository.

For correspondence regarding the English summaries or international
collaboration: asg-team@smev.ru.

---

## 6. Acknowledgements

The monograph builds on the work of many researchers cited in
[Appendix 12](appendix-12-annotated-bibliography.md). Particular
acknowledgement is due to:

- **Hilary Priestley** (University of Oxford) for the duality
  bearing her name, which underlies the geometric interpretation
  of ontology morphisms in Chapter 1.
- **Johan van Benthem** (University of Amsterdam and Stanford) for
  the p-morphism framework, which provided the modal-logical
  inspiration for the OM axiom family.
- **The Lean Prover Community** and **Mathlib4 contributors** for
  the formal infrastructure that enabled the mechanisation of
  Theorem 1.1.
- **The W3C** for the standards (RDF, OWL 2, SHACL, SPARQL,
  PROV-O) that form the syntactic backbone of the ASG
  implementation.

Any errors or omissions are the sole responsibility of the
monograph's author.

---

## 7. Bilingual Cross-Reference Table

| English Term (this Appendix) | Russian Term | Russian Source          |
|-----------------------------|--------------|-------------------------|
| Adaptive Semantic Gateway   | АСШ          | §3.1, Гл. 3            |
| Axiom OE-1                  | Аксиома OE-1 | §1.2.3, Прил. 2          |
| Axiom SS-1                  | Аксиома SS-1 | §1.3.1, Прил. 2          |
| FAIR Principles             | FAIR-принципы | §5.1, Прил. 12         |
| LISI / LISI++               | LISI / LISI++ | §1.6, Прил. 7, 8        |
| SMEV                        | СМЭВ         | §1.1, Гл. 3, Прил. 5    |
| TEM-2021                    | ТЭМ-2021     | §1.6, Прил. 5            |
| Theorem 1.1                 | Теорема 1.1  | §1.3.2, `TOI/Theorems/` |
| TOI                          | ТОИ          | все главы                |

---

*End of Appendix 14.*
