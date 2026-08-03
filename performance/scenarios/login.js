/**
 * Authenticated login throughput against seeded verified accounts.
 * Uses arrival-rate so a burst of 401/429 responses cannot spin the VUs.
 */
import { authThresholds } from '../config/thresholds.js';
import { env } from '../config/env.js';
import { login } from '../lib/auth.js';
import { baseOptions, summaryHandler } from '../lib/options.js';
import { pickUser, sharedSeed } from '../lib/seed.js';

const cfg = env();
const seed = sharedSeed();
const rate = Number(__ENV.LOGIN_ARRIVAL_RATE || 5);

export const options = baseOptions({
  thresholds: authThresholds(),
  scenarios: {
    default: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: cfg.duration,
      preAllocatedVUs: Math.min(cfg.vus, cfg.maxVus),
      maxVUs: cfg.maxVus,
    },
  },
});

export const handleSummary = summaryHandler('login');

export default function () {
  const user = pickUser(seed);
  login(user.email, user.password);
}
