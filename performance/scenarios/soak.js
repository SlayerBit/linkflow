/**
 * Long-running soak: steady redirect + authenticated mix at low VU count.
 * Default SOAK_DURATION=30m — override for shorter local runs.
 * Prefer a throwaway database; this scenario writes occasionally.
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { env } from '../config/env.js';
import { createUrl, login } from '../lib/auth.js';
import { expectApiOk, expectRedirect } from '../lib/checks.js';
import { authHeaders, get } from '../lib/http.js';
import { soakVus, summaryHandler } from '../lib/options.js';
import { pickShortCode, pickUrlId, pickUser, sharedSeed } from '../lib/seed.js';

const cfg = env();
const seed = sharedSeed();
let accessToken;
let user;

export const options = soakVus();

export const handleSummary = summaryHandler('soak');

export default function () {
  if (!accessToken) {
    user = pickUser(seed);
    const result = login(user.email, user.password);
    if (!result.accessToken) {
      sleep(1);
      return;
    }
    accessToken = result.accessToken;
  }

  const code = pickShortCode(seed, __ITER, user);
  const redirect = http.get(`${cfg.baseUrl}/r/${code}`, {
    redirects: 0,
    tags: { name: 'redirect' },
  });
  expectRedirect(redirect);

  if (__ITER % 10 === 0) {
    const headers = authHeaders(accessToken);
    expectApiOk(get('/api/v1/urls?page=0&size=10', 'url_list', { headers }), 'list');
    if ((user.urlIds || []).length > 0) {
      const id = pickUrlId(seed, __ITER, user);
      expectApiOk(get(`/api/v1/urls/${id}/analytics`, 'url_analytics', { headers }), 'analytics');
    }
  }

  if (__ITER % 50 === 0) {
    const alias = `soak${Date.now().toString(36)}${__VU}${__ITER}`;
    createUrl(accessToken, `https://example.com/soak/${__VU}/${__ITER}`, alias);
  }

  // Pace soak iterations — unrestricted loops will 429 even with the perf overlay.
  sleep(Number(__ENV.SOAK_SLEEP_SECONDS || 0.2));
}
