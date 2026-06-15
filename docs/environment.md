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
| `LINKFLOW_JWT_SECRET` | (required in prod) | Base64-encoded JWT secret (≥32 decoded bytes) |
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
| `LINKFLOW_METRICS_PUBLIC` | `false` | **Prod profile only** — overrides `metrics-public` (Compose sets `true` for demo) |
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
| `LINKFLOW_GRAFANA_URL` | `http://localhost:3000` | Admin system page link (Docker stack) |
| `LINKFLOW_PROMETHEUS_URL` | `http://localhost:8081/actuator/prometheus` | Admin system page link — app metrics (not gateway) |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `false` | Set `true` behind HTTPS |
| `LINKFLOW_RATE_LIMIT_USER_RPM` | `100` | Displayed on tools page |
| `LINKFLOW_RATE_LIMIT_IP_RPM` | `200` | Displayed on tools page |

## Docker Compose

Copy `.env.example` to `.env`. Minimum for app container: `LINKFLOW_JWT_SECRET`.

Compose sets internal hostnames (`redis`, `linkflow-app`, `linkflow-web`, `linkflow-gateway`) — see [docker-compose.yml](../docker-compose.yml).

**Compose defaults differ from `.env.example`:** bootstrap admin is **enabled** in Compose with demo credentials unless overridden in `.env`.
