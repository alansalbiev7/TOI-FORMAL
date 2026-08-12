# Конвейер CI/CD ASG

## Назначение

Диаграмма описывает 6-стадийный конвейер непрерывной интеграции и
доставки ASG. Конвейер запускается при `push` в `main`/`develop`
или открытом Pull Request. Управление релизами в staging и prod
осуществляется через GitOps (ArgoCD + Helm). Все артефакты
(Docker-образы, SBOM, SonarQube-отчёты) сохраняются в реестре
`ghcr.io/smev` и Artifactory.

## Диаграмма

```mermaid
flowchart TD
  classDef trigger fill:#37474F,stroke:#263238,color:#FFFFFF
  classDef stage   fill:#1168BD,stroke:#0B4884,color:#FFFFFF
  classDef check   fill:#6A1B9A,stroke:#4A148C,color:#FFFFFF
  classDef artifact fill:#00838F,stroke:#004D40,color:#FFFFFF
  classDef deploy  fill:#2E7D32,stroke:#1B5E20,color:#FFFFFF
  classDef fail    fill:#C62828,stroke:#B71C1C,color:#FFFFFF

  Start([Trigger: push / pull_request]):::trigger

  subgraph S1["Стадия 1 — build"]
    direction TB
    B1["Gradle shadowJar<br/>(asg-core/build.gradle.kts)"]:::stage
    B2["Docker build<br/>(asg-core/Dockerfile)"]:::stage
    B3["Generate SBOM<br/>(CycloneDX plugin)"]:::artifact
    B1 --> B2 --> B3
  end

  subgraph S2["Стадия 2 — static analysis"]
    direction TB
    Q1["SonarQube scan"]:::check
    Q2["Checkstyle (google_checks.xml)"]:::check
    Q3["SpotBugs (findsecbugs)"]:::check
    Q1 --> Q2 --> Q3
  end

  subgraph S3["Стадия 3 — unit tests"]
    direction TB
    U1["JUnit 5 (ScalaTest bridge)"]:::stage
    U2["Coverage ≥ 80%<br/>(JaCoCo + scoverage)"]:::check
    U1 --> U2
  end

  subgraph S4["Стадия 4 — integration tests"]
    direction TB
    I1["Testcontainers<br/>(PostgreSQL + Redis + Jena)"]:::stage
    I2["IntegrationSpec<br/>(asg-core/src/test/.../IntegrationSpec.scala)"]:::stage
    I1 --> I2
  end

  subgraph S5["Стадия 5 — load tests"]
    direction TB
    L1["k6 basic-load.js<br/>(50 RPS, 5 min)"]:::stage
    L2["k6 stress-test.js<br/>(ramp до 500 RPS)"]:::stage
    L3["k6 soak-test.js<br/>(8h, 100 RPS)"]:::stage
    L1 --> L2 --> L3
  end

  subgraph S6["Стадия 6 — deploy (GitOps)"]
    direction TB
    D1["Helm chart packaged<br/>(helm/charts/*.tgz)"]:::deploy
    D2["ArgoCD sync → staging<br/>(k8s namespace: asg-staging)"]:::deploy
    D3["Smoke + canary 10%"]:::check
    D4{Canary success?}:::check
    D5["Promote → prod<br/>(manual approval)"]:::deploy
    D1 --> D2 --> D3 --> D4
    D4 -- "Yes" --> D5
    D4 -- "No" --> Rollback["Auto-rollback<br/>(ArgoCD sync to previous)"]:::fail
  end

  Start --> S1
  S1 --> S2
  S2 --> S3
  S3 --> S4
  S4 --> S5
  S5 --> S6

  S2 -.->|fail| Stop1([Stop pipeline]):::fail
  S3 -.->|fail| Stop1
  S4 -.->|fail| Stop1
  S5 -.->|fail| Stop2([Block deploy,<br/>post to Slack #asg-ci]):::fail

  B3 -.->|artifact| Reg[("ghcr.io/smev/asg-core:${GIT_SHA}<br/>+ SBOM CycloneDX JSON")]:::artifact
  D5 -.->|artifact| ArgoCDapp[("ArgoCD application<br/>asg-prod")]:::artifact
```

## Описание стадий

### Стадия 1 — build

- **Сборка**: `./gradlew :asg-core:shadowJar` (Fat JAR).
- **Docker**: multi-stage build на `eclipse-temurin:21-jre-alpine`.
- **SBOM**: CycloneDX Gradle-плагин, артефакт
  `asg-core/build/reports/bom.json` (формат CycloneDX 1.5).
- **Время**: ~3 мин.
- **Артефакт**: `ghcr.io/smev/asg-core:${GIT_SHA}` + SBOM.

### Стадия 2 — static analysis

- **SonarQube**: quality gate `ASG-Way` (см. `sonar-project.properties`).
- **Checkstyle**: конфиг `google_checks.xml` + кастомные правила
  наименования акторов (`*Agent`, `*Validator`, `*Registry`).
- **SpotBugs**: с плагином `findsecbugs` (security hotspots).
- **Время**: ~2 мин.

### Стадия 3 — unit tests

- **Фреймворк**: ScalaTest + JUnit 5 bridge.
- **Покрытие**: scoverage ≥ 80% (Scala) + JaCoCo ≥ 80% (Java).
- **Проверяемые классы**: `MatcherAgentSpec`, `ValidatorAgentSpec`,
  `ArbiterAgentSpec`, `LearnerAgentSpec`, `ShaclValidatorSpec`,
  `OntologyRegistrySpec`, `RestApiSpec`.
- **Время**: ~4 мин.

### Стадия 4 — integration tests

- **Подход**: Testcontainers (PostgreSQL 15, Redis 7, Jena Fuseki 4.10).
- **Сценарии**: `IntegrationSpec` — полный цикл
  S0→S1→S2→S0 (valid) и S0→S1→S2→S3→S0 (warning/escalation).
- **Время**: ~6 мин.

### Стадия 5 — load tests

- **Инструмент**: k6, скрипты `tests/k6/*.js`.
- **Сценарии**:
  - `basic-load.js` — 50 RPS × 5 мин, p95 < 200ms.
  - `stress-test.js` — ramp до 500 RPS, проверка деградации.
  - `soak-test.js` — 8h × 100 RPS, проверка утечек памяти.
- **Время**: 5 мин (basic+stress), soak — отдельный nightly job.

### Стадия 6 — deploy (GitOps)

- **Подход**: GitOps через ArgoCD, Helm-чарт `helm/`.
- **Staging**: автосинхронизация после успешных стадий 1–5.
- **Canary**: 10% трафика → staging-образ в prod namespace через
  Argo Rollouts.
- **Promote → prod**: ручное approval в ArgoCD (2 пары глаз).
- **Rollback**: автоматический при ошибке canary (ArgoCD sync к
  предыдущей ревизии `helm/Chart.yaml`).

## Связанные файлы

- Конфигурация CI: `.github/workflows/ci.yml` (плагин GitHub Actions).
- Helm-чарт: [`helm/`](../../helm/)
- K8s-манифесты: [`k8s/`](../../k8s/)
- SBOM-политика: [`normative/sbom-policy.md`](../../normative/sbom-policy.md)
- k6 скрипты: [`tests/k6/`](../../tests/k6/)
