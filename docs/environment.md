# Environment Variables

> **Canonical context:** [system-design.md](system-design.md) and [README.md](../README.md).

## linkflow-app

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/linkflow` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `linkflow` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `linkflow` | Database password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_PROFILES_ACTIVE` | — | Use `dev` locally, `prod` in Compose |
| `LINKFLOW_JWT_SECRET` | (required in prod) | Base64-encoded JWT secret |
| `LINKFLOW_JWT_ACCESS_EXPIRATION_MS` | `900000` | Access token TTL (15 min) |
| `LINKFLOW_JWT_REFRESH_EXPIRATION_MS` | `2592000000` | Refresh token TTL (30 days) |
| `LINKFLOW_BASE_URL` | `http://localhost:8080` | Short link prefix in responses |
| `LINKFLOW_CORS_ALLOWED_ORIGINS` | `*` | CORS allowed origins |
| `LINKFLOW_RATE_LIMIT_USER_RPM` | `100` | Requests/minute per authenticated user |
| `LINKFLOW_RATE_LIMIT_IP_RPM` | `200` | Requests/minute per IP |
| `LINKFLOW_BOOTSTRAP_ADMIN_ENABLED` | `false` | Enable admin bootstrap |
| `LINKFLOW_BOOTSTRAP_ADMIN_EMAIL` | — | Bootstrap admin email |
| `LINKFLOW_BOOTSTRAP_ADMIN_PASSWORD` | — | Bootstrap admin password |

**Dev profile:** `application-dev.yml` provides a default JWT secret — override in shared environments.

## linkflow-gateway

| Variable | Default | Description |
|----------|---------|-------------|
| `LINKFLOW_APP_URI` | `http://127.0.0.1:8081` | Upstream app URL (use 127.0.0.1 on macOS) |

## linkflow-web

| Variable | Default | Description |
|----------|---------|-------------|
| `LINKFLOW_GATEWAY_URL` | `http://127.0.0.1:8080` | Gateway base URL for `RestClient` |
| `LINKFLOW_GRAFANA_URL` | `http://localhost:3000` | Admin system page link |
| `LINKFLOW_PROMETHEUS_URL` | `http://localhost:8080/actuator/prometheus` | Admin system page link |
| `LINKFLOW_RATE_LIMIT_USER_RPM` | `100` | Displayed on tools page |
| `LINKFLOW_RATE_LIMIT_IP_RPM` | `200` | Displayed on tools page |

## Docker Compose

Copy `.env.example` to `.env`. Minimum for app container: `LINKFLOW_JWT_SECRET`.

Compose sets internal hostnames (`postgres`, `redis`, `linkflow-app`) — see `docker-compose.yml`.
