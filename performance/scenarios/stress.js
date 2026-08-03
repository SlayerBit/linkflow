/**
 * Ramping VU stress on the redirect hot path (+ light login mix).
 * Raise rate limits (see docker-compose.perf.yml) or 429s will dominate.
 */
import http from 'k6/http';
import { env } from '../config/env.js';
import { redirectThresholds } from '../config/thresholds.js';
import { login } from '../lib/auth.js';
import { expectRedirect } from '../lib/checks.js';
import { rampingArrivalRate, summaryHandler } from '../lib/options.js';
import { pickShortCode, pickUser, sharedSeed } from '../lib/seed.js';

const cfg = env();
const seed = sharedSeed();

export const options = rampingArrivalRate({
  // Stress runs often fail default gates; keep failures visible but slightly looser.
  thresholds: Object.assign({}, redirectThresholds(), {
    http_req_failed: [`rate<${__ENV.THRESHOLD_HTTP_FAIL_RATE || 0.05}`],
    checks: [`rate>${__ENV.THRESHOLD_CHECKS_RATE || 0.95}`],
  }),
});

export const handleSummary = summaryHandler('stress');

export default function () {
  const code = pickShortCode(seed);
  const res = http.get(`${cfg.baseUrl}/r/${code}`, {
    redirects: 0,
    tags: { name: 'redirect' },
  });
  expectRedirect(res);

  // Occasional auth to exercise bcrypt + rate-limit user dimension.
  if (__ITER % 25 === 0) {
    const user = pickUser(seed);
    login(user.email, user.password);
  }
}
