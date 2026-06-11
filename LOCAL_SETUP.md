# LinkFlow — Local Development Setup

Validated on macOS (Apple Silicon) with JDK 21, Maven 3.9+, Docker Desktop, and `docker compose`.

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | **21** (required) | Enforced by Maven Enforcer. Java 26 in `PATH` will break builds if `JAVA_HOME` is wrong. |
| Maven | 3.9+ | Must not have comment lines in `.mvn/jvm.config` (see Troubleshooting). |
| Docker Desktop | Latest | For PostgreSQL and Redis via Compose. |
| Docker Compose | v2 | Included with Docker Desktop. |

Optional: `openssl` (generate JWT secret), `redis-cli` / `psql` (smoke tests).

## Quick start

```bash
# 1. JDK 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version   # must show 21.x

# 2. Infrastructure
docker compose up -d postgres redis

# 3. Resolve PostgreSQL port conflict (see Troubleshooting if needed)
#    Ensure connections to localhost:5432 reach Docker Postgres with user `linkflow`.

# 4. Build
mvn clean package -DskipTests

# 5. Run backend (port 8081)
java -jar linkflow-app/target/linkflow-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# 6. Optional: API gateway (port 8080) — separate terminal
java -jar linkflow-gateway/target/linkflow-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# 7. Optional: Web UI (port 8082) — separate terminal
java -jar linkflow-web/target/linkflow-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

## Spring profile

Use **`dev`** for local JAR runs:

- Enables DEBUG logging for `com.linkflow`
- Provides a default `LINKFLOW_JWT_SECRET` (see `application-dev.yml`)
- Enables JPA `show-sql`

Do **not** use `prod` locally unless you set all production secrets (JWT, etc.). Docker Compose services use `prod` internally.

## Environment variables

Defaults in `application.yml` match `docker-compose.yml` for Postgres and Redis when running JARs on the host:

| Variable | Default (local JAR) | `docker-compose.yml` (containers) |
|----------|---------------------|-----------------------------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/linkflow` | `jdbc:postgresql://postgres:5432/linkflow` |
| `SPRING_DATASOURCE_USERNAME` | `linkflow` | `linkflow` |
| `SPRING_DATASOURCE_PASSWORD` | `linkflow` | `linkflow` |
| `SPRING_DATA_REDIS_HOST` | `localhost` | `redis` (in app container) |
| `SPRING_DATA_REDIS_PORT` | `6379` | `6379` |

### Optional / recommended

```bash
# Override dev default JWT secret (recommended)
export LINKFLOW_JWT_SECRET="$(openssl rand -base64 64)"

# Gateway upstream — use 127.0.0.1 on macOS to avoid IPv6 localhost issues
export LINKFLOW_APP_URI=http://127.0.0.1:8081

# Short-link base URL (when using gateway)
export LINKFLOW_BASE_URL=http://localhost:8080

# Bootstrap first admin user (idempotent)
export LINKFLOW_BOOTSTRAP_ADMIN_ENABLED=true
export LINKFLOW_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export LINKFLOW_BOOTSTRAP_ADMIN_PASSWORD='StrongP@ss1'
```

Copy `.env.example` to `.env` for Docker full-stack runs (`docker compose up --build`). Set `LINKFLOW_JWT_SECRET` before starting app/gateway containers.

## Build commands

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Full reactor
mvn clean package -DskipTests

# With tests (requires Docker for Testcontainers)
mvn clean verify

# Single module + dependencies
mvn clean package -DskipTests -pl linkflow-app -am
```

**Expected result:** `BUILD SUCCESS` for all 10 modules.

## Run commands

### linkflow-app (backend)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -jar linkflow-app/target/linkflow-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

- Listens on **8081**
- Runs Flyway migrations on startup
- Requires PostgreSQL and Redis

### linkflow-gateway (optional)

Start **after** `linkflow-app` is healthy:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export LINKFLOW_APP_URI=http://127.0.0.1:8081   # recommended on macOS
java -jar linkflow-gateway/target/linkflow-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

- Listens on **8080**
- Proxies `/api/**`, `/r/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` to the app

### Full stack via Docker

```bash
cp .env.example .env
# Edit .env — set LINKFLOW_JWT_SECRET
docker compose up --build
```

## Expected URLs

| Service | URL | Notes |
|---------|-----|-------|
| API (direct app) | http://localhost:8081/api/v1/... | Backend without gateway |
| API (via gateway) | http://localhost:8080/api/v1/... | Recommended public entry |
| Health (app) | http://localhost:8081/actuator/health | |
| Health (gateway) | http://localhost:8080/actuator/health | |
| Swagger UI (app) | http://localhost:8081/swagger-ui.html | Redirects to `/swagger-ui/index.html` |
| Swagger UI (gateway) | http://localhost:8080/swagger-ui/index.html | `/swagger-ui.html` is not routed by gateway |
| OpenAPI JSON (app) | http://localhost:8081/v3/api-docs | |
| OpenAPI JSON (gateway) | http://localhost:8080/v3/api-docs | Proxied from app |
| Redirects | http://localhost:8080/r/{code} | Via gateway |
| Web UI | http://localhost:8082 | Requires gateway + app running |
| Prometheus | http://localhost:9090 | Docker full stack only |
| Grafana | http://localhost:3000 | Docker full stack only (admin/admin) |

## Infrastructure ports

| Service | Host port | Container |
|---------|-----------|-----------|
| PostgreSQL | 5432 | `postgres:16-alpine` |
| Redis | 6379 | `redis:7-alpine` |
| linkflow-app | 8081 | Docker full stack |
| linkflow-gateway | 8080 | Docker full stack |
| linkflow-web | 8082 | Local JAR only (not in Compose) |

### Verify Postgres and Redis

```bash
docker compose ps
PGPASSWORD=linkflow psql -h 127.0.0.1 -U linkflow -d linkflow -c 'SELECT 1'
redis-cli -h 127.0.0.1 ping   # expect PONG
```

## Troubleshooting

### `Could not find or load main class #`

**Cause:** `.mvn/jvm.config` contained `#` comment lines. Maven passes each line as a JVM argument; `#` is interpreted as a main class name.

**Fix:** `.mvn/jvm.config` must be **empty** or contain only valid JVM flags (e.g. `-Xmx2g`). Do not use `#` comments. Document JDK requirements in this file or `README.md` instead.

### `mvn` fails: `RequireJavaVersion` / wrong Java version

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -version   # Java version should be 21.x
```

Always use `$JAVA_HOME/bin/java` for `java -jar` if your default `java` points to Java 26.

### `FATAL: role "linkflow" does not exist`

**Cause:** A **native PostgreSQL** instance on macOS is bound to `localhost:5432` instead of Docker Postgres. Homebrew `postgresql@18` commonly conflicts with `docker compose` port `5432:5432`.

**Check:**

```bash
lsof -iTCP:5432 -sTCP:LISTEN
docker compose ps postgres
PGPASSWORD=linkflow psql -h 127.0.0.1 -U linkflow -d linkflow -c 'SELECT 1'
```

**Fix (pick one):**

1. Stop native Postgres: `brew services stop postgresql@18` (adjust version as needed)
2. Remap Docker Postgres to host port 5433 in `docker-compose.yml` and set:
   `export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/linkflow`
3. Remove stale volume and recreate: `docker compose down -v && docker compose up -d postgres redis`

### Gateway returns 500 / `Connection refused` to app

**Cause:** On macOS, `localhost` may resolve to `::1` while the app listens on IPv4.

**Fix:**

```bash
export LINKFLOW_APP_URI=http://127.0.0.1:8081
```

Default in `linkflow-gateway` `application.yml` uses `127.0.0.1` for this reason.

### `Schema-validation: missing column [created_by] in table [url_analytics]`

**Cause:** Older Flyway migration `V4` did not include audit columns required by `AuditableEntity`.

**Fix:** Migration `V6__add_audit_columns_to_url_analytics.sql` adds the columns. Restart the app (Flyway runs on startup). For a clean DB: `docker compose down -v && docker compose up -d postgres redis`.

### Redis connection errors

Ensure Redis container is healthy and port 6379 is not blocked:

```bash
docker compose up -d redis
redis-cli -h 127.0.0.1 ping
```

### JWT / startup failure without `dev` profile

`application.yml` requires `LINKFLOW_JWT_SECRET` when not using `dev`. Either use `--spring.profiles.active=dev` or export a 64+ character base64 secret.

### Integration tests fail

`mvn verify` uses Testcontainers. Docker Desktop must be running.

## Module overview

| Module | Role |
|--------|------|
| `linkflow-common` | Shared DTOs, audit, Redis config |
| `linkflow-auth` | JWT, refresh tokens |
| `linkflow-user` | User profiles, admin users |
| `linkflow-url` | Short URLs, redirects, QR |
| `linkflow-rate-limit` | Redis rate limiter |
| `linkflow-analytics` | Click tracking |
| `linkflow-observability` | Actuator, metrics |
| `linkflow-app` | Runnable backend (8081) |
| `linkflow-gateway` | API gateway (8080) |
| `linkflow-web` | Thymeleaf UI (8082) |
