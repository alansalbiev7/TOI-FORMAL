# ASG API Reference

> Полная спецификация REST и gRPC API Адаптивного семантического шлюза (АСШ).
> Версия API: `v1`. Базовый URL: `http://<host>:8080/api/v1` (REST),
> `grpc://<host>:9090` (gRPC).

## Содержание

- [Аутентификация](#аутентификация)
- [REST API](#rest-api)
  - [POST /api/v1/translate](#post-apiv1translate)
  - [GET /api/v1/health](#get-apiv1health)
  - [GET /api/v1/ready](#get-apiv1ready)
  - [GET /api/v1/metrics](#get-apiv1metrics)
  - [GET /api/v1/ontology/{id}](#get-apiv1ontologyid)
- [gRPC API: TranslateService](#grpc-api-translateservice)
- [Схемы данных](#схемы-данных)
- [Коды ошибок](#коды-ошибок)
- [Rate limiting](#rate-limiting)
- [Версионирование](#версионирование)

---

## Аутентификация

Все эндпоинты (кроме `/health`, `/ready`, `/metrics`) требуют **Bearer JWT** в
заголовке `Authorization`. Токен подписывается HS256 с использованием секрета
из env `ASG_JWT_SECRET`.

```
Authorization: Bearer <JWT>
```

### Структура JWT (HS256)

Header:
```json
{ "alg": "HS256", "typ": "JWT" }
```

Payload:
```json
{
  "sub": "fns-isms",
  "iss": "smev-idp",
  "iat": 1723353600,
  "exp": 1723357200,
  "scope": ["asg:translate", "asg:ontology:read"],
  "consumer_id": "fns",
  "tier": "standard"
}
```

| Claim        | Тип      | Описание                                       |
|--------------|----------|------------------------------------------------|
| `sub`        | string   | Идентификатор потребителя (ВИС)                |
| `iss`        | string   | Эмитент токена (Keycloak / SAML bridge)        |
| `iat`        | integer  | Issued-at (Unix timestamp, секунды)             |
| `exp`        | integer  | Expiry (Unix timestamp, секунды) — проверяется  |
| `scope`      | string[] | Скоупы: `asg:translate`, `asg:ontology:read`   |
| `consumer_id`| string   | Внутренний идентификатор потребителя СМЭВ       |
| `tier`       | string   | `standard` \| `premium` (влияет на rate limit) |

### Получение токена

```bash
# Через Keycloak (production)
curl -fsS -X POST https://keycloak.smev.ru/realms/smev/protocol/openid-connect/token \
  -d 'grant_type=client_credentials' \
  -d 'client_id=fns-isms' \
  -d 'client_secret=<secret>' | jq -r '.access_token'

# Для локальной разработки — токен из ASG_JWT_SECRET (см. .env)
echo "dev.jwt.token"   # валидный для docker-compose dev-режима
```

### Ротация ключа

Ключ `ASG_JWT_SECRET` ротируется раз в 90 дней. Поддерживается grace-период
24 часа, в течение которого валидны и старый, и новый ключи (через
`ASG_JWT_SECRET_PREVIOUS` env). См. [`docs/security.md`](security.md#key-rotation).

---

## REST API

### POST /api/v1/translate

Основной эндпоинт трансляции запроса между онтологиями.

#### Запрос

```http
POST /api/v1/translate HTTP/1.1
Host: asg.smev.ru
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
Accept: application/json
X-Request-Id: req-uuid-1234    (опционально; если нет — генерируется ASG)

{
  "sourceOntologyId": "smev:registration:v1",
  "targetOntologyId": "smev:tax:v1",
  "query": "∃ hasTaxId.Taxpayer",
  "options": {
    "cacheEnabled": true,
    "verificationLevel": "full",
    "confidenceThreshold": 0.7
  }
}
```

#### Параметры запроса

| Поле                 | Тип      | Обяз. | Описание                                              |
|----------------------|----------|-------|------------------------------------------------------|
| `sourceOntologyId`   | string   | ✅    | Идентификатор исходной онтологии (напр. `smev:registration:v1`) |
| `targetOntologyId`   | string   | ✅    | Идентификатор целевой онтологии                       |
| `query`              | string   | ✅    | DL-формула (ALC/ALCHQ) на языке исходной онтологии    |
| `options.cacheEnabled`| boolean | ❌    | Использовать LRU-кэш (default: `true`)               |
| `options.verificationLevel`| enum | ❌  | `full` \| `shacl_only` \| `none` (default: `full`)   |
| `options.confidenceThreshold`| float | ❌ | Если confidence < порог → 422 (default: `0.7`)       |

#### Успешный ответ (200 OK)

```json
{
  "requestId": "req-01HV7X9K2Y3M1N4P5Q6R7S8T9V",
  "accepted": true,
  "outcome": "valid",
  "confidence": 0.92,
  "tookMs": 123,
  "translatedQuery": "∃ hasInn.Taxpayer",
  "contour": "hot-l",
  "cached": false,
  "shaclReport": {
    "conforms": true,
    "violations": []
  },
  "provenance": {
    "activityId": "act:01HV7X9K...",
    "agent": "asg-core/asg-core-7d6f5e4",
    "usedMappings": ["m:registration.tax.inn.v1"]
  }
}
```

#### Поля ответа

| Поле             | Тип      | Описание                                                          |
|------------------|----------|------------------------------------------------------------------|
| `requestId`      | string   | ULID запроса (используется в Jaeger-трейсах)                      |
| `accepted`       | boolean  | `true` если запрос принят к обработке                             |
| `outcome`        | enum     | `valid` \| `invalid` \| `escalated` \| `error`                   |
| `confidence`     | float    | 0.0–1.0 — уверенность MatcherAgent                               |
| `tookMs`         | long     | Время обработки в миллисекундах                                   |
| `translatedQuery`| string   | Переведённая DL-формула на целевой онтологии                      |
| `contour`        | enum     | `hot-l` \| `hot-l-r` \| `learner` — какой контур отработал         |
| `cached`         | boolean  | `true` если ответ из LRU-кэша                                     |
| `shaclReport`    | object   | SHACL-отчёт (conforms + violations)                              |
| `provenance`     | object   | PROV-O: activityId, agent, usedMappings                          |

#### Ошибки

| HTTP | Код ошибки                  | Когда                                              |
|------|----------------------------|----------------------------------------------------|
| 400  | `invalid_request`          | Невалидный JSON / отсутствуют обязательные поля     |
| 401  | `unauthorized`             | Отсутствует/невалиден Bearer JWT                    |
| 403  | `forbidden`                | JWT валиден, но scope не включает `asg:translate`  |
| 404  | `ontology_not_found`       | `sourceOntologyId` или `targetOntologyId` не существует |
| 422  | `shacl_validation_failed`  | Трансляция нарушает SHACL-ограничения              |
| 422  | `low_confidence`           | `confidence < confidenceThreshold`                 |
| 429  | `rate_limit_exceeded`      | Превышен лимит запросов для `consumer_id`           |
| 500  | `internal_error`           | Внутренняя ошибка ASG (см. логи)                   |
| 503  | `service_unavailable`      | Поднимается LearnerAgent (escalation в human/LLM)  |

#### Пример: запрос с ошибкой 422 (SHACL-нарушение)

```bash
curl -fsS -X POST http://localhost:8080/api/v1/translate \
  -H 'Authorization: Bearer dev.jwt.token' \
  -H 'Content-Type: application/json' \
  -d '{
        "sourceOntologyId":"smev:registration:v1",
        "targetOntologyId":"smev:tax:v1",
        "query":"∃ hasInn.⊤"
      }'
```

```json
{
  "requestId": "req-01HV7YA0...",
  "accepted": false,
  "outcome": "invalid",
  "confidence": 0.45,
  "tookMs": 87,
  "error": {
    "code": "shacl_validation_failed",
    "message": "Translated query violates OM-1 (hierarchy preservation)",
    "violations": [
      {
        "shape": "oi:OM1HierarchyShape",
        "focusNode": "toi:TaxPayerMapping",
        "resultPath": "toi:mapsConcept",
        "value": "reg:Person",
        "message": "Concept hierarchy not preserved: reg:Person ⊑ reg:Adult but m(reg:Person) ⊒ m(reg:Adult)",
        "severity": "Violation"
      }
    ]
  }
}
```

---

### GET /api/v1/health

Liveness-проба. Используется Kubernetes `livenessProbe` (каждые 10 секунд).

```http
GET /api/v1/health HTTP/1.1
Host: asg.smev.ru
```

#### Ответ (200 OK)

```json
{
  "status": "UP",
  "version": "0.1.0",
  "uptime": 123456789
}
```

| Поле      | Тип    | Описание                                       |
|-----------|--------|------------------------------------------------|
| `status` | enum   | `UP` \| `DOWN` (если JVM не отвечает)          |
| `version` | string | Версия asg-core (из `build.gradle.kts`)        |
| `uptime`  | long   | Uptime в секундах (с момента старта JVM)        |

> **Важно:** `/health` НЕ требует аутентификации и НЕ проверяет зависимости
> (Redis/Postgres). Для проверки зависимостей используйте `/ready`.

---

### GET /api/v1/ready

Readiness-проба. Используется Kubernetes `readinessProbe` (каждые 5 секунд).
Проверяет подключение к Redis, PostgreSQL, Jena.

```http
GET /api/v1/ready HTTP/1.1
Host: asg.smev.ru
```

#### Ответ (200 OK, если все компоненты UP)

```json
{
  "ready": true,
  "components": {
    "redis": "UP",
    "postgres": "UP",
    "jena": "UP",
    "ontologyRegistry": "UP"
  }
}
```

#### Ответ (503, если хотя бы один компонент DOWN)

```json
{
  "ready": false,
  "components": {
    "redis": "UP",
    "postgres": "DOWN: Connection refused",
    "jena": "UP",
    "ontologyRegistry": "DOWN: depends on postgres"
  }
}
```

---

### GET /api/v1/metrics

Экспорт метрик в формате Prometheus text exposition.

```http
GET /api/v1/metrics HTTP/1.1
Host: asg.smev.ru
Accept: text/plain; version=0.0.4
```

#### Пример ответа (фрагмент)

```
# HELP asg_translate_requests_total Total /translate requests processed
# TYPE asg_translate_requests_total counter
asg_translate_requests_total{outcome="valid",contour="hot-l"} 12453
asg_translate_requests_total{outcome="valid",contour="hot-l-r"} 5821
asg_translate_requests_total{outcome="escalated",contour="learner"} 312
asg_translate_requests_total{outcome="invalid",contour="hot-l-r"} 87

# HELP asg_translate_duration_seconds Duration of /translate in seconds
# TYPE asg_translate_duration_seconds histogram
asg_translate_duration_seconds_bucket{le="0.05"} 8942
asg_translate_duration_seconds_bucket{le="0.1"} 12453
asg_translate_duration_seconds_bucket{le="0.5"} 18189
asg_translate_duration_seconds_bucket{le="1.0"} 18632
asg_translate_duration_seconds_bucket{le="+Inf"} 18719
asg_translate_duration_seconds_count 18719
asg_translate_duration_seconds_sum 4231.7

# HELP asg_cache_hits_total LRU cache hits
# TYPE asg_cache_hits_total counter
asg_cache_hits_total 8942
# HELP asg_cache_misses_total LRU cache misses
# TYPE asg_cache_misses_total counter
asg_cache_misses_total 9777
# HELP asg_shacl_violations_total SHACL validation violations
# TYPE asg_shacl_violations_total counter
asg_shacl_violations_total{shape="OM1HierarchyShape"} 23
asg_shacl_violations_total{shape="OM2UnionShape"} 5

# HELP asg_hotl_escalations_total Three-tier escalation to Learner
# TYPE asg_hotl_escalations_total counter
asg_hotl_escalations_total 312
```

Полный список метрик — в [`docs/monitoring.md`](monitoring.md#metrics-catalogue).

---

### GET /api/v1/ontology/{id}

Получить метаданные об онтологии (версия, количество концептов, источник).

```http
GET /api/v1/ontology/smev:registration:v1 HTTP/1.1
Host: asg.smev.ru
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### Ответ (200 OK)

```json
{
  "id": "smev:registration:v1",
  "available": true,
  "version": "1.0",
  "source": "ontologies/registration/v1.0.owl",
  "loadedAt": "2026-08-11T10:23:45Z",
  "stats": {
    "classes": 47,
    "objectProperties": 23,
    "dataProperties": 18,
    "individuals": 0
  }
}
```

---

## gRPC API: TranslateService

gRPC-сервис — бинарный протокол для high-throughput потребителей (ВИС).
Определение в Protocol Buffers v3.

### .proto

```protobuf
syntax = "proto3";

package ru.smev.asg.grpc.v1;

option java_multiple_files = true;
option java_package = "ru.smev.asg.grpc.v1";

// Основной сервис трансляции.
service TranslateService {
  // Универсальный метод трансляции (с поддержкой streaming для батчей).
  rpc Translate(TranslateRequest) returns (TranslateResponse);

  // Server-streaming: один запрос → последовательность результатов
  // (например, при выгрузке нескольких DL-формул).
  rpc TranslateStream(TranslateRequest) returns (stream TranslateResponse);

  // Health-проверка (gRPC Health Checking Protocol v1).
  rpc Check(HealthCheckRequest) returns (HealthCheckResponse);
}

message TranslateRequest {
  string source_ontology_id = 1;
  string target_ontology_id = 2;
  string query = 3;
  TranslateOptions options = 4;
  string request_id = 5;   // если пустой — генерируется сервером
}

message TranslateOptions {
  bool cache_enabled = 1;
  enum VerificationLevel {
    VERIFICATION_LEVEL_UNSPECIFIED = 0;
    FULL = 1;
    SHACL_ONLY = 2;
    NONE = 3;
  }
  VerificationLevel verification_level = 2;
  double confidence_threshold = 3;
}

message TranslateResponse {
  string request_id = 1;
  bool accepted = 2;
  enum Outcome {
    OUTCOME_UNSPECIFIED = 0;
    VALID = 1;
    INVALID = 2;
    ESCALATED = 3;
    ERROR = 4;
  }
  Outcome outcome = 3;
  double confidence = 4;
  int64 took_ms = 5;
  string translated_query = 6;
  enum Contour {
    CONTOUR_UNSPECIFIED = 0;
    HOT_L = 1;
    HOT_L_R = 2;
    LEARNER = 3;
  }
  Contour contour = 7;
  bool cached = 8;
  ShaclReport shacl_report = 9;
  Provenance provenance = 10;
  Error error = 11;
}

message ShaclReport {
  bool conforms = 1;
  repeated ShaclViolation violations = 2;
}

message ShaclViolation {
  string shape = 1;
  string focus_node = 2;
  string result_path = 3;
  string value = 4;
  string message = 5;
  enum Severity { INFO = 0; WARNING = 1; VIOLATION = 2; }
  Severity severity = 6;
}

message Provenance {
  string activity_id = 1;
  string agent = 2;
  repeated string used_mappings = 3;
}

message Error {
  string code = 1;
  string message = 2;
  map<string, string> details = 3;
}

message HealthCheckRequest { string service = 1; }
message HealthCheckResponse {
  enum ServingStatus {
    UNKNOWN = 0;
    SERVING = 1;
    NOT_SERVING = 2;
    SERVICE_UNKNOWN = 3;
  }
  ServingStatus status = 1;
}
```

### Пример вызова (Python)

```python
import grpc
from ru.smev.asg.grpc.v1 import translate_service_pb2 as pb
from ru.smev.asg.grpc.v1 import translate_service_pb2_grpc as pb_grpc

channel = grpc.insecure_channel('asg.smev.ru:9090')
stub = pb_grpc.TranslateServiceStub(channel)

metadata = (('authorization', 'Bearer ' + jwt_token),)

request = pb.TranslateRequest(
    source_ontology_id='smev:registration:v1',
    target_ontology_id='smev:tax:v1',
    query='∃ hasTaxId.Taxpayer',
    options=pb.TranslateOptions(
        cache_enabled=True,
        verification_level=pb.TranslateOptions.FULL,
        confidence_threshold=0.7,
    ),
)

response = stub.Translate(request, metadata=metadata)
print(f"outcome={response.outcome} confidence={response.confidence} took={response.took_ms}ms")
```

### gRPC reflection

В dev-режиме включена gRPC reflection (`grpc.reflection.v1alpha.ServerReflection`).
Проверить:

```bash
grpcurl -plaintext localhost:9090 list
# → ru.smev.asg.grpc.v1.TranslateService
# → grpc.health.v1.Health
# → grpc.reflection.v1alpha.ServerReflection
```

---

## Схемы данных

### JSON Schema: TranslateRequest

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["sourceOntologyId", "targetOntologyId", "query"],
  "properties": {
    "sourceOntologyId": {
      "type": "string",
      "pattern": "^smev:[a-z]+:v[0-9]+$",
      "example": "smev:registration:v1"
    },
    "targetOntologyId": {
      "type": "string",
      "pattern": "^smev:[a-z]+:v[0-9]+$",
      "example": "smev:tax:v1"
    },
    "query": {
      "type": "string",
      "minLength": 1,
      "maxLength": 4096,
      "description": "DL-формула (ALC/ALCHQ): ∃ hasTaxId.Taxpayer, ∀ hasIncome.Taxpayer, Person ⊓ ∃ hasPermit.¬Expired, ..."
    },
    "options": {
      "type": "object",
      "properties": {
        "cacheEnabled": { "type": "boolean", "default": true },
        "verificationLevel": {
          "type": "string",
          "enum": ["full", "shacl_only", "none"],
          "default": "full"
        },
        "confidenceThreshold": {
          "type": "number",
          "minimum": 0.0,
          "maximum": 1.0,
          "default": 0.7
        }
      },
      "additionalProperties": false
    }
  },
  "additionalProperties": false
}
```

### Поддерживаемые DL-конструкторы

| Символ | Имя              | Пример                            |
|--------|------------------|-----------------------------------|
| `⊓`   | Intersection     | `Person ⊓ Taxpayer`              |
| `⊔`   | Union            | `Doctor ⊔ Nurse`                 |
| `¬`   | Negation         | `¬Expired`                        |
| `∃`   | Existential      | `∃ hasTaxId.Taxpayer`            |
| `∀`   | Universal        | `∀ hasIncome.Taxpayer`           |
| `⊑`   | Subsumption      | `MoscowResident ⊑ RussianResident`|
| `≡`   | Equivalence      | `Taxpayer ≡ Person ⊓ ∃ hasInn.⊤`  |
| `≤n R.C`| Cardinality ≤ | `≤2 hasParent.Human`             |
| `≥n R.C`| Cardinality ≥ | `≥1 hasDoctor.MedicalRecord`     |
| `={a,b}`| Nominal       | `={ivanov,petrov}`               |

---

## Коды ошибок

Полный каталог ошибок с HTTP-кодом, кодом ошибки и описанием.

| HTTP | `error.code`                | Описание                                              | Retry? |
|------|----------------------------|------------------------------------------------------|--------|
| 400  | `invalid_request`          | Невалидный JSON или отсутствуют обязательные поля     | ❌     |
| 400  | `malformed_dl_query`       | Синтаксическая ошибка в DL-формуле                    | ❌     |
| 401  | `unauthorized`             | Bearer JWT отсутствует или невалиден (HS256/exp check)| ❌     |
| 403  | `forbidden`                | JWT валиден, но scope не содержит `asg:translate`     | ❌     |
| 404  | `ontology_not_found`       | Запрошенная онтология не загружена                    | ❌     |
| 422  | `shacl_validation_failed`  | Трансляция нарушает SHACL-ограничения (OM-1/2/3, SS-2')| ❌     |
| 422  | `low_confidence`           | `confidence < confidenceThreshold` (default 0.7)    | ⚠️     |
| 422  | `owl_inconsistent`         | OWL2RL-reasoner обнаружил inconsistency              | ❌     |
| 422  | `sparql_verify_failed`    | SPARQL-верификатор (ss1/ss2) обнаружил нарушение       | ❌     |
| 429  | `rate_limit_exceeded`      | Превышен rate-limit для consumer (см. ниже)            | ✅ Retry-After |
| 500  | `internal_error`           | Необработанная ошибка ASG — см. логи, Jaeger trace    | ⚠️     |
| 503  | `service_unavailable`      | Subsystem (LearnerAgent / Jena) недоступен            | ✅     |
| 503  | `escalated_to_human`       | Контуру Learner требуется ручная валидация оператором | ❌     |
| 504  | `timeout`                  | Запрос не уложился в 30-секундный таймаут             | ✅     |

### Формат ошибки

```json
{
  "requestId": "req-01HV7X9K2Y3M1N4P5Q6R7S8T9V",
  "error": {
    "code": "shacl_validation_failed",
    "message": "Translated query violates OM-1 (hierarchy preservation)",
    "details": {
      "shape": "oi:OM1HierarchyShape",
      "violationsCount": 1
    }
  },
  "traceId": "5f4dcc3b5aa765d61d8327deb882cf99"
}
```

| Поле         | Тип    | Описание                                              |
|--------------|--------|------------------------------------------------------|
| `requestId`  | string | ULID — корреляция с Jaeger trace                      |
| `error.code` | string | Строковый код ошибки (см. таблицу выше)               |
| `error.message`| string| Человекочитаемое сообщение (RU/EN)                   |
| `error.details`| object| Дополнительный контекст (shape, violationsCount)    |
| `traceId`    | string | Jaeger traceId для поиска трейса в UI                  |

---

## Rate limiting

Каждый `consumer_id` (из JWT) имеет свой лимит. Реализован через Redis
token-bucket (`asg:ratelimit:<consumer_id>`).

| Tier        | Requests/sec | Burst | Запросов в минуту |
|-------------|--------------|-------|-------------------|
| `standard`  | 100          | 200   | 6000              |
| `premium`   | 1000         | 2000  | 60000             |
| `internal`  | 10000        | 20000 | 600000            |

При превышении → `429 Too Many Requests` с заголовком `Retry-After: <seconds>`.

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 12
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1723357212

{
  "requestId": "req-01HV7YA1...",
  "error": {
    "code": "rate_limit_exceeded",
    "message": "Rate limit of 100 rps exceeded for consumer fns-isms",
    "details": {
      "limit": 100,
      "window": "1s",
      "retryAfter": 12
    }
  }
}
```

---

## Версионирование

- API версия: `v1` (URI prefix `/api/v1/`).
- Совместимые изменения (additive) — minor bumps (без новой версии в URL).
- Несовместимые изменения — новая major-версия `/api/v2/` + 6 месяцев
  deprecation-периода, в течение которого обе версии работают параллельно.
- Deprecation объявляется в `Deprecation:` и `Sunset:` HTTP-заголовках.

```http
Deprecation: Sun, 11 Jan 2026 00:00:00 GMT
Sunset:       Sat, 11 Jul 2026 00:00:00 GMT
Link:        </api/v2/translate>; rel="successor-version"
```
