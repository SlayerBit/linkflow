import { SharedArray } from 'k6/data';
import { env } from '../config/env.js';

function loadSeedObject() {
  const cfg = env();
  try {
    const raw = open(cfg.seedFile);
    const parsed = JSON.parse(raw);
    return {
      users: parsed.users || [],
      shortCodes: parsed.shortCodes || [],
      urlIds: parsed.urlIds || [],
    };
  } catch (_) {
    if (cfg.email) {
      return {
        users: [{ email: cfg.email, password: cfg.password, shortCodes: [], urlIds: [] }],
        shortCodes: [],
        urlIds: [],
      };
    }
    return { users: [], shortCodes: [], urlIds: [] };
  }
}

const seedArray = new SharedArray('linkflow-seed', () => [loadSeedObject()]);

export function sharedSeed() {
  return seedArray[0];
}

export function pickUser(seed, vu = __VU) {
  const users = seed.users || [];
  if (users.length === 0) {
    throw new Error(
      'No seeded users. Run: ./performance/scripts/seed.sh  (or set PERF_EMAIL / PERF_PASSWORD)',
    );
  }
  return users[(vu - 1) % users.length];
}

/** Prefer the current user's urls; fall back to global shortCodes (public redirect). */
export function pickShortCode(seed, iter = __ITER, user = null) {
  const mine = (user && user.shortCodes) || [];
  const codes = mine.length > 0 ? mine : seed.shortCodes || [];
  if (codes.length === 0) {
    throw new Error('No seeded shortCodes. Run: ./performance/scripts/seed.sh');
  }
  return codes[iter % codes.length];
}

export function pickUrlId(seed, iter = __ITER, user = null) {
  const mine = (user && user.urlIds) || [];
  const ids = mine.length > 0 ? mine : seed.urlIds || [];
  if (ids.length === 0) {
    throw new Error('No seeded urlIds. Run: ./performance/scripts/seed.sh');
  }
  return ids[iter % ids.length];
}
