/**
 * Shared k6 thresholds used as CI / local regression gates.
 *
 * These are NOT LinkFlow SLOs and MUST NOT be cited as measured product latency.
 * Tune per environment with THRESHOLD_* env vars when needed.
 */

import { num } from './env.js';

export function defaultThresholds() {
  const httpP95 = num('THRESHOLD_HTTP_P95_MS', 500);
  const httpP99 = num('THRESHOLD_HTTP_P99_MS', 1500);
  const failRate = num('THRESHOLD_HTTP_FAIL_RATE', 0.01);
  const checkRate = num('THRESHOLD_CHECKS_RATE', 0.99);

  return {
    http_req_failed: [`rate<${failRate}`],
    http_req_duration: [`p(95)<${httpP95}`, `p(99)<${httpP99}`],
    checks: [`rate>${checkRate}`],
  };
}

/** Redirect hot-path: tighter default gate (still not a published SLO). */
export function redirectThresholds() {
  const p95 = num('THRESHOLD_REDIRECT_P95_MS', 200);
  const p99 = num('THRESHOLD_REDIRECT_P99_MS', 800);
  const failRate = num('THRESHOLD_HTTP_FAIL_RATE', 0.01);
  const checkRate = num('THRESHOLD_CHECKS_RATE', 0.99);

  return {
    http_req_failed: [`rate<${failRate}`],
    http_req_duration: [`p(95)<${p95}`, `p(99)<${p99}`],
    checks: [`rate>${checkRate}`],
    'http_req_duration{name:redirect}': [`p(95)<${p95}`, `p(99)<${p99}`],
  };
}

/** Auth endpoints are slower (bcrypt); looser gate. */
export function authThresholds() {
  const p95 = num('THRESHOLD_AUTH_P95_MS', 1000);
  const p99 = num('THRESHOLD_AUTH_P99_MS', 2500);
  const failRate = num('THRESHOLD_HTTP_FAIL_RATE', 0.02);
  const checkRate = num('THRESHOLD_CHECKS_RATE', 0.98);

  return {
    http_req_failed: [`rate<${failRate}`],
    http_req_duration: [`p(95)<${p95}`, `p(99)<${p99}`],
    checks: [`rate>${checkRate}`],
  };
}
