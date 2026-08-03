/**
 * Quick health of critical paths after seed.
 * Usage: ./performance/run.sh smoke
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { env } from '../config/env.js';
import { constantVus, summaryHandler } from '../lib/options.js';
import { login } from '../lib/auth.js';
import { expectApiOk, expectRedirect } from '../lib/checks.js';
import { authHeaders, get } from '../lib/http.js';
import { pickShortCode, pickUser, pickUrlId, sharedSeed } from '../lib/seed.js';

const cfg = env();
const seed = sharedSeed();

export const options = constantVus({
  vus: 1,
  duration: __ENV.DURATION || '15s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
  },
});

export const handleSummary = summaryHandler('smoke');

export default function () {
  const user = pickUser(seed);
  const { accessToken } = login(user.email, user.password);
  if (!accessToken) {
    sleep(1);
    return;
  }

  const me = get('/api/v1/users/me', 'users_me', {
    headers: authHeaders(accessToken),
  });
  expectApiOk(me, 'me');

  if ((user.shortCodes || []).length > 0 || (seed.shortCodes || []).length > 0) {
    const code = pickShortCode(seed, __ITER, user);
    const redirect = http.get(`${cfg.baseUrl}/r/${code}`, {
      redirects: 0,
      tags: { name: 'redirect' },
    });
    expectRedirect(redirect);
  }

  if ((user.urlIds || []).length > 0 || (seed.urlIds || []).length > 0) {
    const id = pickUrlId(seed, __ITER, user);
    const analytics = get(`/api/v1/urls/${id}/analytics`, 'url_analytics', {
      headers: authHeaders(accessToken),
    });
    expectApiOk(analytics, 'analytics');
  }
}
