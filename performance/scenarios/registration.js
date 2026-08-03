/**
 * Registration load. Creates real users — use a throwaway DB / raised rate limits.
 * Does not complete email verification (that is seed.sh's job).
 */
import { authThresholds } from '../config/thresholds.js';
import { register } from '../lib/auth.js';
import { constantVus, summaryHandler } from '../lib/options.js';
import { env } from '../config/env.js';

const cfg = env();

export const options = constantVus({
  thresholds: authThresholds(),
});

export const handleSummary = summaryHandler('registration');

export default function () {
  const email = `k6.reg.${Date.now()}.${__VU}.${__ITER}@example.com`;
  register(email, cfg.password);
}
