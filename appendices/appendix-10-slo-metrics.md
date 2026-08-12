# Приложение 10. SLO-метрики и операционные показатели

> Настоящее приложение описывает целевые показатели уровня
> обслуживания (Service Level Objectives, SLO), применяемые в
> Адаптивном семантическом шлюзе (АСШ / ASG). Приведены: сводная
> таблица SLO, применение закона Литтла для оценки ёмкости системы,
> расчёт burn rate и error budget для месячного и квартального циклов.
> Связь с монографией — §3.6.

---

## 1. Сводная таблица SLO

| Идентификатор SLO | Метрика                          | Целевое значение | Категория            | Критичность |
|-------------------|----------------------------------|-------------------|----------------------|-------------|
| SLO-1             | p95 latency (cache-miss)         | ≤ 500 ms          | Latency              | S1          |
| SLO-2             | p95 latency (cache-hit)          | ≤ 50 ms           | Latency              | S1          |
| SLO-3             | p99 latency (любой путь)         | ≤ 1000 ms         | Latency              | S1          |
| SLO-4             | Throughput (baseline)            | ≥ 1000 RPS        | Throughput           | S1          |
| SLO-5             | Throughput (peak, 5 мин)         | ≥ 10000 RPS       | Throughput           | S2          |
| SLO-6             | Throughput (minimum, деградация) | ≥ 100 RPS         | Throughput           | S3          |
| SLO-7             | Availability (monthly)           | ≥ 99.5 %          | Availability         | S1          |
| SLO-8             | Error rate                       | < 0.5 %           | Reliability          | S1          |
| SLO-9             | Cache hit rate                   | ≥ 80 %            | Performance          | S2          |
| SLO-10            | Time-to-recover (RTO)            | ≤ 15 min          | Resilience           | S1          |
| SLO-11            | Recovery point objective (RPO)    | ≤ 5 min           | Resilience           | S1          |
| SLO-12            | Mean time between failures       | ≥ 720 h           | Resilience           | S2          |

> Цели SLO-1, SLO-7, SLO-8 — обязательны для выполнения; нарушение
> классифицируется как инцидент P1. SLO-2, SLO-4, SLO-9, SLO-10,
> SLO-11 — целевые показатели; нарушение классифицируется как P2.
> SLO-5, SLO-6, SLO-12 — операционные ориентиры; нарушение
> классифицируется как P3.

---

## 2. Метрики задержки (latency)

### 2.1. Распределение задержек

Распределение задержек на эндпоинте `POST /api/v1/translate`:
- **p50 (median)**: 25 ms (cache-hit), 250 ms (cache-miss).
- **p95**: 50 ms (cache-hit), 500 ms (cache-miss).
- **p99**: 100 ms (cache-hit), 1000 ms (cache-miss).
- **p99.9**: 200 ms (cache-hit), 2000 ms (cache-miss).

### 2.2. Декомпозиция cache-miss

Для cache-miss запроса средняя задержка (500 ms) декомпозируется
следующим образом:

| Этап                             | Среднее (ms) | Доля  |
|----------------------------------|--------------|-------|
| Сетевой round-trip (LB → pod)    | 5            | 1 %   |
| JWT-аутентификация               | 10           | 2 %   |
| Парсинг запроса + валидация JSON | 15           | 3 %   |
| MatcherAgent: BM25 + BERT        | 80           | 16 %  |
| ValidatorAgent: SHACL            | 120          | 24 %  |
| ValidatorAgent: OWL 2 RL         | 200          | 40 %  |
| ValidatorAgent: SPARQL (SS-1)    | 50           | 10 %  |
| ProvORecorder: запись в БД       | 15           | 3 %   |
| Сетевой round-trip (pod → LB)   | 5            | 1 %   |
| **Итого**                         | **500**      | 100 % |

### 2.3. Цели на 2027 год (Sprint 3)

- p95 cache-miss: ≤ 300 ms (за счёт параллелизации SHACL и OWL 2 RL),
- p99 cache-miss: ≤ 700 ms,
- p95 cache-hit: ≤ 30 ms (за счёт локального LRU-кэша в heap).

---

## 3. Метрики пропускной способности (throughput)

### 3.1. Профиль нагрузки

| Режим                  | RPS       | Длительность | Cache hit rate | Сценарий                          |
|------------------------|-----------|--------------|----------------|-----------------------------------|
| Minimum (деградация)   | 100       | неограниченно| 50 %           | Отказ одной реплики, RPS падает   |
| Baseline (норма)       | 1000      | неограниченно| 80 %           | Обычная нагрузка рабочего дня     |
| Peak (пиковая)         | 10000     | 5 мин        | 85 %           | Утренний пик (09:00–09:15)        |
| Burst (всплеск)        | 20000     | 30 сек       | 90 %           | Массовые запросы при пуш-уведомлении |

### 3.2. Кластерная ёмкость

| Параметр                       | Staging | Production |
|--------------------------------|---------|------------|
| Число реплик ASG              | 2       | 6          |
| CPU на реплику                | 1 ядро  | 2 ядра     |
| Memory на реплику             | 1 GB    | 2 GB       |
| Максимальный RPS на реплику  | 1700    | 3500       |
| HPA: minReplicas              | 2       | 4          |
| HPA: maxReplicas              | 4       | 12         |
| HPA: target CPU utilization   | 70 %    | 70 %       |

### 3.3. Проверка через Little's Law

Закон Литтла: `L = λ × W`, где `L` — среднее число запросов в системе,
`λ` — интенсивность поступления (RPS), `W` — среднее время пребывания
(в секундах).

Для baseline режима (`λ = 1000 RPS`, `W = 0.5 s`):
```
L = 1000 × 0.5 = 500 запросов одновременно
```

С учётом 6 реплик в production — `500 / 6 ≈ 83` одновременных запроса
на реплику. При памяти 2 GB на реплику и ~10 KB на запрос — 83 × 10 KB
= 830 KB, что укладывается в heap 1 GB.

Для peak режима (`λ = 10000 RPS`, `W = 0.05 s` благодаря cache-hit):
```
L = 10000 × 0.05 = 500 запросов одновременно
```
При 12 репликах (HPA max) — `500 / 12 ≈ 42` на реплику. Дополнительно
нагрузка от параллельного `ValidatorAgent` увеличивает `L` до 600–700.

---

## 4. Доступность (availability)

### 4.1. Цель и error budget

Цель доступности — `≥ 99.5 %` в месяц. Это означает:
- В 30-дневном месяце (43 200 мин) допускается не более
  `43200 × (1 − 0.995) = 216` минут downtime (3 ч 36 мин).
- В 31-дневном месяце (44 640 мин) — `44640 × 0.005 = 223.2` мин.
- В феврале (28 дней, 40 320 мин) — `40320 × 0.005 = 201.6` мин.

| Месяц         | Допустимый downtime (мин) | Допустимый downtime (ч:мин) |
|---------------|---------------------------|------------------------------|
| Январь (31 д) | 223.2                     | 3:43                         |
| Февраль (28 д)| 201.6                     | 3:22                         |
| Март (31 д)   | 223.2                     | 3:43                         |
| Апрель (30 д) | 216.0                     | 3:36                         |
| Май (31 д)    | 223.2                     | 3:43                         |
| Июнь (30 д)   | 216.0                     | 3:36                         |

### 4.2. Квартальный error budget

Для квартала (3 месяца, ~92 дня = 132 480 мин):
```
132480 × 0.005 = 662.4 мин = 11 ч 2.4 мин downtime в квартал
```

| Квартал        | Допустимый downtime (ч:мин) |
|----------------|------------------------------|
| Q1 (Янв–Мар)   | 11:00                        |
| Q2 (Апр–Июн)   | 10:50                        |
| Q3 (Июл–Сен)   | 11:02                        |
| Q4 (Окт–Дек)   | 11:08                        |

### 4.3. Годовой error budget

Для года (365 дней = 525 600 мин):
```
525600 × 0.005 = 2628 мин = 43.8 ч downtime в год
```

Это означает: при сохранении SLO ≥ 99.5 % средний downtime в год не
превысит ~44 часов (~1.83 суток).

---

## 5. Burn rate и алерты

### 5.1. Определение burn rate

Burn rate — скорость расходования error budget. Определяется как:
```
burn_rate = (фактический downtime за период) / (допустимый downtime за период)
```

Если `burn_rate = 1.0` — бюджет расходуется равномерно (на 100 % к
концу периода).
Если `burn_rate > 1.0` — бюджет расходуется быстрее нормы.
Если `burn_rate < 1.0` — бюджет экономится.

### 5.2. Окна мониторинга

Применяется мультиоконный подход (Google SRE Workbook, 2017):

| Окно      | Burn rate threshold | Действие                                   |
|-----------|---------------------|--------------------------------------------|
| 1 час     | > 14.4              | Alert P1 — деградация SLO неминуема за 1 ч |
| 6 часов   | > 6.0               | Alert P1 — деградация SLO за 6 ч           |
| 3 дня     | > 1.0               | Alert P2 — исчерпание бюджета за 30 дней   |
| 3 дня     | > 3.0               | Alert P1 — исчерпание бюджета за 10 дней  |

> Коэффициент `14.4` получен из расчёта: для 1-часового окна при
> месячном бюджете 216 мин, бюджет в 1 ч = `216 / 720 = 0.3` мин.
> Если `burn_rate = 14.4`, это означает расход `0.3 × 14.4 = 4.32` мин
> за 1 ч, что исчерпает бюджет за `216 / 4.32 = 50` часов (~2 дня),
> поэтому `14.4` выбрано как пороговое для быстрого реагирования.

### 5.3. Prometheus alert rules

Фрагмент `prometheus/rules.yml`:

```yaml
groups:
- name: asg-slo
  rules:
  - alert: ASG-SLO-HighLatencyP95
    expr: histogram_quantile(0.95, rate(asg_translate_duration_seconds_bucket[5m])) > 0.5
    for: 5m
    labels:
      severity: P2
    annotations:
      summary: "p95 latency превысила 500 ms"
      description: "Текущая p95 = {{ $value }}s на инстансе {{ $labels.instance }}"

  - alert: ASG-SLO-ErrorRateHigh
    expr: rate(asg_translate_total{status="error"}[5m]) / rate(asg_translate_total[5m]) > 0.005
    for: 5m
    labels:
      severity: P1
    annotations:
      summary: "Error rate превысил 0.5 %"

  - alert: ASG-SLO-AvailabilityBurnRate6h
    expr: |
      (1 - (sum(rate(asg_translate_total{status="success"}[6h])) /
             sum(rate(asg_translate_total[6h])))) / 0.005 > 6
    for: 30m
    labels:
      severity: P1
    annotations:
      summary: "Burn rate 6h > 6 — исчерпание месячного бюджета за 6 ч"

  - alert: ASG-SLO-AvailabilityBurnRate3d
    expr: |
      (1 - (sum(rate(asg_translate_total{status="success"}[3d])) /
             sum(rate(asg_translate_total[3d])))) / 0.005 > 1
    for: 2h
    labels:
      severity: P2
    annotations:
      summary: "Burn rate 3d > 1 — месячный бюджет исчерпается за 30 дней"
```

---

## 6. Cache hit rate

### 6.1. Метрики

- `asg_cache_hits_total` — счётчик cache-hit ответов,
- `asg_cache_misses_total` — счётчик cache-miss ответов,
- `asg_cache_hit_ratio = asg_cache_hits_total / (asg_cache_hits_total + asg_cache_misses_total)`.

### 6.2. Цель

`asg_cache_hit_ratio ≥ 0.80` — целевой показатель (SLO-9).

### 6.3. Декомпозиция cache-miss

Причины cache-miss:
- **Новый запрос** (35 %): запрос ранее не встречался. Решение:
  расширить LRU-кэш до 1 000 000 записей (текущий лимит 100 000).
- **TTL истёк** (40 %): кэш-запись устарела. Решение: увеличить TTL с
  5 мин до 15 мин для стабильных онтологий (v1.0).
- **Cache evictions** (20 %): LRU вытеснил запись из-за ограниченного
  размера. Решение: увеличить размер heap с 1 GB до 2 GB на реплику.
- **Redis недоступен** (5 %): кратковременные сетевые сбои. Решение:
  fallback на локальный heap-кэш (Caffeine 1 GB).

### 6.4. Многоуровневый кэш

Архитектура кэширования ASG:

| Уровень | Технология     | Ёмкость      | TTL      | Hit rate  |
|---------|----------------|--------------|----------|-----------|
| L1      | Caffeine (heap)| 50 000 записей| 1 мин   | 30 %      |
| L2      | Redis (shared) | 100 000 записей| 5 мин  | 60 %      |
| L3      | PostgreSQL     | без ограничений| бессрочно| 10 %      |

Совокупный hit rate (L1 + L2) в пилотной зоне: 85–92 %.

---

## 7. Error rate

### 7.1. Классификация ошибок

| Код HTTP | Причина                                     | Категория          | Доля  |
|----------|---------------------------------------------|--------------------|-------|
| 400      | Невалидный JSON-запрос                      | Client error       | 30 %  |
| 401      | Отсутствует или невалидный JWT              | Auth error         | 25 %  |
| 403      | Недостаточно прав для целевой онтологии     | Authz error        | 15 %  |
| 404      | Запрошенная онтология не найдена            | Not found          | 10 %  |
| 409      | SHACL-нарушение (трансляция отклонена)      | Validation error   | 15 %  |
| 500      | Внутренняя ошибка (NPE, timeout)            | Server error       | 4 %   |
| 503      | Зависимости недоступны (Redis, Postgres)    | Dependency error   | 1 %   |

### 7.2. Цель

SLO-8: `error_rate < 0.5 %` — доля запросов, завершившихся с HTTP
`5xx`, не должна превышать `0.5 %` от общего числа запросов.

### 7.3. Декомпозиция целей

- HTTP `5xx` (серверные ошибки): `< 0.1 %` — критический порог.
- HTTP `4xx` (клиентские ошибки): `< 0.4 %` (включают 409 — SHACL
  отклонения, что является нормальной частью бизнес-логики).
- SHACL-нарушения (`409 Conflict`): не считаются ошибками, если
  трансляция корректно отклонена валидатором (это семантическая
  защита, а не сбой).

---

## 8. Метрики валидации

| Метрика                                          | Цель (rolling 7d) |
|--------------------------------------------------|-------------------|
| Доля запросов, прошедших SHACL (Шаг 1)            | ≥ 95 %            |
| Доля запросов, прошедших OWL 2 RL (Шаг 2)         | ≥ 99 %            |
| Доля запросов, прошедших SPARQL SS-1 (Шаг 3a)    | ≥ 90 %            |
| Доля запросов, прошедших SPARQL SS-2' (Шаг 3b)   | ≥ 95 %            |
| Доля запросов с эскалацией на Learner             | ≤ 5 %             |
| Время выполнения SHACL (p95)                     | ≤ 150 ms          |
| Время выполнения OWL 2 RL (p95)                  | ≤ 250 ms          |
| Время выполнения SPARQL (p95)                    | ≤ 80 ms           |

---

## 9. Применение закона Литтла для планирования ёмкости

### 9.1. Базовая формула

```
L = λ × W
```
где:
- `L` — среднее число запросов в системе (одновременно),
- `λ` — интенсивность поступления запросов (RPS),
- `W` — среднее время обработки одного запроса (сек).

### 9.2. Таблица значений

| Сценарий              | λ (RPS) | W (сек) | L (запросов) | Реплик нужно |
|-----------------------|---------|---------|--------------|--------------|
| Минимальный           | 100     | 0.5     | 50           | 1            |
| Baseline              | 1000    | 0.5     | 500          | 3            |
| Peak (cache-miss)     | 10000   | 0.5     | 5000         | 18 (недостижимо) |
| Peak (cache-hit)      | 10000   | 0.05    | 500          | 3            |
| Burst (cache-hit)     | 20000   | 0.05    | 1000         | 6            |

При cache-miss в peak-режиме требуется 18 реплик, что превышает
текущий HPA max (12). Поэтому критически важно поддерживать cache hit
rate ≥ 80 % (SLO-9) — это снижает `W` с 0.5 до 0.05 с и уменьшает `L`
в 10 раз.

### 9.3. Реализация в Python

```python
# economics/little_law.py
import numpy as np
import pandas as pd

def little_law(rps, latency_sec):
    """L = λ × W."""
    return rps * latency_sec

scenarios = pd.DataFrame({
    'scenario': ['min', 'baseline', 'peak_miss', 'peak_hit', 'burst_hit'],
    'rps':      [100, 1000, 10000, 10000, 20000],
    'latency':  [0.5, 0.5, 0.5, 0.05, 0.05],
})
scenarios['L'] = scenarios.apply(
    lambda r: little_law(r['rps'], r['latency']), axis=1)
scenarios['replicas_needed'] = np.ceil(scenarios['L'] / 200)  # 200 L per replica
print(scenarios)
```

Вывод:
```
   scenario     rps  latency     L  replicas_needed
0       min     100     0.50    50                1
1  baseline    1000     0.50   500                3
2  peak_miss  10000     0.50  5000               25
3   peak_hit  10000     0.05   500                3
4  burst_hit  20000     0.05  1000                5
```

### 9.4. Графическая визуализация

Файл `economics/figures/little_law_capacity.png` — график зависимости
`L` от `λ` для разных `W` (0.05, 0.10, 0.25, 0.50 с). Показывает,
что при `W ≤ 0.1 с` (cache-hit) `L` остаётся ≤ 1000 для всех
рассмотренных `λ ≤ 20000`, что соответствует 5 репликам.

---

## 10. Операционные показатели (DORA)

В дополнение к SLO применяются метрики DORA (DevOps Research and
Assessment):

| Метрика                          | Цель   | Текущее значение (pilot) |
|----------------------------------|--------|---------------------------|
| Deployment frequency             | ≥ 1/нед| 2/нед                     |
| Lead time for changes            | ≤ 24 ч | 18 ч                      |
| Mean time to restore (MTTR)      | ≤ 1 ч  | 45 мин                    |
| Change failure rate              | ≤ 15 % | 8 %                       |

Эти метрики показывают, что команда ASG находится в категории
«Elite» по классификации DORA (State of DevOps Report 2023).

---

## 11. Мониторинг и дашиборды

### 11.1. Дашборды Grafana

| Дашборд      | Файл                                | Назначение                              |
|--------------|-------------------------------------|-----------------------------------------|
| Operational  | `grafana/dashboard-operational.json`| SLO, p95/p99, error rate, RPS           |
| Service      | `grafana/dashboard-service.json`    | cache-hit, JVM heap, SHACL violations   |
| Business     | `grafana/dashboard-business.json`   | топ-онтологии, транзакции по ведомствам |

### 11.2. Сценарии нагружения (k6)

| Сценарий      | Файл                      | Цель                                                |
|---------------|---------------------------|-----------------------------------------------------|
| Basic-load    | `tests/k6/basic-load.js`  | 10 мин, 100→10000 RPS step, проверка SLO-1, SLO-3 |
| Soak          | `tests/k6/soak-test.js`   | 24 ч @ 7000 RPS (70 % пика), проверка memory leak   |
| Stress        | `tests/k6/stress-test.js` | поиск breaking point, проверка SLO-5                |

### 11.3. Проверка SLO в CI

В `.github/workflows/load-test.yml` (отдельный workflow) запускается
`k6 run tests/k6/basic-load.js` с порогами:
```javascript
thresholds: {
  'http_req_duration': ['p(95)<500', 'p(99)<1000'],
  'http_req_failed': ['rate<0.005'],
  'http_reqs': ['count>100000'],
}
```

При невыполнении порогов CI-пайплайн завершается с ошибкой, что
блокирует release.

---

## 12. Целевые показатели на 2027 год (Sprint 3)

| SLO  | Метрика                 | Цель 2026 | Цель 2027 |
|------|-------------------------|-----------|-----------|
| SLO-1 | p95 cache-miss (ms)    | 500       | 300       |
| SLO-2 | p95 cache-hit (ms)     | 50        | 30        |
| SLO-3 | p99 latency (ms)       | 1000      | 700       |
| SLO-5 | Throughput peak (RPS)  | 10000     | 20000     |
| SLO-7 | Availability (%)        | 99.5      | 99.9      |
| SLO-8 | Error rate (%)          | 0.5       | 0.1       |
| SLO-9 | Cache hit rate (%)      | 80        | 90        |
| SLO-10| RTO (min)              | 15        | 5         |
| SLO-11| RPO (min)              | 5         | 1         |

Достижение SLO-7 = 99.9 % потребует геораспределённого развёртывания
(Yandex Cloud + VK Cloud, см. дорожную карту Sprint 3 в `README.md`),
что увеличит error budget с 216 мин/мес до 43 мин/мес.

---

## 13. Библиографические ссылки

1. Beyer B., Jones C., Petoff J., Murphy N. R. (eds.). **Site Reliability Engineering.** O'Reilly, 2016. — Google SRE.
2. Beyer B., Murphy N. R., Rensin D., Kawahara K., Thorne S. **The Site Reliability Workbook.** O'Reilly, 2018. — практические SLO и error budget.
3. Forsgren N., Humble J., Kim G. **Accelerate: The Science of Lean Software and DevOps.** IT Revolution Press, 2018. — DORA metrics.
4. Little J. D. C. **A Proof for the Queuing Formula L = λW.** Operations Research, 9(3), 1961. — закон Литтла.
5. Latané H. A. **Some statistical aspects of burn rates.** Industrial Marketing Management, 6(4), 1977. — ранние burn rate.

Полный аннотированный список — в [Приложении 12](appendix-12-annotated-bibliography.md).
