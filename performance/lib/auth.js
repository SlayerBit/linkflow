import { check } from 'k6';
import { post, unwrapData, parseApi, authHeaders } from './http.js';

export function register(email, password, firstName = 'Perf', lastName = 'User') {
  const res = post(
    '/api/v1/auth/register',
    { email, password, firstName, lastName },
    'auth_register',
  );
  check(res, {
    'register accepted': (r) => r.status === 200 || r.status === 201,
  });
  return { res, data: unwrapData(res), body: parseApi(res) };
}

export function login(email, password) {
  const res = post(
    '/api/v1/auth/login',
    { email, password },
    'auth_login',
  );
  const data = unwrapData(res);
  check(res, {
    'login ok': (r) => r.status === 200,
    'login has accessToken': () => data && !!data.accessToken,
  });
  return { res, data, accessToken: data && data.accessToken };
}

export function createUrl(accessToken, originalUrl, customAlias) {
  const body = { originalUrl };
  if (customAlias) {
    body.customAlias = customAlias;
  }
  const res = post('/api/v1/urls', body, 'url_create', {
    headers: authHeaders(accessToken),
  });
  const data = unwrapData(res);
  check(res, {
    'create url ok': (r) => r.status === 200 || r.status === 201,
    'create url has shortCode': () => data && !!data.shortCode,
  });
  return { res, data };
}
