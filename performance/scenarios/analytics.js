/**
 * Read analytics for URLs owned by the VU's seeded user.
 */
import { sleep } from 'k6';
import { authThresholds } from '../config/thresholds.js';
import { login } from '../lib/auth.js';
import { expectApiOk } from '../lib/checks.js';
import { authHeaders, get } from '../lib/http.js';
import { constantVus, summaryHandler } from '../lib/options.js';
import { pickUrlId, pickUser, sharedSeed } from '../lib/seed.js';

const seed = sharedSeed();
let accessToken;
let user;

// Includes a bcrypt login once per VU, so use auth-tier duration gates (not product SLOs).
export const options = constantVus({
  thresholds: authThresholds(),
});

export const handleSummary = summaryHandler('analytics');

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
  const id = pickUrlId(seed, __ITER, user);
  const headers = authHeaders(accessToken);

  expectApiOk(get(`/api/v1/urls/${id}/analytics`, 'url_analytics', { headers }), 'summary');
  expectApiOk(
    get(`/api/v1/urls/${id}/analytics/clicks?limit=20`, 'url_clicks', { headers }),
    'clicks',
  );
  expectApiOk(
    get(`/api/v1/urls/${id}/analytics/click-trend?days=30`, 'url_click_trend', { headers }),
    'trend',
  );
  expectApiOk(get('/api/v1/analytics/top?limit=10', 'analytics_top', { headers }), 'top');
}
