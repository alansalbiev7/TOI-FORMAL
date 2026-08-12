// =============================================================================
// build.gradle.kts — Adaptivnyj Semantičeskij Šljuz (ASG / АСШ)
// Module: asg-core
//
// Build configuration for the ASG core: Scala 3.3.x + Akka Typed 2.8.x +
// Apache Jena 4.10.x + Lettuce (Redis) + Akka gRPC + Circe + Doobie (PostgreSQL).
// Produces a fat JAR via ShadowJar (application main class: ru.smev.asg.Main).
//
// Plugins:
//   - scala                : Scala 3 compiler integration
//   - application          : runnable application (gradle run)
//   - shadow               : fat-JAR packaging (ShadowJar)
//   - com.palantir.docker  : Docker image build via Dockerfile
//   - sonarqube            : static analysis / coverage upload to SonarQube
//   - jacoco                : code-coverage report (≥ 80% gate)
//   - akka.grpc             : Akka gRPC server code generation
//   - checkstyle + spotbugs : Java-side static analysis (called from CI)
//
// Java target: 17 (Temurin / OpenJDK).
// Build artifacts:
//   - build/libs/asg-core-0.1.0.jar        (fat JAR via shadowJar)
//   - build/reports/jacoco/test/html/      (coverage HTML report)
//   - build/reports/sonar/                 (SonarQube analysis)
//   - build/docker/                        (Docker build context)
// =============================================================================

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.palantir.gradle.docker.DockerExtension

plugins {
    scala
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.palantir.docker") version "0.36.0"
    id("org.sonarqube") version "5.0.0.4638"
    jacoco
    id("com.lightbend.akka.grpc.gradle") version "2.2.0"
    // checkstyle + spotbugs — применяются только к Java-классам (если они есть)
    checkstyle
    id("com.github.spotbugs") version "6.0.18"
}

repositories {
    mavenCentral()
    maven("https://repo.akka.io/maven")
    maven("https://jitpack.io")   // для com.palantir.docker при необходимости
}

// -----------------------------------------------------------------------------
// Versions — single source of truth (Scala 3.3.x + Akka 2.8.x + Jena 4.10.x).
// -----------------------------------------------------------------------------
object Versions {
    // Language / runtime
    const val Scala       = "3.3.3"
    const val ScalaBinary = "3"   // для cross-публикации _3

    // Akka (Typed + HTTP + gRPC)
    const val Akka       = "2.8.5"
    const val AkkaHttp   = "10.5.3"
    const val AkkaGrpc   = "2.2.0"

    // RDF / OWL / SHACL / SPARQL
    const val Jena       = "4.10.0"

    // Data stores
    const val Lettuce    = "6.3.2.RELEASE"   // Redis
    const val Doobie     = "1.0.0-RC5"       // PostgreSQL (Cats-Effect 3)
    const val Postgres   = "42.7.3"           // JDBC driver (для HikariCP)
    const val HikariCP   = "5.1.0"

    // JSON / serialization
    const val Circe      = "0.14.6"

    // Config / logging
    const val Typesafe   = "1.4.3"
    const val Logback    = "1.4.14"
    const val Slf4j      = "2.0.11"

    // Observability
    const val Micrometer = "1.13.3"
    const val Prometheus = "1.3.1"   // io.prometheus:simpleclient_*

    // gRPC / OpenTelemetry
    const val GrpcJava   = "1.66.0"
    const val OtelVersion = "1.40.0"

    // Tests
    const val ScalaTest  = "3.2.18"
    const val JUnit      = "5.10.2"
    const val Testcontainers = "1.20.1"
}

// -----------------------------------------------------------------------------
// Dependencies
// -----------------------------------------------------------------------------
dependencies {
    // ── Scala 3 standard library ────────────────────────────────────────────
    implementation("org.scala-lang:scala3-library_${Versions.ScalaBinary}:${Versions.Scala}")

    // ── Akka Typed core + streams + persistence ─────────────────────────────
    implementation("com.typesafe.akka:akka-actor-typed_${Versions.ScalaBinary}:${Versions.Akka}")
    implementation("com.typesafe.akka:akka-stream_${Versions.ScalaBinary}:${Versions.Akka}")
    implementation("com.typesafe.akka:akka-persistence-typed_${Versions.ScalaBinary}:${Versions.Akka}")

    // ── Akka HTTP (REST fallback) + JSON support ────────────────────────────
    implementation("com.typesafe.akka:akka-http_${Versions.ScalaBinary}:${Versions.AkkaHttp}")
    implementation("com.typesafe.akka:akka-http-spray-json_${Versions.ScalaBinary}:${Versions.AkkaHttp}")

    // ── Akka gRPC server (TranslateService) ────────────────────────────────
    implementation("com.lightbend.akka.grpc:akka-grpc-runtime_${Versions.ScalaBinary}:${Versions.AkkaGrpc}")
    implementation("io.grpc:grpc-netty:${Versions.GrpcJava}")
    implementation("io.grpc:grpc-stub:${Versions.GrpcJava}")
    implementation("io.grpc:grpc-protobuf:${Versions.GrpcJava}")

    // ── Apache Jena — ontology / RDF / SHACL / SPARQL ──────────────────────
    implementation("org.apache.jena:jena-core:${Versions.Jena}")
    implementation("org.apache.jena:jena-arq:${Versions.Jena}")
    implementation("org.apache.jena:jena-shacl:${Versions.Jena}")
    implementation("org.apache.jena:jena-ontapi:${Versions.Jena}")
    implementation("org.apache.jena:jena-tdb2:${Versions.Jena}")

    // ── Redis (Lettuce client) — LRU cache ─────────────────────────────────
    implementation("io.lettuce:lettuce-core:${Versions.Lettuce}")

    // ── Circe — JSON for REST/gRPC payloads ────────────────────────────────
    implementation("io.circe:circe-core_${Versions.ScalaBinary}:${Versions.Circe}")
    implementation("io.circe:circe-generic_${Versions.ScalaBinary}:${Versions.Circe}")
    implementation("io.circe:circe-parser_${Versions.ScalaBinary}:${Versions.Circe}")

    // ── Doobie — PostgreSQL access (MappingRegistry) ────────────────────────
    implementation("org.tpolecat:doobie-core_${Versions.ScalaBinary}:${Versions.Doobie}")
    implementation("org.tpolecat:doobie-postgres_${Versions.ScalaBinary}:${Versions.Doobie}")
    implementation("org.tpolecat:doobie-hikari_${Versions.ScalaBinary}:${Versions.Doobie}")
    implementation("org.postgresql:postgresql:${Versions.Postgres}")
    implementation("com.zaxxer:HikariCP:${Versions.HikariCP}")

    // ── Typesafe config (HOCON) ─────────────────────────────────────────────
    implementation("com.typesafe:config:${Versions.Typesafe}")

    // ── Logging (SLF4J → Logback) ───────────────────────────────────────────
    implementation("org.slf4j:slf4j-api:${Versions.Slf4j}")
    runtimeOnly("ch.qos.logback:logback-classic:${Versions.Logback}")
    // JSON-encoder для Loki (logstash-encoder)
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.4")

    // ── Observability: Micrometer + Prometheus + OpenTelemetry ──────────────
    implementation("io.micrometer:micrometer-core:${Versions.Micrometer}")
    implementation("io.prometheus:simpleclient:${Versions.Prometheus}")
    implementation("io.prometheus:simpleclient_httpserver:${Versions.Prometheus}")
    implementation("io.prometheus:simpleclient_hotspot:${Versions.Prometheus}")
    implementation("io.opentelemetry:opentelemetry-api:${Versions.OtelVersion}")
    implementation("io.opentelemetry:opentelemetry-sdk:${Versions.OtelVersion}")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:${Versions.OtelVersion}")

    // ── Tests ───────────────────────────────────────────────────────────────
    testImplementation("org.scalatest:scalatest_${Versions.ScalaBinary}:${Versions.ScalaTest}")
    testImplementation("com.typesafe.akka:akka-actor-testkit-typed_${Versions.ScalaBinary}:${Versions.Akka}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.JUnit}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Versions.JUnit}")
    testImplementation("org.scalatestplus:junit-5-10_${Versions.ScalaBinary}:3.2.18.0")

    // ── Integration tests (Testcontainers: Redis, Postgres, Jena) ───────────
    testImplementation("org.testcontainers:testcontainers:${Versions.Testcontainers}")
    testImplementation("org.testcontainers:postgresql:${Versions.Testcontainers}")
    testImplementation("org.testcontainers:junit-jupiter:${Versions.Testcontainers}")
}

// -----------------------------------------------------------------------------
// Scala 3 compiler settings
// -----------------------------------------------------------------------------
scala {
    scalaVersion.set(Versions.Scala)
}

tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.additionalParameters = listOf(
        "-explain",                       // подробные сообщения об ошибках
        "-feature",                       // предупреждения о feature-синтаксисе
        "-unchecked",                     // проверки на уровне типов
        "-deprecation",                   // предупреждения о deprecated API
        "-language:strictEquality",       // проверки на структурное равенство
        "-language:implicitConversions",  // разрешить implicit conversions
        "-language:adhocExtensions",     // разрешить ad-hoc extension methods
        "-Wconf:cat=deprecation:ws",      // суммарно одно предупреждение
        "-Yretain-comments"               // сохранять комментарии в .tasty
    )
}

// -----------------------------------------------------------------------------
// Application plugin — entry point
// -----------------------------------------------------------------------------
application {
    mainClass.set("ru.smev.asg.Main")
    applicationDefaultJvmArgs = listOf(
        "-XX:+UseZGC",                   // ZGC для low-latency (p99 < 1s)
        "-XX:MaxRAMPercentage=75.0",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=/var/log/asg/heapdump.hprof",
        "-XX:+ExitOnOutOfMemoryError",
        "-Dfile.encoding=UTF-8",
        "-Dconfig.file=application.conf"
    )
}

// -----------------------------------------------------------------------------
// ShadowJar — produce a single fat JAR with all dependencies.
// =============================================================================
tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("asg-core")
    archiveClassifier.set("")           // no classifier → asg-core-<version>.jar
    archiveVersion.set("0.1.0")
    mergeServiceFiles()                 // META-INF/services для Jena & Akka
    manifest {
        attributes(
            mapOf(
                "Main-Class"            to "ru.smev.asg.Main",
                "Implementation-Title"   to "Adaptive Semantic Gateway (ASG / АСШ)",
                "Implementation-Version" to "0.1.0",
                "Implementation-Vendor"  to "SMEV.ru",
                "Build-JDK"              to "17"
            )
        )
    }
    // Исключаем лишнее из fat-JAR для уменьшения размера.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("module-info.class")        // JPMS modules — не нужны
}

// Build the fat JAR as part of `assemble`.
tasks.named("assemble") {
    dependsOn("shadowJar")
}

// -----------------------------------------------------------------------------
// Java toolchain: require JDK 17 for Akka 2.8 + Scala 3.
// -----------------------------------------------------------------------------
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// -----------------------------------------------------------------------------
// JaCoCo — code coverage (≥ 80% gate enforced in CI).
// -----------------------------------------------------------------------------
jacoco {
    toolVersion = "0.8.11"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Отчёт JaCoCo генерируется после запуска test.
    finalizedBy("jacocoTestReport")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)   // для SonarQube
        csv.required.set(false)
        html.required.set(true)  // для локального просмотра
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
    }
    // Ограничиваем отчёт нашим кодом (без scala-library).
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "**/scala/**",
                        "**/akka/**",
                        "**/jena/**",
                        "**/lettuce/**",
                        "**/circe/**",
                        "**/doobie/**"
                    )
                }
            }
        )
    )
}

// Покрытие должно быть ≥ 80% — иначе jacocoCoverageVerification фейлит сборку.
tasks.named<JacocoCoverageVerification>("jacocoCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value   = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()   // ветвления — мягче
            }
        }
    }
}

// Проверка покрытия запускается после unit-тестов (вызывается явно в CI Stage 3).
tasks.named("check") {
    dependsOn("jacocoCoverageVerification")
}

// -----------------------------------------------------------------------------
// Checkstyle — Google Java Style + кастомные правила (для Java-классов, если есть).
// -----------------------------------------------------------------------------
checkstyle {
    toolVersion = "10.17.0"
    configFile = file("${rootDir}/config/checkstyle/google_checks.xml")
    maxErrors = 0
    maxWarnings = 0
}

// -----------------------------------------------------------------------------
// SpotBugs — статический анализ байткода.
// -----------------------------------------------------------------------------
spotbugs {
    toolVersion.set("4.8.6")
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") {
    reports {
        create("html") { required.set(true) }   // build/reports/spotbugs/main.html
        create("xml")  { required.set(false) }
    }
}

// -----------------------------------------------------------------------------
// SonarQube — статический анализ + покрытие.
// Запускается из CI Stage 2 (static) с передачей -Dsonar.login=${SONAR_TOKEN}.
// -----------------------------------------------------------------------------
sonarqube {
    properties {
        property("sonar.projectKey", "smev_asg-core")
        property("sonar.projectName", "asg-core")
        property("sonar.sources", "src/main/scala")
        property("sonar.tests", "src/test/scala")
        property("sonar.scala.version", "3.3.3")
        property("sonar.coverage.jacoco.xmlReportPaths",
                 "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.exclusions", "**/BuildConfig.*,**/R.class,**/Generated*")
    }
}

// -----------------------------------------------------------------------------
// Docker plugin (com.palantir.docker) — сборка Docker-образа из Dockerfile.
// Образ строится из готового fat-JAR + Dockerfile в asg-core/Dockerfile.
// -----------------------------------------------------------------------------
docker {
    name = "ghcr.io/smev/asg-core:0.1.0"
    tag("latest", "ghcr.io/smev/asg-core:latest")
    files(tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile.absolutePath)
    setDockerfile(file("Dockerfile"))
    buildArgs(mapOf(
        "ASG_VERSION" to "0.1.0",
        "SCALA_VERSION" to Versions.Scala,
        "JDK_VERSION" to "17"
    ))
    pull(true)
}

// Зависимость: Docker-плагин требует готовый fat-JAR.
tasks.named("docker") {
    dependsOn("shadowJar")
}

tasks.named("dockerPrepare") {
    dependsOn("shadowJar")
}

// -----------------------------------------------------------------------------
// Integration tests — отдельный source-set (`src/it/scala`).
// =============================================================================
sourceSets {
    create("integration") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        scala {
            srcDir("src/it/scala")
        }
        resources {
            srcDir("src/it/resources")
        }
    }
}

val integrationImplementation: Configuration by configurations.getting
val integrationRuntimeOnly: Configuration by configurations.getting

integrationImplementation.extendsFrom(configurations.testImplementation.get())
integrationRuntimeOnly.extendsFrom(configurations.testRuntimeOnly.get())

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests with Testcontainers (Redis, PG, Jena)."
    group = "verification"
    testClassesDirs = sourceSets["integration"].output.classesDirs
    classpath = sourceSets["integration"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter("test")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named("check") {
    dependsOn("integrationTest")
}
