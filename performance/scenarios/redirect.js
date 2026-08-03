/**
 * Hot path: GET /r/{shortCode} → 302 (no follow).
 * Requires seeded shortCodes.
 */
import http from 'k6/http';
import { env } from '../config/env.js';
import { redirectThresholds } from '../config/thresholds.js';
import { expectRedirect } from '../lib/checks.js';
import { constantArrivalRate, summaryHandler } from '../lib/options.js';
import { pickShortCode, sharedSeed } from '../lib/seed.js';

const cfg = env();
const seed = sharedSeed();

export const options = constantArrivalRate({
  thresholds: redirectThresholds(),
});

export const handleSummary = summaryHandler('redirect');

export default function () {
  const code = pickShortCode(seed);
  const res = http.get(`${cfg.baseUrl}/r/${code}`, {
    redirects: 0,
    tags: { name: 'redirect' },
    timeout: '30s',
  });
  expectRedirect(res);
}
