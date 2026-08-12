# Changelog

All notable changes to the **Adaptive Semantic Gateway (ASG / АСШ)** project
will be documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

Для русского перевода изменений см. секцию "Примечания к релизам" в
[`README.md`](README.md#дорожная-карта-sprint-13).

---

## [Unreleased]

### Added — Sprint 3 (Hardening & scale-out)

- **CI/CD pipeline**: 6-stage workflow `ci.yml` (build → static → unit-test →
  integration → load-test → deploy), total ≤ 130 min, with matrix builds
  (JDK 17 × Scala 3.3.x), Gradle + Docker layer caching, concurrency
  cancellation.
- **Separate workflows**: `lean-verify.yml` (nightly + on PR to `TOI/`),
  `economics-check.yml` (NPV/IRR/BCR range verification), `shacl-validate.yml`
  (Jena riot + SHACL validation of all `.ttl`/`.owl` files).
- **SBOM**: CycloneDX JSON SBOM generated for every `asg-core` Docker image
  in CI (via `anchore/sbom-action`).
- **Deployment**: Helm + ArgoCD GitOps-based deployment to staging (only on
  `main` push). Production via Yandex Cloud Managed K8s + Terraform.
- **Documentation**: 9 new docs — `README.md`, `docs/architecture.md` (C4
  Model + 6 ADRs), `docs/deployment.md`, `docs/api-reference.md`,
  `docs/verification-guide.md`, `docs/monitoring.md`, `docs/security.md`,
  `docs/CONTRIBUTING.md`, `CHANGELOG.md`, `LICENSE` (Apache 2.0).
- **`build.gradle.kts`**: extended with `sonarqube`, `jacoco` (coverage ≥ 80%),
  `checkstyle`, `spotbugs`, `docker` plugins. Application main class set to
  `ru.smev.asg.Main`. ShadowJar produces fat JAR `asg-core-0.1.0.jar`.

### Changed — Sprint 3

- **Concurrency control**: GitHub Actions workflows now cancel in-progress
  runs on new push to the same branch (`concurrency.cancel-in-progress: true`).
- **Docker layer caching**: Buildx cache moved between stages to avoid
  unbounded growth.

### Deprecated — Sprint 3

- The `cached` boolean field in `/api/v1/translate` response will be renamed
  to `cache_hit` in v0.3.0 (currently aliased — both work).

### Removed — Sprint 3

- Support for Scala 3.2.x (minimum required Scala version is now 3.3.3).
- The `master` git branch alias (use `main`).

### Fixed — Sprint 3

- **ShaclValidator cache bug** (#187): violation reports were cached
  without including the shape `path` in the cache key, leading to false
  positives when multiple shapes target the same node.
- **HPA scaling**: HPA now correctly scales on p95 latency (not average),
  reducing tail-latency SLO breaches during traffic spikes.

### Security — Sprint 3

- **JWT key rotation**: introduced 24-hour grace period via
  `ASG_JWT_SECRET_PREVIOUS` env var.
- **NetworkPolicies**: default-deny ingress + explicit allow-rules for
  Redis/Postgres/Jena/Jaeger egress.
- **RBAC**: 4 roles (admin, operator, observer, consumer) with explicit
  scope-per-endpoint enforcement.
- **Audit logging**: all `/translate` requests now write PROV-O records to
  `asg_audit.prov_activities` (retention 7 years per ФЗ-149).

---

## [0.2.0] — 2026-09-15 (planned — Sprint 2 release)

### Added — Sprint 2 (Production-ready)

- **Helm chart**: complete chart in `helm/` with templates (deployment,
  service, hpa, configmap, _helpers.tpl), values.yaml, Chart.yaml, and
  vendored dependencies (bitnami/redis 20.0.5, bitnami/postgresql 15.5.0).
- **Terraform**: Yandex Cloud Managed K8s provisioning (`terraform/main.tf`,
  `variables.tf`, `outputs.tf`) — 3 master nodes + 5 worker-nodes, multi-AZ.
- **ArgoCD integration**: `Application` manifest for GitOps-based
  deployment with `automated.prune` + `automated.selfHeal`.
- **Observability stack**:
  - Prometheus 2.54 (scrape config + alert rules in `prometheus/rules.yml`)
    with 6 P1 / 8 P2 / 4 P3 alerts.
  - Grafana 11.2 dashboards (provisioned via ConfigMap):
    `dashboard-operational.json` (10 panels, SLO thresholds),
    `dashboard-service.json` (cache, JVM, SHACL),
    `dashboard-business.json` (consumer SLA, ontology coverage).
  - Loki 3.1 (log aggregation, JSON-structured logs from asg-core).
  - Jaeger 1.60 (distributed tracing via OTLP 4317/4318).
- **k6 load tests** (3 scenarios):
  - `basic-load.js` — 10-min step load 100→10000 RPS.
  - `soak-test.js` — 24h @ 7000 RPS with memory-leak detection.
  - `stress-test.js` — ramping to breaking point with auto-recovery verdict.
- **Economics model** (Python):
  - `npv_irr_bcr.py` — base case NPV/IRR/BCR calculation.
  - `monte_carlo.py` — Monte-Carlo n=10000 with P(NPV>0) verification.
  - `sensitivity.py` — OAT + tornado analysis.
  - `little_law.py` — Little's Law capacity calculator.
  - `scenario_analysis.py` — 3-scenario comparison.
- **SHACL shapes** (4 + 1 SPARQL verifier):
  - `shapes/om1-hierarchy.ttl` — preservation of class hierarchy.
  - `shapes/om2-union.ttl`, `shapes/om2-intersection.ttl` — union/intersection.
  - `shapes/om3-role.ttl` — role (object property) preservation.
  - `sparql/ss1-verify.rq`, `sparql/ss2-verify.rq` — structural soundness.
- **Cross-domain ontology mapping** (`ontologies/cross-domain-mapping.ttl`)
  with qualified subclass equivalence (DL-correct Person ≡ Patient under INN).
- **Application config** (`asg-config.yaml` / HOCON): full akka.actor
  dispatcher config (default, blocking, validation), Redis/Postgres/Jena
  connection pools, JWT settings.
- **Ansible playbook** (`ansible/playbook.yml`) for bootstrap node
  configuration (Docker, kubelet, sysctl tuning).
- **k8s manifests**: raw deployment/service/hpa YAML (for non-Helm users).

### Changed — Sprint 2

- **Docker Compose**: expanded to 8 services (added Prometheus, Grafana,
  Loki, Jaeger). Health checks added to all services. Default logging
  driver set to `json-file` with rotation (50 MB × 5 files).
- **Dockerfile** (asg-core): multi-stage build (`gradle:8-jdk17` →
  `eclipse-temurin:17-jre-jammy`), non-root user UID 10001, `tini` as
  PID 1, healthcheck via curl, JVM-flags for ZGC + heap dump.
- **Resource limits** (k8s): requests `{cpu:1, memory:2Gi}`, limits
  `{cpu:2, memory:4Gi}`. `preStop` hook `sleep 15` for graceful drain.
- **Replicas**: 3 in staging, 5 in prod (podAntiAffinity by hostname).

### Fixed — Sprint 2

- **GrpcServer port conflict**: gRPC `9090` and Prometheus `9090` collided;
  Prometheus moved to host port `19090` (in docker-compose).
- **`depends_on` healthcheck**: replaced `condition: service_started` with
  `service_healthy` for Redis, Postgres, Jena to avoid startup race.

---

## [0.1.0] — 2026-08-01 (Sprint 1 — MVP)

### Added — Sprint 1 (Validation of concept)

- **Core ASG service** (Scala 3.3 + Akka Typed 2.8):
  - `Main.scala` — bootstrap ActorSystem + REST/gRPC servers.
  - `api/RestApi.scala` — Akka HTTP routes: `POST /translate`,
    `GET /health`, `GET /ready`, `GET /metrics`, `GET /ontology/{id}`.
    JWT (HS256) authentication.
  - `api/GrpcServer.scala` — Akka gRPC `TranslateService`.
  - 4 agents (`agents/`):
    - `MatcherAgent` — builds ontology alignment, computes confidence.
    - `ArbiterAgent` — coordinates verification, chooses 3-tier contour.
    - `LearnerAgent` — Hot-L escalation (manual in MVP, LLM in Sprint 3).
    - `ValidatorAgent` — runs SHACL + OWL2RL + SPARQL.
  - `ontology/OntologyRegistry.scala` — loads OWL from disk + Jena Fuseki.
  - `ontology/MappingRegistry.scala` — PostgreSQL persistence via Doobie.
  - `ontology/CacheManager.scala` — Lettuce/Redis LRU cache (TTL 300s).
  - `verification/ShaclValidator.scala` — Jena SHACL API wrapper.
  - `verification/Owl2RlReasoner.scala` — Jena OWL2RL forward-chaining.
  - `verification/SparqlVerifier.scala` — executes `ss1-verify.rq`,
    `ss2-verify.rq`.
  - `hotl/HotlContour.scala` — three-tier escalation logic.
  - `provenance/ProvORecorder.scala` — PROV-O audit logging.
- **Initial ontologies** (3 domains):
  - `ontologies/registration/v1.0.owl` (МВД — регистрация граждан).
  - `ontologies/tax/v1.0.owl` (ФНС — налоги).
  - `ontologies/healthcare/v1.0.owl` (ФОМС — здравоохранение).
  - All ontologies include INN/SNILS/ОМС datatype restrictions
    (OWL 2 `owl:onDatatype` + `owl:withRestrictions`).
- **Lean 4 formalization of Theorem 1.1**:
  - `TOI/Axioms.lean` — axioms of ontology & morphism.
  - `TOI/Theorems/T11_Infinite.lean` — Theorem 1.1 for infinite domains.
  - `TOI/Theorems/T11_Finite.lean` — Theorem 1.1 for finite domains.
  - `TOI/Lemmas/TopPreserved.lean`, `RoleRestrict.lean`, `NegPreserved.lean`,
    `BoolExt.lean` — auxiliary lemmas.
  - `TOI/Countermodels/A.lean` — countermodel showing necessity of
    interpretation-preservation hypothesis.
  - `lakefile.lean` — Lake configuration with Mathlib4 dependency.
- **Docker Compose stack** (initial): asg-core + Redis + PostgreSQL + Jena.
- **`build.gradle.kts`** (Scala 3.3 + Akka 2.8 + Jena 4.10 + Lettuce 6.3 +
  Circe 0.14 + Doobie 1.0-RC5). ShadowJar for fat JAR.

---

## Versioning policy

This project follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html):

- **MAJOR** (X.0.0): incompatible API changes (e.g., removed endpoints,
  changed response schema without backward compatibility).
- **MINOR** (0.X.0): new features, backward-compatible (new endpoint,
  new optional field in response).
- **PATCH** (0.0.X): bugfixes, backward-compatible.

Pre-release versions follow `X.Y.Z-rcN` format (e.g., `0.2.0-rc1`).

## Release cadence

- **Sprint release** (every 4 weeks): minor version bump (`0.X.0`).
- **Hotfix** (as needed): patch version bump (`0.0.X`), branch from `main`.
- **Major release**: as needed, with deprecation period ≥ 6 months.

## Changelog management

- Entries are added under `[Unreleased]` during development.
- At release time, `[Unreleased]` is renamed to `[X.Y.Z] — YYYY-MM-DD` and
  a new empty `[Unreleased]` section is created.
- Categories (per [Keep a Changelog](https://keepachangelog.com/)):
  `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.
- Each entry should reference a GitHub issue or PR (e.g., `(#142)`).

## Migration guides

For major releases, a separate migration guide will be added at
`docs/migrations/X-to-Y.md` with step-by-step instructions for consumers.
