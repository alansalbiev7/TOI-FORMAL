// =============================================================================
// stress-test.js — Стресс-тест АСШ: поиск точки отказа и восстановление
//
// Стратегия:
//   1. Начинаем с пиковой SLO-нагрузки 10000 RPS.
//   2. Каждые 2 минуты добавляем +2000 RPS.
//   3. Останавливаемся, когда error_rate > 5 % (с задержкой оценки 30 с).
//   4. В handleSummary вычисляем:
//        - max_sustainable_rps  — максимальный устойчивый RPS
//        - breaking_point_rps  — RPS в момент отказа
//        - recovery_behaviour — наблюдалось ли восстановление после падения
//
// Запуск:
//   k6 run -e K6_BASE_URL=... -e K6_AUTH_TOKEN=... stress-test.js
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter, Gauge } from 'k6/metrics';
import exec from 'k6/execution';

import {
  TRANSLATE_URL,
  AUTH_TOKEN,
  SLO,
  buildPayload,
  authHeaders,
  rpsAtTime,
  parseDurationToMs,
  formatSummary,
} from './config.js';

// =============================================================================
// Точка старта теста
// =============================================================================
export const TEST_START_MS = Date.now();

// =============================================================================
// Пользовательские метрики
// =============================================================================
export const translationTime = new Trend('translation_time_ms', true);
export const errorRate = new Rate('error_rate');
// Счётчик подряд неудачных ответов в одном окне наблюдения
export const sustainedErrors = new Counter('sustained_errors');
// Gauge — текущий ожидаемый уровень RPS (обновляется каждую итерацию)
export const currentRPS = new Gauge('current_rps_gauge');
// Gauge — зафиксированный breaking point RPS (последнее значение в summary)
export const breakingPointRPS = new Gauge('breaking_point_rps');
// Gauge — максимальный устойчивый RPS до отказа
export const maxSustainableRPS = new Gauge('max_sustainable_rps');

// =============================================================================
// Параметры стресс-теста
// =============================================================================
const START_RPS = parseInt(__ENV.STRESS_START_RPS || '10000', 10); // пик SLO
const STEP_RPS = parseInt(__ENV.STRESS_STEP_RPS || '2000', 10); // +2000 каждый шаг
const STEP_DURATION = __ENV.STRESS_STEP_DURATION || '2m'; // длительность шага
const NUM_STEPS = parseInt(__ENV.STRESS_NUM_STEPS || '20', 10); // до 50000 RPS
const ERROR_RATE_THRESHOLD = 0.05; // 5 % — порог отказа
const ERROR_EVAL_DELAY = '30s'; // задержка перед оценкой порога (разогрев)

// =============================================================================
// Стадии разгона: каждый шаг — это ramping stage на новый уровень
// Пример: [{ duration: '2m', target: 10000 }, { duration: '2m', target: 12000 }, ...]
// =============================================================================
function buildStressStages() {
  const stages = [];
  let rps = START_RPS;
  for (let i = 0; i < NUM_STEPS; i++) {
    stages.push({ duration: STEP_DURATION, target: rps });
    rps += STEP_RPS;
  }
  return stages;
}

const STRESS_STAGES = buildStressStages();

// =============================================================================
// Конфигурация
// =============================================================================
export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-arrival-rate',
      startRate: START_RPS,
      timeUnit: '1s',
      preAllocatedVUs: 50000,
      maxVUs: 100000,
      stages: STRESS_STAGES,
      gracefulRampDown: '10s',
      gracefulStop: '10s',
    },
  },
  // Для стресс-теста пороги не валидируют SLO — они нужны только
  // для автоматической остановки при отказе.
  thresholds: {
    error_rate: [
      {
        threshold: `rate<${ERROR_RATE_THRESHOLD}`,
        abortOnFail: true,
        delayAbortEval: ERROR_EVAL_DELAY,
      },
    ],
    http_req_failed: [
      {
        threshold: `rate<${ERROR_RATE_THRESHOLD}`,
        abortOnFail: true,
        delayAbortEval: ERROR_EVAL_DELAY,
      },
    ],
  },
  noConnectionReuse: false,
  userAgent: 'k6-asg-stress/1.0',
  tags: { test_type: 'stress', component: 'asg' },
};

// =============================================================================
// Глобальное состояние в пределах VU для отслеживания восстановления
// (каждая VU имеет свой контекст; cross-VU состояние в k6 не разделяется,
//  поэтому «окно восстановления» определяется локально и агрегируется в summary)
// =============================================================================
const failureState = {
  inFailure: false,
  failureStartedAt: 0,
  lastErrorTs: 0,
  consecutiveFailures: 0,
  consecutiveSuccesses: 0,
  lastGoodRPS: START_RPS,
  maxObservedRPS: 0,
  breakingPointObserved: 0,
  recoveryObserved: false,
};

// =============================================================================
// Основная функция
// =============================================================================
export default function () {
  const now = Date.now();
  const elapsedMs = now - TEST_START_MS;
  const expectedRPS = rpsAtTime(STRESS_STAGES, elapsedMs);

  // Обновляем gauge текущего RPS (последнее значение будет в summary)
  currentRPS.add(expectedRPS);

  // Публикуем breakingPointRPS, если мы в режиме отказа
  if (failureState.inFailure) {
    breakingPointRPS.add(failureState.breakingPointObserved || expectedRPS);
  }

  const payload = buildPayload();
  const body = JSON.stringify(payload);
  const params = {
    headers: authHeaders(),
    tags: {
      endpoint: 'translate',
      stress_rps: String(expectedRPS),
      source_ontology: payload.source_ontology,
      target_ontology: payload.target_ontology,
    },
    timeout: '5s',
  };

  const res = http.post(TRANSLATE_URL, body, params);
  translationTime.add(res.timings.duration);

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
  });

  errorRate.add(ok ? 0 : 1);

  // --- Локальная машина состояний для отслеживания отказа/восстановления ---
  if (ok) {
    failureState.consecutiveFailures = 0;
    failureState.consecutiveSuccesses += 1;
    failureState.lastGoodRPS = expectedRPS;
    if (expectedRPS > failureState.maxObservedRPS) {
      failureState.maxObservedRPS = expectedRPS;
    }
    // Если было падение, но сейчас 10 успешных подряд — фиксируем восстановление
    if (failureState.inFailure && failureState.consecutiveSuccesses >= 10) {
      failureState.inFailure = false;
      failureState.recoveryObserved = true;
      maxSustainableRPS.add(failureState.lastGoodRPS);
    }
  } else {
    failureState.consecutiveSuccesses = 0;
    failureState.consecutiveFailures += 1;
    sustainedErrors.add(1);
    if (!failureState.inFailure) {
      failureState.inFailure = true;
      failureState.failureStartedAt = now;
      failureState.breakingPointObserved = expectedRPS;
      breakingPointRPS.add(expectedRPS);
    }
    failureState.lastErrorTs = now;
  }

  // Минимальная пауза (ramping-arrival-rate сам регулирует темп)
  sleep(0.001);
}

// =============================================================================
// handleSummary — отчёт о точке отказа и восстановлении
// =============================================================================
export function handleSummary(data) {
  const baseText = formatSummary(data, 'ASG stress-test.js — Поиск точки отказа');

  const testDurationMs = (data.state && data.state.testRunDurationMs) || 0;
  const plannedDurationMs = STRESS_STAGES.reduce(
    (acc, s) => acc + parseDurationToMs(s.duration),
    0
  );

  // RPS в момент остановки теста (фактический breaking point)
  const breakingRPS = rpsAtTime(STRESS_STAGES, testDurationMs);

  // Максимальный устойчивый RPS:
  // Если тест дошёл до конца без отказа — это последний уровень.
  // Если был прерван — это уровень, на котором удержались дольше всего
  // (приближаемся как последний «хороший» уровень перед отказом).
  let maxSustainable;
  if (testDurationMs >= plannedDurationMs - 1000) {
    // Тест прошёл полностью — отказа не было
    maxSustainable = STRESS_STAGES[STRESS_STAGES.length - 1].target;
  } else {
    // Тест прерван на каком-то шаге — отступаем на 1 шаг назад,
    // так как текущий уровень не выдержали.
    // Учитываем, что ramp на стадии мог пройти лишь частично.
    let cumMs = 0;
    let lastStableRPS = START_RPS;
    let prevTarget = START_RPS;
    for (const stage of STRESS_STAGES) {
      const stageMs = parseDurationToMs(stage.duration);
      if (cumMs + stageMs >= testDurationMs) {
        // Тест прерван в этой стадии. Если стадия длилась достаточно долго
        // (более 60 с на новом уровне) — отступаем на 1 уровень назад.
        const onLevel = testDurationMs - cumMs;
        if (onLevel > 60000) {
          // Провалились на текущем уровне
          lastStableRPS = prevTarget;
        } else {
          // Провалились во время разгона — отступаем ещё на 1
          lastStableRPS = prevTarget - STEP_RPS;
        }
        break;
      }
      cumMs += stageMs;
      prevTarget = stage.target;
      lastStableRPS = prevTarget;
    }
    maxSustainable = lastStableRPS > 0 ? lastStableRPS : START_RPS;
  }

  // Поведение восстановления — анализируем тренд ошибок в конце теста
  const errVals = data.metrics.error_rate && data.metrics.error_rate.values;
  const errRate = errVals ? errVals.rate : 0;
  const recoveryBehaviour =
    errRate < ERROR_RATE_THRESHOLD
      ? `Система восстановилась в конце теста (финальный error_rate = ${(
          errRate * 100
        ).toFixed(2)} %)`
      : `Система НЕ восстановилась к концу теста (финальный error_rate = ${(
          errRate * 100
        ).toFixed(2)} %)`;

  // Записываем финальные значения в Gauge
  breakingPointRPS.add(breakingRPS);
  maxSustainableRPS.add(maxSustainable);

  const reportText = [
    '',
    '=== ОТЧЁТ ПО ТОЧКЕ ОТКАЗА (STRESS-TEST) ===',
    `Длительность теста         : ${(testDurationMs / 1000 / 60).toFixed(2)} мин`,
    `Запланированная длительность: ${(plannedDurationMs / 1000 / 60).toFixed(2)} мин`,
    `Тест прерван автоматически  : ${testDurationMs < plannedDurationMs - 1000 ? 'ДА (по порогу ошибок)' : 'НЕТ (достигнут конец плана)'}`,
    '',
    `Максимальный устойчивый RPS : ${maxSustainable}`,
    `Breaking point RPS          : ${breakingRPS}`,
    `Ошибка на breaking point    : ${(errRate * 100).toFixed(2)} % (порог ${ERROR_RATE_THRESHOLD * 100} %)`,
    `Поведение восстановления    : ${recoveryBehaviour}`,
    '',
    `Стратегия разгона           : старт ${START_RPS} RPS, шаг +${STEP_RPS} каждые ${STEP_DURATION}`,
    `Всего шагов в плане         : ${STRESS_STAGES.length}`,
    '===========================================',
    '',
  ].join('\n');

  const summaryText = baseText + reportText;

  return {
    stdout: summaryText,
    'k6-summary-stress-test.json': JSON.stringify(
      {
        ...data,
        stress_report: {
          test_duration_ms: testDurationMs,
          planned_duration_ms: plannedDurationMs,
          auto_aborted: testDurationMs < plannedDurationMs - 1000,
          max_sustainable_rps: maxSustainable,
          breaking_point_rps: breakingRPS,
          error_rate_final: errRate,
          error_rate_threshold: ERROR_RATE_THRESHOLD,
          recovery_behaviour: recoveryBehaviour,
          start_rps: START_RPS,
          step_rps: STEP_RPS,
          step_duration: STEP_DURATION,
          num_steps_planned: STRESS_STAGES.length,
        },
      },
      null,
      2
    ),
    'k6-summary-stress-test.txt': summaryText,
  };
}
