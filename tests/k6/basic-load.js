// =============================================================================
// basic-load.js — Ступенчатая нагрузка на АСШ
// 100 → 1000 → 5000 → 10000 RPS за 10 минут
//
// Запуск:
//   k6 run -e K6_BASE_URL=http://localhost:8080 -e K6_AUTH_TOKEN=<JWT> basic-load.js
//
// Проверяемые SLO (монография):
//   - p95 latency ≤ 500 мс
//   - p99 latency ≤ 1000 мс
//   - error_rate < 0.5 %
//   - throughput до 10000 RPS (пик)
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

import {
  TRANSLATE_URL,
  AUTH_TOKEN,
  SLO,
  THRESHOLDS,
  buildPayload,
  authHeaders,
  formatSummary,
} from './config.js';

// =============================================================================
// Пользовательские метрики
// =============================================================================
// translation_time_ms — полное время выполнения запроса перевода (мс)
export const translationTime = new Trend('translation_time_ms', true);
// cache_hit_rate — доля ответов с признаком попадания в кэш
export const cacheHitRate = new Rate('cache_hit_rate');
// error_rate — доля неудачных ответов (статус ≠ 200 или нет translated_query)
export const errorRate = new Rate('error_rate');
// total_requests — общий счётчик запросов
export const totalRequests = new Counter('total_requests');

// =============================================================================
// Конфигурация сценария: ступенчатый рост RPS
//   Stage 1: 2 мин на 100 RPS   (минимум SLO)
//   Stage 2: 2 мин на 1000 RPS  (базовый уровень)
//   Stage 3: 3 мин на 5000 RPS  (промежуточный)
//   Stage 4: 3 мин на 10000 RPS (пик SLO)
//   Итого: 10 минут
// =============================================================================
export const options = {
  scenarios: {
    step_load: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 5000,
      maxVUs: 20000,
      stages: [
        { duration: '2m', target: 100 }, // минимум SLO
        { duration: '2m', target: 1000 }, // базовый уровень
        { duration: '3m', target: 5000 }, // промежуточная ступень
        { duration: '3m', target: 10000 }, // пик SLO (10000 RPS)
      ],
      gracefulRampDown: '15s',
      gracefulStop: '15s',
    },
  },
  thresholds: THRESHOLDS,
  // Переиспользуем TCP-соединения — это ближе к продакшен-поведению клиента
  noConnectionReuse: false,
  // Минимальный таймаут на один запрос: 5 с (выше SLO, чтобы фиксировать отказы)
  httpDebug: '',
  userAgent: 'k6-asg-basic-load/1.0',
  tags: { test_type: 'basic_load', component: 'asg' },
};

// =============================================================================
// Основная функция — исполняется каждой VU на каждой итерации
// =============================================================================
export default function () {
  const payload = buildPayload();
  const body = JSON.stringify(payload);
  const params = {
    headers: authHeaders(),
    tags: {
      endpoint: 'translate',
      source_ontology: payload.source_ontology,
      target_ontology: payload.target_ontology,
    },
    timeout: '5s',
  };

  const res = http.post(TRANSLATE_URL, body, params);

  // Фиксируем собственную метрику времени перевода (полный round-trip)
  translationTime.add(res.timings.duration);
  totalRequests.add(1);

  // --- Проверки (k6 checks) ---
  // 1. Статус 200
  // 2. Тело ответа содержит строковое поле translated_query
  // 3. Время ответа < 500 мс (SLO p95)
  let parsedBody = null;
  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'has translated_query field': (r) => {
      try {
        parsedBody = r.json();
        return (
          parsedBody !== null &&
          typeof parsedBody === 'object' &&
          typeof parsedBody.translated_query === 'string' &&
          parsedBody.translated_query.length > 0
        );
      } catch (e) {
        return false;
      }
    },
    'response time < 500ms (SLO p95)': (r) => r.timings.duration < SLO.p95_latency_ms,
    'response time < 1000ms (SLO p99)': (r) => r.timings.duration < SLO.p99_latency_ms,
    'cache hit under 50ms (SLO cache p95)': (r) => {
      // Если ответ пришёл быстрее 50 мс — считаем, что это кэш-попадание на стороне клиента
      return r.timings.duration < SLO.cache_hit_p95_ms;
    },
  });

  // --- Определяем cache_hit по явному признаку в теле ---
  let cached = false;
  if (parsedBody) {
    cached =
      parsedBody.cached === true ||
      parsedBody.cache_hit === true ||
      parsedBody.from_cache === true;
  }
  cacheHitRate.add(cached);

  // --- error_rate ---
  errorRate.add(ok ? 0 : 1);

  // Минимальная пауза 10 мс — эмулирует время подготовки следующего запроса клиентом
  sleep(0.01);
}

// =============================================================================
// Сводка — вывод в stdout и сохранение в JSON-файл
// =============================================================================
export function handleSummary(data) {
  const summaryText = formatSummary(data, 'ASG basic-load.js — Ступенчатая нагрузка 100→10000 RPS');
  return {
    stdout: summaryText,
    'k6-summary-basic-load.json': JSON.stringify(data, null, 2),
    'k6-summary-basic-load.txt': summaryText,
  };
}
