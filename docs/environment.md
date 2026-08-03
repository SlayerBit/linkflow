# Environment Variables

> **Canonical context:** [system-design.md](system-design.md) and [README.md](../README.md).

## linkflow-app

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | (required) | Database URL (e.g. Neon DB) |
| `SPRING_DATASOURCE_USERNAME` | (required) | Database user |
| `SPRING_DATASOURCE_PASSWORD` | (required) | Database password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_PROFILES_ACTIVE` | — | Use `dev` locally, `prod` in Compose |
| `LINKFLOW_JWT_SECRET` | (required in prod) | Base64 JWT secret; must decode to ≥64 bytes for HS512 |
| `SPRING_DATA_REDIS_PASSWORD` | (required in prod) | Redis password; Redis holds sessions and rate-limit state |
| `LINKFLOW_TRUSTED_PROXIES` | (empty) | CIDRs allowed to set `X-Forwarded-For`; empty ignores the header |
| `LINKFLOW_JWT_ACCESS_EXPIRATION_MS` | `900000` | Access token TTL (15 min) |
| `LINKFLOW_JWT_REFRESH_EXPIRATION_MS` | `2592000000` | Refresh token TTL (30 days) |
| `LINKFLOW_BASE_URL` | `http://localhost:8080` | Short link prefix in responses |
| `LINKFLOW_CORS_ALLOWED_ORIGINS` | `*` | CORS allowed origins |
| `LINKFLOW_RATE_LIMIT_USER_RPM` | `100` | Requests/minute per authenticated user |
| `LINKFLOW_RATE_LIMIT_IP_RPM` | `200` | Requests/minute per IP |
| `LINKFLOW_RATE_LIMIT_AUTH_FAIL_CLOSED` | `true` | Return 503 on `/api/v1/auth/**` when Redis is down |
| `LINKFLOW_SECURITY_SWAGGER_PUBLIC` | `true` | Allow Swagger without auth (dev default) |
| `LINKFLOW_SECURITY_ACTUATOR_PUBLIC` | `true` | Allow all actuator paths without auth (dev default) |
| `LINKFLOW_SECURITY_METRICS_PUBLIC` | `false` | Allow Prometheus/metrics without auth (dev default) |
| `LINKFLOW_SECURITY_EMAIL_VERIFICATION_REQUIRED` | `true` | Require email verification before login |
| `LINKFLOW_URL_EXPIRED_CLEANUP_CRON` | `0 0 * * * *` | Cron for expired URL + idempotency cleanup |
| `LINKFLOW_AUTH_SINGLE_USE_TOKEN_RETENTION_DAYS` | `7` | How long spent/expired verification, reset, and email-change tokens are kept before deletion |
| `LINKFLOW_AUTH_SINGLE_USE_TOKEN_CLEANUP_CRON` | `0 45 3 * * *` | Cron for the single-use token reaper |
| `LINKFLOW_METRICS_PUBLIC` | `false` | **Prod profile only** — overrides `metrics-public` (Compose sets `true` for demo) |

### Email

Account activation, password reset, and email change all depend on these. Under the `prod` profile
the application refuses to start if they are misconfigured — a user locked out by a missing
verification email has no other way back in, so failing at startup is preferable to failing
silently at 3am.

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_MAIL_HOST` | `localhost` | SMTP host. Required in prod |
| `SPRING_MAIL_PORT` | `1025` | SMTP port (`1025` is MailHog; `587` for most relays) |
| `SPRING_MAIL_USERNAME` | (empty) | SMTP user |
| `SPRING_MAIL_PASSWORD` | (empty) | SMTP password |
| `SPRING_MAIL_SMTP_AUTH` | `false` | Enable for a real relay |
| `SPRING_MAIL_SMTP_STARTTLS` | `false` | Enable for a real relay |
| `LINKFLOW_MAIL_ENABLED` | `true` | `false` logs links instead of sending. Rejected under `prod` |
| `LINKFLOW_MAIL_FROM` | `no-reply@linkflow.local` | Sender address. Must be a domain you control with SPF/DKIM covering your relay, or mail is filtered as spam. A `@linkflow.local` value is rejected under `prod` |
| `LINKFLOW_MAIL_FROM_NAME` | `LinkFlow` | Sender display name |
| `LINKFLOW_MAIL_BASE_URL` | `http://localhost:8080` | Origin used to build links in emails. Must be reachable from a recipient's browser, so an internal container hostname will not do; must be `https` under `prod` |
| `LINKFLOW_MAIL_MAX_ATTEMPTS` | `3` | Delivery attempts before giving up, with exponential backoff |
| `LINKFLOW_MAIL_RETRY_BACKOFF_MS` | `1000` | Initial backoff between attempts |
| `LINKFLOW_MAIL_COOLDOWN_INTERVAL` | `60s` | Minimum gap between emails of the same kind to the same address. `0` disables it (the `dev` profile does) |

`LINKFLOW_MAIL_COOLDOWN_INTERVAL` is throttling by *recipient*, which the IP rate limit cannot do.
Resend-verification and forgot-password each accept an arbitrary address and cause mail to be sent
to it, so the per-IP limit governs how fast a caller may ask, not how much mail a third party
receives. Requests suppressed by the cooldown still return the same response as ones that send, so
the throttle cannot be used to probe which addresses are registered.
| `LINKFLOW_BOOTSTRAP_ADMIN_ENABLED` | `false` | Enable admin bootstrap |
| `LINKFLOW_BOOTSTRAP_ADMIN_EMAIL` | — | Bootstrap admin email |
| `LINKFLOW_BOOTSTRAP_ADMIN_PASSWORD` | — | Bootstrap admin password |

**Dev profile:** `application-dev.yml` provides a default JWT secret — override in shared environments.

**Prod profile:** `JwtSecretValidator` requires a strong `LINKFLOW_JWT_SECRET`. Swagger is disabled; actuator exposure is restricted — see [security-review.md](security-review.md).

## linkflow-gateway

| Variable | Default | Description |
|----------|---------|-------------|
| `LINKFLOW_APP_URI` | `http://127.0.0.1:8081` | Upstream backend URL (use 127.0.0.1 on macOS) |
| `LINKFLOW_WEB_URI` | `http://127.0.0.1:8082` | Upstream web UI URL |

## linkflow-web

| Variable | Default | Description |
|----------|---------|-------------|
| `LINKFLOW_GATEWAY_URL` | `http://127.0.0.1:8080` | Gateway base URL for all backend `RestClient` calls |
| `LINKFLOW_PUBLIC_GATEWAY_URL` | `http://localhost:8080` | Base URL shown to users for generated short links |
| `LINKFLOW_BACKEND_APP_URL` | `http://127.0.0.1:8081` | Backend app URL for actuator probes on the tools page |
| `LINKFLOW_GRAFANA_URL` | `http://localhost:3000` | Admin system page link — must be browser-reachable |
| `LINKFLOW_PUBLIC_PROMETHEUS_URL` | `http://localhost:9090` | Admin system page link — must be browser-reachable |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `false` | Set `true` behind HTTPS (Compose sets `true`; `prod` defaults to `true`) |
| `LINKFLOW_RATE_LIMIT_USER_RPM` | `100` | Displayed on tools page |
| `LINKFLOW_RATE_LIMIT_IP_RPM` | `200` | Displayed on tools page |

## Docker Compose

Copy `.env.example` to `.env`. Minimum for app container: `LINKFLOW_JWT_SECRET`.

Compose sets internal hostnames (`redis`, `linkflow-app`, `linkflow-web`, `linkflow-gateway`) — see [docker-compose.yml](../docker-compose.yml).

**Compose defaults differ from `.env.example`:** bootstrap admin is **enabled** in Compose with demo credentials unless overridden in `.env`.
