/**
 * Mixed authenticated workload: login once per VU, then list + create + analytics + redirect.
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { env } from '../config/env.js';
import { createUrl, login } from '../lib/auth.js';
import { expectApiOk, expectRedirect } from '../lib/checks.js';
import { authHeaders, get } from '../lib/http.js';
import { constantVus, summaryHandler } from '../lib/options.js';
import { pickShortCode, pickUrlId, pickUser, sharedSeed } from '../lib/seed.js';

const cfg = env();
const seed = sharedSeed();
let accessToken;
let user;

export const options = constantVus();

export const handleSummary = summaryHandler('authenticated-mix');

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
  const headers = authHeaders(accessToken);

  expectApiOk(get('/api/v1/urls?page=0&size=20', 'url_list', { headers }), 'list');

  if (__ITER % 5 === 0) {
    const alias = `mix${Date.now().toString(36)}${__VU}${__ITER}`;
    createUrl(accessToken, `https://example.com/mix/${__VU}/${__ITER}`, alias);
  }

  if ((user.urlIds || []).length > 0 || (seed.urlIds || []).length > 0) {
    const id = pickUrlId(seed, __ITER, user);
    expectApiOk(get(`/api/v1/urls/${id}/analytics`, 'url_analytics', { headers }), 'analytics');
  }

  if ((user.shortCodes || []).length > 0 || (seed.shortCodes || []).length > 0) {
    const code = pickShortCode(seed, __ITER, user);
    const redirect = http.get(`${cfg.baseUrl}/r/${code}`, {
      redirects: 0,
      tags: { name: 'redirect' },
    });
    expectRedirect(redirect);
  }
}
