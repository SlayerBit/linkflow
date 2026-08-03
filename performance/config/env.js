/**
 * Environment-driven knobs for every scenario.
 *
 * Values come from the process environment (k6 -e KEY=value or export KEY=...).
 * Defaults target the Compose stack behind Nginx (HTTPS, self-signed cert).
 *
 * Threshold numbers here are regression gates for a run, not published product
 * benchmarks. Do not quote them as measured LinkFlow performance.
 */

export function env() {
  const baseUrl = trimSlash(__ENV.BASE_URL || 'https://localhost');
  return {
    baseUrl,
    mailhogUrl: trimSlash(__ENV.MAILHOG_URL || 'http://localhost:8025'),
    // Skip TLS verify: Compose serves a self-signed cert. Set INSECURE_SKIP_TLS_VERIFY=false
    // only when pointing at a real certificate.
    insecureSkipTLSVerify: (__ENV.INSECURE_SKIP_TLS_VERIFY || 'true').toLowerCase() !== 'false',
    seedFile: __ENV.SEED_FILE || 'performance/data/seed.json',
    password: __ENV.PERF_PASSWORD || 'PerfT3st!Pass',
    // Optional single-account override for smoke tests without a seed file.
    email: __ENV.PERF_EMAIL || '',
    // Scenario sizing — override per run, never treat defaults as capacity claims.
    vus: num('VUS', 10),
    duration: __ENV.DURATION || '1m',
    // Redirect/stress defaults use arrival rate so open-loop flood does not instantly 429.
    arrivalRate: num('ARRIVAL_RATE', 50),
    maxVus: num('MAX_VUS', 50),
    rampRate: num('RAMP_RATE', 200),
    rampVus: num('RAMP_VUS', 50),
    rampUp: __ENV.RAMP_UP || '1m',
    sustain: __ENV.SUSTAIN || '2m',
    rampDown: __ENV.RAMP_DOWN || '1m',
    soakVus: num('SOAK_VUS', 5),
    soakDuration: __ENV.SOAK_DURATION || '30m',
    seedUsers: num('SEED_USERS', 20),
    seedUrlsPerUser: num('SEED_URLS_PER_USER', 3),
  };
}

export function num(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
}

function trimSlash(url) {
  return url.endsWith('/') ? url.slice(0, -1) : url;
}
