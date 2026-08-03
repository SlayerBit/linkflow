# LinkFlow — Local Development Setup

Validated on macOS (Apple Silicon) with JDK 21, Maven 3.9+, Docker Desktop, and `docker compose`.

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | **21** (required) | Enforced by Maven Enforcer. Java 26 in `PATH` will break builds if `JAVA_HOME` is wrong. |
| Maven | 3.9+ | Must not have comment lines in `.mvn/jvm.config` (see Troubleshooting). |
| Docker Desktop | Latest | For Redis via Compose. |
| Docker Compose | v2 | Included with Docker Desktop. |

Optional: `openssl` (generate JWT secret), `redis-cli` / `psql` (smoke tests).

## Quick start

```bash
# 1. JDK 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version   # must show 21.x

# 2. Infrastructure (PostgreSQL, Redis, and a mail catcher on the host's ports)
#    The dev overlay is what publishes those ports; the base stack keeps everything
#    behind Nginx, so without it a JAR on the host cannot reach them.
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres redis mailhog

# 3. Build
mvn clean package -DskipTests

# 4. Run backend (port 8081)
java -jar linkflow-app/target/linkflow-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# 5. Optional: API gateway (port 8080) — separate terminal
java -jar linkflow-gateway/target/linkflow-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# 6. Optional: Web UI (port 8082) — separate terminal
java -jar linkflow-web/target/linkflow-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Prefer the full containerised stack instead? `./docker/nginx/generate-dev-certs.sh && docker compose
up --build`, then open **https://localhost**. See [docs/docker.md](docs/docker.md).

## Spring profile

Use **`dev`** for local JAR runs:

- Enables DEBUG logging for `com.linkflow`
- Provides a default `LINKFLOW_JWT_SECRET` (see `application-dev.yml`)
- Enables JPA `show-sql`

Do **not** use `prod` locally: it requires a real SMTP relay, an `https` base URL, an explicit CORS
origin list, and a Redis password, and refuses to start without them. Docker Compose runs the
**`docker`** profile, which applies the same hardening but is honest about being a local demo.

## Environment variables

Running JARs on the host, with infrastructure from the dev overlay:

| Variable | Local JAR (`dev`) | Compose containers |
|----------|-------------------|--------------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/linkflow` | `jdbc:postgresql://postgres:5432/linkflow` |
| `SPRING_DATASOURCE_USERNAME` | `linkflow` | `linkflow` |
| `SPRING_DATASOURCE_PASSWORD` | `linkflow-local-postgres` | `linkflow-local-postgres` |
| `SPRING_DATA_REDIS_HOST` | `localhost` | `redis` |
| `SPRING_DATA_REDIS_PORT` | `6379` | `6379` |
| `SPRING_DATA_REDIS_PASSWORD` | `linkflow-local-redis` | `linkflow-local-redis` |
| `SPRING_MAIL_HOST` / `PORT` | `localhost` / `1025` | `mailhog` / `1025` |

Redis requires a password even locally, because it holds sessions, refresh-token state, and
rate-limit counters — a mistakenly published port should not be an open door to all of it.

Set `SPRING_DATASOURCE_*` to point at a managed database instead of the bundled container.

### Optional / recommended

```bash
# Override dev default JWT secret (recommended)
export LINKFLOW_JWT_SECRET="$(openssl rand -base64 64)"

# Gateway upstream — use 127.0.0.1 on macOS to avoid IPv6 localhost issues
export LINKFLOW_APP_URI=http://127.0.0.1:8081
export LINKFLOW_WEB_URI=http://127.0.0.1:8082

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

**Expected result:** `BUILD SUCCESS` for all 11 modules.

## Run commands

### linkflow-app (backend)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -jar linkflow-app/target/linkflow-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

- Listens on **8081**
- Runs Flyway migrations on startup
- Requires PostgreSQL, Redis, and an SMTP listener (all provided by the dev overlay in step 2)

### linkflow-gateway (optional)

Start **after** `linkflow-app` is healthy:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export LINKFLOW_APP_URI=http://127.0.0.1:8081   # recommended on macOS
export LINKFLOW_WEB_URI=http://127.0.0.1:8082
java -jar linkflow-gateway/target/linkflow-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

- Listens on **8080**
- Proxies `/api/**`, `/r/**`, `/swagger-ui/**`, `/v3/api-docs/**` to the app; `/css/**`, `/js/**`, `/webjars/**`, and `/**` to the web UI
- Gateway `/actuator/**` is handled **locally** (gateway health only) — app metrics/health are on **8081**

### Full stack via Docker

```bash
./docker/nginx/generate-dev-certs.sh   # self-signed cert for local TLS
cp .env.example .env                   # then set LINKFLOW_JWT_SECRET
docker compose up --build
```

Open **https://localhost**. Details: [docs/docker.md](docs/docker.md).

## Expected URLs

Local JAR runs (`dev` profile), where every process binds a host port:

| Service | URL | Notes |
|---------|-----|-------|
| API (direct app) | http://localhost:8081/api/v1/... | Backend without gateway |
| API (via gateway) | http://localhost:8080/api/v1/... | Single entry point |
| Health (app) | http://localhost:8081/actuator/health | Also `/health/liveness`, `/health/readiness` |
| Health (gateway) | http://localhost:8080/actuator/health | |
| Swagger UI (app) | http://localhost:8081/swagger-ui.html | Redirects to `/swagger-ui/index.html` |
| Swagger UI (gateway) | http://localhost:8080/swagger-ui/index.html | `/swagger-ui.html` is not routed by gateway |
| OpenAPI JSON (gateway) | http://localhost:8080/v3/api-docs | Proxied from app |
| Redirects | http://localhost:8080/r/{code} | Via gateway |
| Web UI (via gateway) | http://localhost:8080 | Single entry point |
| MailHog inbox | http://localhost:8025 | Every outbound message |

The full Compose stack publishes **only** `https://localhost` (plus Prometheus, Grafana, and MailHog
for convenience). Swagger is disabled there, and `/actuator` is denied at the edge. Add the dev
overlay to expose the individual services for debugging:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

## Infrastructure ports

| Service | Host port | Published by |
|---------|-----------|--------------|
| PostgreSQL | 5432 | dev overlay |
| Redis | 6379 | dev overlay |
| MailHog SMTP / inbox | 1025 (internal) / 8025 | base stack |
| linkflow-app | 8081 | dev overlay |
| linkflow-gateway | 8080 | dev overlay |
| linkflow-web | 8082 | dev overlay |
| Nginx | 80, 443 | base stack |
| Prometheus | 9090 | base stack |
| Grafana | 3000 | base stack |

### Verify PostgreSQL and Redis

```bash
docker compose ps
redis-cli -h 127.0.0.1 -a linkflow-local-redis ping           # expect PONG
psql "postgresql://linkflow:linkflow-local-postgres@127.0.0.1:5432/linkflow" -c 'select 1'
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

### Port 5432 already in use

**Cause:** A PostgreSQL installed natively on the host is already bound to 5432, so the dev overlay
cannot publish the container's port.

**Fix:** Stop the native server, or remap the container port in `docker-compose.dev.yml` (for
example `"5433:5432"`) and set `SPRING_DATASOURCE_URL` to match.

### Gateway returns 500 / `Connection refused` to app

**Cause:** On macOS, `localhost` may resolve to `::1` while the app listens on IPv4.

**Fix:**

```bash
export LINKFLOW_APP_URI=http://127.0.0.1:8081
```

Default in `linkflow-gateway` `application.yml` uses `127.0.0.1` for this reason.

### `Schema-validation: missing column [created_by] in table [url_analytics]`

**Cause:** Older Flyway migration `V4` did not include audit columns required by `AuditableEntity`.

**Fix:** Migration `V6__add_audit_columns_to_url_analytics.sql` adds the columns. Restart the app (Flyway runs on startup).

### Redis connection errors

Redis is password-protected, and the base stack does not publish its port. Both are easy to trip
over: `NOAUTH Authentication required` means the password is missing, and a connection refused on
6379 usually means the dev overlay was omitted.

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d redis
redis-cli -h 127.0.0.1 -a linkflow-local-redis ping
```

### JWT / startup failure without `dev` profile

`application.yml` requires `LINKFLOW_JWT_SECRET` when not using `dev`. Either use `--spring.profiles.active=dev` or export a secret that decodes to at least 64 bytes: `export LINKFLOW_JWT_SECRET="$(openssl rand -base64 64)"`. Note the requirement is on the decoded length, not the length of the Base64 string.

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
| `linkflow-analytics` | Click tracking, daily trends (7d/30d/90d), recent activity feeds (with IP masking for user privacy) |
| `linkflow-observability` | Actuator, metrics |
| `linkflow-app` | Runnable backend (8081) |
| `linkflow-gateway` | API gateway (8080) |
| `linkflow-web` | Thymeleaf UI (8082) |
