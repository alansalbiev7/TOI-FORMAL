// =============================================================================
// soak-test.js — Длительный (24 ч) soak-тест АСШ на 70 % пиковой нагрузки
// (7000 RPS из SLO пика 10000 RPS).
//
// Цели:
//   1. Выявление утечек памяти: тренд времени отклика по часовым окнам.
//      Если p95/p99 монотонно растут со временем — утечка вероятна.
//   2. Стабильность кэша: периодический прогрев каждые 10 минут.
//   3. Сбор трендов: медиана, p95, p99 в каждом часовом окне.
//
// Запуск:
//   k6 run -e K6_BASE_URL=... -e K6_AUTH_TOKEN=... soak-test.js
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import exec from 'k6/execution';

import {
  TRANSLATE_URL,
  AUTH_TOKEN,
  SLO,
  THRESHOLDS,
  buildPayload,
  buildWarmupPayload,
  authHeaders,
  formatSummary,
} from './config.js';

// =============================================================================
// Точка старта — для вычисления часовых окон
// (Date.now() в k6 синхронизируется между VU через k6 runtime)
// =============================================================================
export const TEST_START_MS = Date.now();

// =============================================================================
// Пользовательские метрики
// =============================================================================
export const translationTime = new Trend('translation_time_ms', true);
export const cacheHitRate = new Rate('cache_hit_rate');
export const errorRate = new Rate('error_rate');
// Тренд времени отклика с тегом hour — позволяет видеть утечку памяти
export const responseTimeByWindow = new Trend('response_time_by_window', true);
// Индикатор утечки: то же, что response_time_by_window, но отдельной метрикой
// для удобства alerting
export const memoryLeakIndicator = new Trend('memory_leak_indicator_ms', true);
// Счётчик прогревов кэша
export const cacheWarmupCount = new Counter('cache_warmup_count');

// Параметр теста: длительность soak-теста
const SOAK_DURATION = __ENV.SOAK_DURATION || '24h';
// 70 % пиковой нагрузки: 0.7 × 10000 = 7000 RPS
const SOAK_RPS = parseInt(__ENV.SOAK_RPS || '7000', 10);
// Интервал прогрева кэша
const WARMUP_INTERVAL_SEC = 600; // 10 минут

// =============================================================================
// Конфигурация: два параллельных сценария
//   soak   — постоянные 7000 RPS в течение 24 часов
//   warmup — 1 VU, выполняет прогрев кэша каждые 10 минут
// =============================================================================
export const options = {
  scenarios: {
    soak: {
      executor: 'constant-arrival-rate',
      rate: SOAK_RPS,
      timeUnit: '1s',
      duration: SOAK_DURATION,
      preAllocatedVUs: 10000,
      maxVUs: 25000,
      exec: 'default',
      startTime: '0s',
      gracefulStop: '30s',
    },
    warmup: {
      executor: 'constant-vus',
      vus: 1,
      duration: SOAK_DURATION,
      startTime: '10s', // даём основному потоку разогнаться
      exec: 'warmupCache',
      gracefulStop: '10s',
    },
  },
  thresholds: THRESHOLDS,
  noConnectionReuse: false,
  userAgent: 'k6-asg-soak/1.0',
  tags: { test_type: 'soak', component: 'asg' },
};

// =============================================================================
// Вспомогательная функция — номер часового окна с момента старта теста
// Возвращает строку 'hour_0', 'hour_1', ..., 'hour_23'
// =============================================================================
function timeWindowTag() {
  const elapsedSec = (Date.now() - TEST_START_MS) / 1000;
  const hourIdx = Math.floor(elapsedSec / 3600);
  return `hour_${hourIdx}`;
}

// =============================================================================
// Основная функция — постоянная нагрузка 7000 RPS
// =============================================================================
export default function () {
  const windowTag = timeWindowTag();
  const payload = buildPayload();
  const body = JSON.stringify(payload);
  const params = {
    headers: authHeaders(),
    tags: {
      endpoint: 'translate',
      time_window: windowTag,
      source_ontology: payload.source_ontology,
      target_ontology: payload.target_ontology,
    },
    timeout: '5s',
  };

  const res = http.post(TRANSLATE_URL, body, params);
  const dur = res.timings.duration;

  // Метрики с тегом hour — k6 автоматически разбивает Trend по тегу в выводе
  translationTime.add(dur, { time_window: windowTag });
  responseTimeByWindow.add(dur, { time_window: windowTag });
  memoryLeakIndicator.add(dur, { time_window: windowTag });

  let parsedBody = null;
  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'has translated_query': (r) => {
      try {
        parsedBody = r.json();
        return (
          parsedBody &&
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
  });

  // Признак попадания в кэш
  let cached = false;
  if (parsedBody) {
    cached =
      parsedBody.cached === true ||
      parsedBody.cache_hit === true ||
      parsedBody.from_cache === true;
  }
  cacheHitRate.add(cached);

  errorRate.add(ok ? 0 : 1);

  // Без sleep — constant-arrival-rate сам регулирует темп
}

// =============================================================================
// Функция прогрева кэша — вызывается отдельным сценарием (1 VU)
// Выполняет один «эталонный» запрос и засыпает на 10 минут.
// VU зацикливается → следующий прогрев через WARMUP_INTERVAL_SEC.
// =============================================================================
export function warmupCache() {
  const payload = buildWarmupPayload();
  const params = {
    headers: authHeaders(),
    tags: {
      endpoint: 'translate',
      scenario: 'warmup',
      time_window: timeWindowTag(),
    },
    timeout: '5s',
  };

  // Отправляем серию из 5 одинаковых запросов, чтобы гарантированно
  // прогреть кэш (первый — cache miss, последующие — cache hit).
  for (let i = 0; i < 5; i++) {
    const reqBody = JSON.stringify(
      Object.assign({}, payload, {
        request_id: `asg-warmup-${Date.now()}-${i}`,
      })
    );
    const res = http.post(TRANSLATE_URL, reqBody, params);
    translationTime.add(res.timings.duration, { time_window: timeWindowTag(), scenario: 'warmup' });
    cacheWarmupCount.add(1);
    sleep(0.05); // 50 мс между прогревочными запросами
  }

  // Засыпаем на 10 минут до следующего цикла прогрева
  sleep(WARMUP_INTERVAL_SEC);
}

// =============================================================================
// handleSummary — вывод + детальный отчёт по часовым окнам
// Анализ тренда: если p95 последнего часа > p95 первого часа × 1.2
//                — диагностируем потенциальную утечку памяти.
// =============================================================================
export function handleSummary(data) {
  const baseText = formatSummary(data, 'ASG soak-test.js — 24 ч @ 7000 RPS');

  // Пытаемся извлечь разбивку по тегу time_window из Trend-метрики.
  // k6 хранит per-tag values в data.metrics.<name>.values при поддержке
  // подсчёта по тегам. Формат зависит от версии k6; обрабатываем оба варианта.
  const trendReport = extractTrendReport(data);

  let leakDiagnosis = 'Не удалось вычислить (отсутствуют данные по часовым окнам)';
  if (trendReport.length >= 2) {
    const first = trendReport[0];
    const last = trendReport[trendReport.length - 1];
    if (first.p95 > 0) {
      const ratio = last.p95 / first.p95;
      if (ratio > 1.2) {
        leakDiagnosis =
          `⚠️  ВЫЯВЛЕНА потенциальная утечка памяти: ` +
          `p95 вырос с ${first.p95.toFixed(1)} мс (${first.window}) ` +
          `до ${last.p95.toFixed(1)} мс (${last.window}), ` +
          `коэффициент роста ×${ratio.toFixed(2)}`;
      } else {
        leakDiagnosis =
          `✓ Утечки памяти не обнаружено: ` +
          `p95 в первом окне ${first.p95.toFixed(1)} мс, ` +
          `в последнем — ${last.p95.toFixed(1)} мс (×${ratio.toFixed(2)})`;
      }
    }
  }

  const trendText = [
    '',
    '--- Тренд времени отклика по часовым окнам ---',
    `Окон всего: ${trendReport.length}`,
    '',
    'window      | count   | median  | p95     | p99     ',
    '------------|---------|---------|---------|---------',
  ]
    .concat(
      trendReport.map(
        (w) =>
          `${w.window.padEnd(11)} | ${String(w.count).padStart(7)} | ` +
          `${w.median.toFixed(1).padStart(7)} | ${w.p95.toFixed(1).padStart(7)} | ${w.p99
            .toFixed(1)
            .padStart(7)}`
      )
    )
    .join('\n');

  const diagnosisText = `\n--- Диагностика утечки памяти ---\n${leakDiagnosis}\n`;

  const summaryText = baseText + trendText + diagnosisText;

  return {
    stdout: summaryText,
    'k6-summary-soak-test.json': JSON.stringify(
      { ...data, trend_report: trendReport, leak_diagnosis: leakDiagnosis },
      null,
      2
    ),
    'k6-summary-soak-test.txt': summaryText,
  };
}

/**
 * Извлекает разбивку Trend по тегу time_window из summary data.
 * Возвращает массив вида:
 *   [{ window: 'hour_0', count, median, p95, p99 }, ...]
 */
function extractTrendReport(data) {
  const out = [];
  const m = data.metrics && (data.metrics.response_time_by_window || data.metrics.translation_time_ms);
  if (!m) return out;
  const vals = m.values;
  if (!vals) return out;

  // Вариант 1: per-tag values доступны как объект tag.{tagName}.{tagValue}
  if (vals.tag && vals.tag.time_window) {
    for (const [window, stat] of Object.entries(vals.tag.time_window)) {
      out.push({
        window,
        count: stat.count || 0,
        median: stat.med || 0,
        p95: stat['p(95)'] || 0,
        p99: stat['p(99)'] || 0,
      });
    }
  } else if (vals.time_window) {
    // Вариант 2: прямой ключ
    for (const [window, stat] of Object.entries(vals.time_window)) {
      out.push({
        window,
        count: stat.count || 0,
        median: stat.med || 0,
        p95: stat['p(95)'] || 0,
        p99: stat['p(99)'] || 0,
      });
    }
  } else {
    // Вариант 3: общий агрегат без разбивки — отдаём как единственное окно
    out.push({
      window: 'overall',
      count: vals.count || 0,
      median: vals.med || 0,
      p95: vals['p(95)'] || 0,
      p99: vals['p(99)'] || 0,
    });
  }

  // Сортировка по порядковому номеру окна
  out.sort((a, b) => {
    const ai = parseInt((a.window.match(/\d+/) || [0])[0], 10);
    const bi = parseInt((b.window.match(/\d+/) || [0])[0], 10);
    return ai - bi;
  });
  return out;
}
