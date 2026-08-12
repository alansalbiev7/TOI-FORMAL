# syntax=docker/dockerfile:1.7
# ─────────────────────────────────────────────────────────────────────────────
# Dockerfile для ASG Core (Adaptive Semantic Gateway)
# Многоступенчатая сборка: Stage 1 — сборка fat JAR через Gradle 8 / JDK 17;
#                          Stage 2 — runtime на eclipse-temurin:17-jre-jammy.
# Не-root пользователь UID 10001, минимальный образ, healthcheck через curl.
# ─────────────────────────────────────────────────────────────────────────────

ARG GRADLE_IMAGE=gradle:8-jdk17
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy

# ─── Stage 1: build ────────────────────────────────────────────────────────
FROM ${GRADLE_IMAGE} AS builder
LABEL stage=asg-builder

WORKDIR /workspace

# Кэширование зависимостей Gradle: сначала только build-файлы, потом код.
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle ./gradle
# Если используются версии Gradle wrapper — копируем их, иначе пропускаем.
COPY gradlew gradlew.bat ./
RUN chmod +x ./gradlew

# Предзагрузка зависимостей (ускоряет инкрементальные сборки в CI)
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon dependencies --quiet || true

# Копируем исходники и собираем fat JAR
COPY src ./src
COPY config ./config

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon \
        clean \
        shadowJar \
        -x test \
        -PbuildTimestamp=build \
      && ls -la build/libs/

# Переносим собранный артефакт в /workspace/artifacts/
RUN mkdir -p /workspace/artifacts \
    && cp build/libs/*-all.jar /workspace/artifacts/asg-core.jar \
    && cp build/libs/*-all.jar /workspace/artifacts/ || true

# Если shadowJar не настроен, fallback на обычный jar
RUN if [ ! -f /workspace/artifacts/asg-core.jar ]; then \
        cp build/libs/asg-core-*.jar /workspace/artifacts/asg-core.jar 2>/dev/null \
        || cp build/libs/*.jar /workspace/artifacts/asg-core.jar; \
    fi

# ─── Stage 2: runtime ──────────────────────────────────────────────────────
FROM ${RUNTIME_IMAGE} AS runtime

# Метаданные OCI
LABEL org.opencontainers.image.title="asg-core" \
      org.opencontainers.image.description="Adaptive Semantic Gateway — REST/gRPC шлюз трансляции онтологий СМЭВ" \
      org.opencontainers.image.source="https://github.com/smev/asg" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.vendor="SMEV ASG Team"

# Установка curl (для healthcheck), tini (PID 1 / signal forwarding), dumb-init не нужен — tini достаточен,
# и очистка apt-кэша в одном RUN-слое, чтобы не раздувать образ.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl=7.88.1-* \
        tini=0.19.* \
        ca-certificates=20230311ubuntu0.22.04.1 \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/* /tmp/* \
    && groupadd --system --gid 10001 asg \
    && useradd  --system --uid 10001 --gid asg \
                --home-dir /app --shell /usr/sbin/nologin asg \
    && mkdir -p /app /app/config /app/shacl /app/ontologies /app/logs \
    && chown -R 10001:10001 /app

WORKDIR /app

# Копируем fat JAR из stage 1
COPY --from=builder /workspace/artifacts/asg-core.jar /app/asg-core.jar

# Копируем application.conf (HOCON), SHACL-шейпы и онтологии
COPY --chown=10001:10001 config/application.conf /app/config/application.conf
# Шейпы и онтологии обычно монтируются через ConfigMap/PVC, но в образе есть дефолтные
COPY --chown=10001:10001 shapes /app/shacl
COPY --chown=10001:10001 ontologies /app/ontologies

# Порты: 8080 REST, 9090 gRPC, 5005 JDWP (debug — выключен по умолчанию, нужно
# пробросить дополнительно и включить через JAVA_TOOL_OPTIONS).
EXPOSE 8080 9090 5005

# Переключаемся на непривилегированного пользователя
USER 10001:10001

# JVM-флаги: MaxRAMPercentage=75 (контейнерный лимит), G1GC (для низких пауз).
# JDWP по умолчанию отключён (срабатывает только при явном задании ENV JAVA_ENABLE_DEBUG=1).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/" \
    JAVA_ENABLE_DEBUG="false" \
    ASG_HTTP_HOST="0.0.0.0" \
    ASG_HTTP_PORT="8080" \
    ASG_GRPC_PORT="9090" \
    TZ="Europe/Moscow" \
    LC_ALL="C.UTF-8" \
    LANG="C.UTF-8"

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", \
    "if [ \"$JAVA_ENABLE_DEBUG\" = \"true\" ]; then \
        JDWP_OPTS=\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005\"; \
     else \
        JDWP_OPTS=\"\"; \
     fi; \
     exec java $JDWP_OPTS $JAVA_OPTS -jar /app/asg-core.jar" \
    ]

# Дефолтный CMD: не нужен, ENTRYPOINT уже запускает JVM.
CMD []
