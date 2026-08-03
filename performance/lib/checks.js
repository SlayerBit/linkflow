import { check } from 'k6';
import { parseApi } from './http.js';

export function expectStatus(res, status, label) {
  return check(res, {
    [`${label} status is ${status}`]: (r) => r.status === status,
  });
}

export function expectRedirect(res, label = 'redirect') {
  return check(res, {
    [`${label} is 302`]: (r) => r.status === 302,
    [`${label} has Location`]: (r) => !!r.headers.Location || !!r.headers.location,
  });
}

export function expectApiOk(res, label) {
  const body = parseApi(res);
  return check(res, {
    [`${label} HTTP OK`]: (r) => r.status >= 200 && r.status < 300,
    [`${label} envelope success`]: () => body != null && body.success === true,
    [`${label} has data`]: () => body != null && body.data != null,
  });
}

export function expectRateLimitHeaders(res, label = 'rate-limit') {
  return check(res, {
    [`${label} X-RateLimit-Limit`]: (r) =>
      r.headers['X-RateLimit-Limit'] != null || r.headers['X-Ratelimit-Limit'] != null,
  });
}
