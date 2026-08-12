// =============================================================================
// config.js — Общая конфигурация для k6 нагрузочных тестов АСШ
// (Adaptive Semantic Gateway / Адаптивный Семантический Шлюз)
//
// Содержит: базовые URL, SLO-пороги, JWT-токен, реальные онтологии СМЭВ,
// реалистичные DL-запросы, генератор payload, вспомогательные функции
// и форматтер сводки handleSummary.
// =============================================================================

// --- Базовый URL АСШ --------------------------------------------------------
// Переопределяется переменной окружения K6_BASE_URL при запуске:
//   k6 run -e K6_BASE_URL=https://asg.example.ru basic-load.js
export const BASE_URL = __ENV.K6_BASE_URL || __ENV.BASE_URL || 'http://localhost:8080';

// --- Аутентификация (Bearer JWT) --------------------------------------------
// В продакшене токен должен передаваться через секреты, а не в коде.
export const AUTH_TOKEN =
  __ENV.K6_AUTH_TOKEN ||
  __ENV.AUTH_TOKEN ||
  // Заглушка для локального запуска; замените на реальный JWT
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhc2ctdGVzdC1jbGllbnQiLCJyb2xlIjoidGVzdGVyIiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.sig-placeholder-replace-with-real-jwt';

// --- Endpoint перевода онтологий --------------------------------------------
export const TRANSLATE_PATH = '/api/v1/translate';
export const TRANSLATE_URL = `${BASE_URL}${TRANSLATE_PATH}`;

// =============================================================================
// SLO из монографии «Технология обеспечения интероперабельности»
// =============================================================================
export const SLO = Object.freeze({
  // Латентность
  p95_latency_ms: 500, // p95 общая ≤ 500 мс
  p99_latency_ms: 1000, // p99 ≤ 1000 мс (защитный порог)
  cache_hit_p95_ms: 50, // p95 при cache hit ≤ 50 мс
  // Пропускная способность (RPS)
  throughput_baseline_rps: 1000,
  throughput_peak_rps: 10000,
  throughput_minimum_rps: 100,
  // Доступность
  availability: 0.995, // ≥ 99.5 %
  // Допустимая частота ошибок
  max_error_rate: 0.005, // < 0.5 %
});

// =============================================================================
// k6 thresholds — единые для всех сценариев
// =============================================================================
export const THRESHOLDS = Object.freeze({
  http_req_failed: [
    { threshold: 'rate<0.005', abortOnFail: false, delayAbortEval: '10s' },
  ],
  http_req_duration: [
    { threshold: 'p(95)<500', abortOnFail: false, delayAbortEval: '10s' },
    { threshold: 'p(99)<1000', abortOnFail: false, delayAbortEval: '10s' },
  ],
  translation_time_ms: [{ threshold: 'p(95)<500', abortOnFail: false, delayAbortEval: '10s' }],
  error_rate: [{ threshold: 'rate<0.005', abortOnFail: false, delayAbortEval: '10s' }],
});

// =============================================================================
// Стадии разгона: 0 → 1000 → 10000 → 1000 RPS
// =============================================================================
export const RAMP_STAGES = Object.freeze([
  { duration: '30s', target: 1000 }, // подъём до 1000 RPS (базовый уровень)
  { duration: '2m', target: 1000 }, // удержание 1000 RPS
  { duration: '1m', target: 10000 }, // пик 10000 RPS
  { duration: '2m', target: 10000 }, // удержание пика
  { duration: '1m', target: 1000 }, // спад к базовому уровню
  { duration: '30s', target: 0 }, // завершение
]);

// =============================================================================
// Готовые сценарии для комплексного нагрузочного тестирования
// =============================================================================
export const SCENARIOS = Object.freeze({
  // Базовый ступенчатый профиль
  ramp_load: Object.freeze({
    executor: 'ramping-arrival-rate',
    startRate: 100,
    timeUnit: '1s',
    preAllocatedVUs: 5000,
    maxVUs: 20000,
    stages: RAMP_STAGES,
    gracefulRampDown: '15s',
  }),
});

// =============================================================================
// Реальные идентификаторы онтологий СМЭВ (Russian SMEV ontologies)
// =============================================================================
export const ONTOLOGY_IDS = Object.freeze([
  'smev:registration:v1', // Регистрация физических лиц
  'smev:healthcare:v1', // Здравоохранение (ЕМИСС, ОМС)
  'smev:tax:v1', // Налоговый учёт (ФНС)
  'smev:transport:v1', // Транспорт (ГИБДД, ГИБДД-авто)
  'smev:property:v1', // Имущество (Росреестр)
  'smev:education:v1', // Образование
  'smev:social:v1', // Социальная защита
  'smev:court:v1', // Судопроизводство (ГАС Правосудие)
  'smev:finance:v1', // Финансовые услуги
  'smev:customs:v1', // Таможня (ФТС)
  'smev:migration:v1', // Миграционный учёт (ГУВМ МВД)
  'smev:passport:v1', // Паспортные данные
  'smev:vehicles:v1', // Транспортные средства
  'smev:business:v1', // Юридические лица (ЕГРЮЛ)
  'smev:registry:v1', // Реестры
]);

// =============================================================================
// Реалистичные запросы в дескрипционных логиках (DL)
// Используется синтаксис ALC / ALCHQ
// =============================================================================
export const DL_QUERIES = Object.freeze([
  '∃ hasRegistration.MoscowResident', // существует регистрация у москвича
  '∀ hasIncome.Taxpayer', // весь доход — налогоплательщика
  '∃ registeredAt.∃ locatedIn.Moscow', // цепочка ролей
  'Person ⊓ ∃ hasTaxId.Taxpayer', // пересечение концептов
  '∀ hasService.PublicService', // ограничения на роли
  '∃ hasRegistration.∃ issuedBy.FNS', // вложенная роль
  'Organization ⊓ ∃ providesService.SocialService', // юр. лицо + услуга
  'Taxpayer ⊓ ∃ pays.VAT', // налогоплательщик НДС
  '∃ hasCitizenship.RFCitizen', // гражданство РФ
  '∀ hasStatus.Active', // активный статус
  '∃ owns.RealEstate ⊓ Taxpayer', // владение недвижимостью
  'Patient ⊓ ∃ hasInsurance.ObligatorInsurance', // пациент с ОМС
  '∃ hasEducation.HigherEducation', // высшее образование
  'Vehicle ⊓ ∃ registeredBy.GIBDD', // ТС, зарегистрированное ГИБДД
  '∃ hasIncome ⊓ ∃ paysTax.NDFL', // доход + уплата НДФЛ
  '∀ hasParent.RFCitizen', // родители — граждане РФ
  'Taxpayer ⊓ ∀ hasObligation.Paid', // все обязательства исполнены
  '∃ hasAddress.∃ inCity.Moscow', // адрес в Москве
  '∃ hasPermit.¬Expired', // действующее разрешение (отрицание)
  'Person ⊓ ∀ hasMaritalStatus.Married', // женат/замужем
]);

// =============================================================================
// Вспомогательные функции
// =============================================================================

/** Случайный элемент массива */
export function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/**
 * Генерирует реалистичный payload для POST /api/v1/translate
 * Случайно выбирает source/target онтологии и DL-запрос.
 */
export function buildPayload(opts = {}) {
  const source = opts.source_ontology || randomItem(ONTOLOGY_IDS);
  let target = opts.target_ontology || randomItem(ONTOLOGY_IDS);
  // избегаем тривиального отображения source → source
  let guard = 0;
  while (target === source && guard < 10) {
    target = randomItem(ONTOLOGY_IDS);
    guard++;
  }
  return {
    source_ontology: source,
    target_ontology: target,
    query: opts.query || randomItem(DL_QUERIES),
    options: Object.assign(
      { cache_enabled: true, strict_mode: false, include_proof: false },
      opts.options || {}
    ),
    request_id: `asg-req-${Date.now()}-${Math.floor(Math.random() * 1e9)}`,
  };
}

/**
 * Фиксированный payload для прогрева кэша — одинаковые source/target/query,
 * чтобы сервер гарантированно кэшировал результат и последующие запросы
 * попадали в cache hit.
 */
export function buildWarmupPayload() {
  return {
    source_ontology: 'smev:registration:v1',
    target_ontology: 'smev:tax:v1',
    query: '∃ hasRegistration.MoscowResident',
    options: { cache_enabled: true, warmup: true, strict_mode: false },
    request_id: `asg-warmup-${Date.now()}`,
  };
}

/** HTTP-заголовки с Bearer-токеном */
export function authHeaders(extra = {}) {
  return Object.assign(
    {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${AUTH_TOKEN}`,
      Accept: 'application/json',
      'User-Agent': 'k6-asg-load-test/1.0',
    },
    extra
  );
}

/** Парсит строку длительности ('30s', '2m', '1h') в миллисекунды */
export function parseDurationToMs(d) {
  if (typeof d === 'number') return d;
  if (typeof d !== 'string' || !d.length) return 0;
  const m = d.match(/^(\d+(?:\.\d+)?)\s*(ms|s|m|h|d)?$/);
  if (!m) return 0;
  const val = parseFloat(m[1]);
  const unit = m[2] || 'ms';
  const mult = { ms: 1, s: 1000, m: 60000, h: 3600000, d: 86400000 }[unit] || 1;
  return val * mult;
}

/**
 * Вычисляет ожидаемый уровень RPS в момент времени elapsedMs
 * по списку stages (для ramping-arrival-rate).
 * Между стадиями RPS интерполируется линейно.
 */
export function rpsAtTime(stages, elapsedMs) {
  let prevTarget = 0;
  let cumMs = 0;
  for (const stage of stages) {
    const dur = parseDurationToMs(stage.duration);
    if (cumMs + dur >= elapsedMs) {
      const intoStage = elapsedMs - cumMs;
      const frac = dur > 0 ? intoStage / dur : 1;
      return Math.round(prevTarget + (stage.target - prevTarget) * frac);
    }
    cumMs += dur;
    prevTarget = stage.target;
  }
  // если вышли за пределы — возвращаем последний target
  return stages.length ? stages[stages.length - 1].target : 0;
}

/**
 * Универсальный форматтер сводки k6 для handleSummary.
 * Без зависимостей от внешних URL — полностью self-contained.
 */
export function formatSummary(data, title = 'ASG k6 Test Summary') {
  const sep = '='.repeat(60);
  const lines = [];
  lines.push(sep);
  lines.push(` ${title}`);
  lines.push(sep);

  if (data && data.state) {
    const dur = data.state.testRunDurationMs || 0;
    lines.push(`Test duration    : ${(dur / 1000).toFixed(1)} s`);
    lines.push(`Iterations total : ${fmtMetric(data, 'iterations', 'count')}`);
    lines.push(`VUs at peak      : ${fmtMetric(data, 'vus_max', 'value')}`);
    lines.push('');
  }

  lines.push('--- HTTP ---');
  lines.push(`  Requests total : ${fmtMetric(data, 'http_reqs', 'count')}`);
  lines.push(`  Request rate   : ${fmtMetric(data, 'http_reqs', 'rate')} /s`);
  lines.push(`  Failed requests: ${fmtPct(data, 'http_req_failed')}`);
  if (data.metrics && data.metrics.http_req_duration) {
    const d = data.metrics.http_req_duration.values;
    lines.push(
      `  Duration       : min=${fmtNum(d.min)}ms med=${fmtNum(d.med)}ms ` +
        `p90=${fmtNum(d['p(90)'])}ms p95=${fmtNum(d['p(95)'])}ms p99=${fmtNum(d['p(99)'])}ms max=${fmtNum(
          d.max
        )}ms`
    );
  }
  lines.push('');

  lines.push('--- Custom metrics ---');
  const customNames = [
    'translation_time_ms',
    'cache_hit_rate',
    'error_rate',
    'cache_warmup_count',
    'sustained_errors',
    'current_rps_gauge',
    'breaking_point_rps',
    'max_sustainable_rps',
    'response_time_by_window',
    'memory_leak_indicator_ms',
  ];
  for (const name of customNames) {
    const line = describeMetric(data, name);
    if (line) lines.push(`  ${line}`);
  }

  lines.push('');
  lines.push('--- Checks ---');
  if (data.metrics && data.metrics.checks) {
    const c = data.metrics.checks.values;
    lines.push(`  Passes: ${c.passes}  Fails: ${c.fails}  Rate: ${(c.rate * 100).toFixed(2)}%`);
  }

  lines.push(sep);
  return lines.join('\n') + '\n';
}

function fmtMetric(data, name, field) {
  if (!data.metrics || !data.metrics[name]) return 'N/A';
  const v = data.metrics[name].values;
  if (v && v[field] !== undefined) return String(v[field]);
  return 'N/A';
}

function fmtNum(n) {
  if (n === undefined || n === null || Number.isNaN(n)) return 'N/A';
  return n.toFixed(2);
}

function fmtPct(data, name) {
  if (!data.metrics || !data.metrics[name]) return 'N/A';
  const v = data.metrics[name].values;
  if (v && v.rate !== undefined) return `${(v.rate * 100).toFixed(3)} %`;
  return 'N/A';
}

function describeMetric(data, name) {
  if (!data.metrics || !data.metrics[name]) return null;
  const v = data.metrics[name].values;
  if (v.value !== undefined) return `${name}: value=${fmtNum(v.value)}`;
  if (v.count !== undefined && v.rate !== undefined) {
    return `${name}: count=${v.count} rate=${fmtNum(v.rate)}/s`;
  }
  if (v.rate !== undefined && v.passes !== undefined) {
    return `${name}: passes=${v.passes} fails=${v.fails} rate=${(v.rate * 100).toFixed(3)}%`;
  }
  if (v.med !== undefined) {
    return (
      `${name}: med=${fmtNum(v.med)} p95=${fmtNum(v['p(95)'])} ` +
      `p99=${fmtNum(v['p(99)'])} min=${fmtNum(v.min)} max=${fmtNum(v.max)} count=${v.count}`
    );
  }
  return `${name}: ${JSON.stringify(v)}`;
}
