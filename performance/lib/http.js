import http from 'k6/http';
import { check } from 'k6';
import { env } from '../config/env.js';

const cfg = env();

/** Common params: TLS skip + tagged name for metrics. */
export function params(name, extra = {}) {
  return {
    tags: { name },
    timeout: extra.timeout || '30s',
    redirects: extra.redirects === undefined ? 0 : extra.redirects,
    headers: Object.assign(
      {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      extra.headers || {},
    ),
  };
}

export function authHeaders(accessToken) {
  return { Authorization: `Bearer ${accessToken}` };
}

export function get(path, name, extra = {}) {
  return http.get(`${cfg.baseUrl}${path}`, params(name, extra));
}

export function post(path, body, name, extra = {}) {
  return http.post(
    `${cfg.baseUrl}${path}`,
    typeof body === 'string' ? body : JSON.stringify(body),
    params(name, extra),
  );
}

export function parseApi(res) {
  try {
    return res.json();
  } catch (_) {
    return null;
  }
}

export function unwrapData(res) {
  const body = parseApi(res);
  if (!body || body.success !== true) {
    return null;
  }
  return body.data;
}

export function checkOk(res, name, expectedStatus = 200) {
  return check(res, {
    [`${name} status ${expectedStatus}`]: (r) => r.status === expectedStatus,
  });
}

export function checkApiSuccess(res, name) {
  const body = parseApi(res);
  return check(res, {
    [`${name} http 2xx`]: (r) => r.status >= 200 && r.status < 300,
    [`${name} api success`]: () => body && body.success === true,
  });
}
