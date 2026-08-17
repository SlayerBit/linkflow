# LinkFlow deployment

What this repository actually runs. Architecture: [ARCHITECTURE.md](ARCHITECTURE.md).

## What is supported

| Model | Evidence | Profile |
|-------|----------|---------|
| Full Docker Compose | `docker-compose.yml` | `docker` |
| Host JARs + published Postgres/Redis/MailHog | `docker-compose.dev.yml` overlay | `dev` on the JARs |
| Raised rate limits for k6 | `docker-compose.perf.yml` | still `docker` |
| 2-EC2 hosted stack | `docker-compose.ec2-edge.yml` + `docker-compose.ec2-app.yml` | `prod` on EC2 #2 |

`prod` refuses to start without a JWT secret (≥64 decoded bytes), Redis password, explicit CORS origins (not `*`), `https` base/mail URLs, enabled mail, and a non-`@linkflow.local` sender. Compose uses `docker` so a laptop can run without a real SMTP domain.

## Prerequisites

- JDK 21 for host JAR builds (`JAVA_HOME` must be 21)
- Maven 3.9+
- Docker (Compose stack, Testcontainers, image builds)
- k6 only if you run the load suite

`.mvn/jvm.config` must be empty or contain only JVM flags — `#` comments are passed as a main class name.

## Full Compose (primary local stack)

```bash
./infrastructure/nginx/generate-dev-certs.sh
cp .env.example .env
# set LINKFLOW_JWT_SECRET (openssl rand -base64 64)
docker compose up --build
```

Open **https://localhost**. Mail: http://localhost:8025. Prometheus: http://localhost:9090. Grafana: http://localhost:3000 (admin/admin).

| Service | Published | Notes |
|---------|-----------|-------|
| nginx | 80, 443 | Only public application entry |
| linkflow-gateway / app / web | — | Private network |
| postgres / redis | — | Private; add the dev overlay to publish |
| mailhog | 8025 | Inbox |
| prometheus | 9090 | Scrapes app and gateway privately |
| grafana | 3000 | Provisioned **LinkFlow Overview** |

Bootstrap admin is **enabled** in Compose unless you override it (`admin@linkflow.local` / `ChangeMeAdmin1!`). Disable it after first use.

### Dev overlay

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres redis mailhog
```

Publishes 5432, 6379, 1025 (and optionally 8080–8082 if you start those services). Needed for host JARs.

### Host JARs

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn clean package -DskipTests
java -jar linkflow-app/target/linkflow-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
export LINKFLOW_APP_URI=http://127.0.0.1:8081
export LINKFLOW_WEB_URI=http://127.0.0.1:8082
java -jar linkflow-gateway/target/linkflow-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
java -jar linkflow-web/target/linkflow-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

On macOS use `127.0.0.1` for gateway upstreams — `localhost` may resolve to `::1`.

`SPRING_DATASOURCE_URL` has no default outside Compose; startup fails without it.

## Images

```bash
docker build -f infrastructure/Dockerfile --target app     -t linkflow-app .
docker build -f infrastructure/Dockerfile --target gateway -t linkflow-gateway .
docker build -f infrastructure/Dockerfile --target web     -t linkflow-web .
```

One Maven build stage is shared. Images: UID 1001, layered JARs, `HEALTHCHECK` on `/actuator/health/readiness`, `MaxRAMPercentage=75`, `ExitOnOutOfMemoryError`, JVM as PID 1. Compose `stop_grace_period` is 40s (app graceful shutdown budget is 25s).

## Nginx

`infrastructure/nginx/nginx.conf` + `conf.d/linkflow.conf`:

- TLS 1.2/1.3 at the edge; HTTP → HTTPS except `/.well-known/acme-challenge/`
- HSTS set here, stripped from upstream
- gzip for text/JSON/SVG
- Edge rate-limit zones (credential paths tighter than general)
- `/actuator` denied; `/nginx-health` answered by Nginx
- Static assets `max-age=300, must-revalidate` (filenames are not content-hashed)
- `/r/` is `no-store`
- `/vendor/**` proxied with other static assets

`generate-dev-certs.sh` writes a self-signed ECDSA cert (gitignored). Replace `infrastructure/nginx/certs/linkflow.crt` and `linkflow.key` for a real certificate.

Trusted proxies in Compose are the two `/32` addresses `172.28.0.10` (Nginx) and `172.28.0.11` (gateway). A subnet-wide CIDR would treat real clients as proxies and collapse rate-limit buckets.

## Health

| Target | URL |
|--------|-----|
| Nginx | `GET /nginx-health` |
| Liveness | `GET /actuator/health/liveness` (process only) |
| Readiness | `GET /actuator/health/readiness` (app: DB + Redis; web: Redis; gateway: process) |
| Metrics | `GET /actuator/prometheus` (private scrape) |

Liveness excludes dependencies so a database blip does not restart a healthy JVM.

## Email

Sends after transaction commit. Retries `LINKFLOW_MAIL_MAX_ATTEMPTS` (default 3). Activation / email-change links: 24h, idempotent. Reset: 15m, not idempotent. Per-recipient cooldown default 60s (`0` in `dev`). Spent tokens reaped after 7 days.

`LINKFLOW_MAIL_BASE_URL` must be reachable from a recipient's browser. Deliverability needs SPF/DKIM on a domain you control — Compose uses MailHog and does not prove that.

## Hosted deployment (2 EC2)

This is the **current** hosted topology. Nginx and Prometheus send traffic and scrapes to **EC2 #2 only**. There is no Terraform in the repo and no deploy script.

```
                         INTERNET
                            |
                            v
                    +---------------+
                    |    EC2 #1     |
                    | Edge / infra  |
                    | Nginx         |
                    | Redis         |
                    | Prometheus    |
                    | Grafana       |
                    +-------+-------+
                            |
                      private network
                            |
                            v
                    +---------------+
                    |    EC2 #2     |
                    | Gateway       |
                    | App           |
                    | Web           |
                    +-------+-------+
                            |
              Neon PostgreSQL + external SMTP
```

| Instance | File | Processes | Published by Compose |
|----------|------|-----------|----------------------|
| EC2 #1 | `docker-compose.ec2-edge.yml` | Nginx, Redis, Prometheus, Grafana | 80, 443; Redis `${REDIS_BIND_ADDRESS}:6379`; Grafana `127.0.0.1:3000`. Prometheus is not published |
| EC2 #2 | `docker-compose.ec2-app.yml` | gateway :8080, app :8081, web :8082 | 8080, 8081, 8082 (private path from #1, not public ingress) |

On #2 the three JVMs share Docker bridge `172.20.0.0/24` (gateway `172.20.0.10`, app `172.20.0.11`, web `172.20.0.12`). `LINKFLOW_TRUSTED_PROXIES` is `172.20.0.10/32`.

| Path | Current behavior |
|------|------------------|
| Public entry | Nginx on #1 (`infrastructure/nginx/linkflow-ec2.conf`). **HTTP-only** in that file. Compose publishes 443 and mounts `/etc/letsencrypt`; no HTTPS `server` block yet |
| To the app | One active upstream: `172.31.5.37:8080` (EC2 #2 gateway). `#3`/`#4` lines are commented placeholders |
| Redis | Only on #1. #2 sets `REDIS_HOST` to #1's private IP |
| PostgreSQL | Neon (external). `SPRING_DATASOURCE_*` on #2 |
| SMTP | External. `SPRING_MAIL_*` on #2 |
| Prometheus | On #1, unpublished. Scrapes `172.31.5.37:8081` and `:8080` only |
| Grafana | On #1, `127.0.0.1:3000` (SSH tunnel) |

AWS security-group IDs are not in this repository. Compose only states bound ports.

```bash
# EC2 #1
cp .env.ec2.example .env   # Section A
docker compose -f docker-compose.ec2-edge.yml up -d

# EC2 #2
cp .env.ec2.example .env   # Section B
docker compose -f docker-compose.ec2-app.yml up -d --build
```

**Future scale-out (not deployed):** the same app Compose file could run on additional hosts; Nginx/Prometheus have commented `#3`/`#4` slots. Do not treat those as live.

### Health

Edge: `GET /nginx-health`. EC2 #2 healthchecks hit readiness on 8081/8082/8080. App nodes use the `prod` profile.

## Environment variables

Copy `.env.example` to `.env`. Minimum for the app container: `LINKFLOW_JWT_SECRET`.

### App

| Variable | Default | Notes |
|----------|---------|-------|
| `SPRING_DATASOURCE_*` | required outside Compose | JDBC |
| `SPRING_DATA_REDIS_HOST` / `PORT` / `PASSWORD` | localhost / 6379 / empty | Password required in `prod` |
| `LINKFLOW_JWT_SECRET` | empty | Required in Compose. `JwtSecretValidator` (length + entropy) runs only on `prod` |
| `LINKFLOW_JWT_ACCESS_EXPIRATION_MS` | `900000` | 15 min |
| `LINKFLOW_JWT_REFRESH_EXPIRATION_MS` | `2592000000` | 30 days |
| `LINKFLOW_BASE_URL` | `http://localhost:8080` | Short-link prefix |
| `LINKFLOW_CORS_ALLOWED_ORIGINS` | `*` | `*` rejected in `prod` |
| `LINKFLOW_TRUSTED_PROXIES` | empty | CIDRs; empty ignores XFF |
| `LINKFLOW_RATE_LIMIT_USER_RPM` / `IP_RPM` | 100 / 200 | Sliding window |
| `LINKFLOW_RATE_LIMIT_AUTH_FAIL_CLOSED` | `true` | 503 on auth if Redis down |
| `LINKFLOW_SECURITY_SWAGGER_PUBLIC` | `true` | Off in `docker`/`prod` |
| `LINKFLOW_SECURITY_ACTUATOR_PUBLIC` | `true` | Off in `docker`/`prod` |
| `LINKFLOW_SECURITY_METRICS_PUBLIC` / `LINKFLOW_METRICS_PUBLIC` | `false` | Compose demo sets `true` |
| `LINKFLOW_SECURITY_EMAIL_VERIFICATION_REQUIRED` | `true` | |
| `LINKFLOW_URL_EXPIRED_CLEANUP_CRON` | `0 0 * * * *` | |
| `LINKFLOW_AUTH_SINGLE_USE_TOKEN_RETENTION_DAYS` | `7` | |
| `LINKFLOW_AUTH_REFRESH_TOKEN_REVOKED_RETENTION_DAYS` | `7` | |
| `LINKFLOW_ANALYTICS_CLICK_EVENTS_RETENTION_DAYS` | `365` | |
| `LINKFLOW_ANALYTICS_FLUSH_INTERVAL_MS` | `30000` | |
| `LINKFLOW_BOOTSTRAP_ADMIN_*` | disabled | Compose enables with demo values |

### Mail

| Variable | Default | Notes |
|----------|---------|-------|
| `SPRING_MAIL_HOST` / `PORT` | localhost / 1025 | MailHog locally |
| `SPRING_MAIL_SMTP_AUTH` / `STARTTLS` | false | Enable for a real relay |
| `LINKFLOW_MAIL_ENABLED` | `true` | `false` rejected in `prod` |
| `LINKFLOW_MAIL_FROM` | `no-reply@linkflow.local` | Rejected in `prod` |
| `LINKFLOW_MAIL_BASE_URL` | `http://localhost:8080` | Must be `https` in `prod` |
| `LINKFLOW_MAIL_MAX_ATTEMPTS` | `3` | |
| `LINKFLOW_MAIL_COOLDOWN_INTERVAL` | `60s` | `0` in `dev` |

### Gateway and web

| Variable | Default | Notes |
|----------|---------|-------|
| `LINKFLOW_APP_URI` | `http://127.0.0.1:8081` | Gateway → app |
| `LINKFLOW_WEB_URI` | `http://127.0.0.1:8082` | Gateway → web |
| `LINKFLOW_GATEWAY_URL` | `http://127.0.0.1:8080` | Web → gateway |
| `LINKFLOW_PUBLIC_GATEWAY_URL` | `http://localhost:8080` | Short links shown to users |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `false` | `true` behind HTTPS |

### Compose extras

`POSTGRES_*`, `REDIS_PASSWORD`, `REDIS_MAXMEMORY` (eviction disabled), `GRAFANA_ADMIN_*`.

## Observability

Prometheus scrapes `linkflow-app:8081` and `linkflow-gateway:8080`. Alert rules (`infrastructure/prometheus/alerts.yml`): app/gateway scrape down, elevated 5xx, email delivery failures, rate-limiter Redis unavailable, analytics flush failures, high heap. They evaluate in Prometheus. Grafana dashboard: `infrastructure/grafana/provisioning/dashboards/json/linkflow-overview.json`.

Business counters go through `LinkflowMetrics` (`linkflow_redirect_*`, URL cache, login, registration, URL create, rate-limit, analytics flush). Email delivery is recorded via `EmailDeliveryEvent`.

## CI

`.github/workflows/ci.yml` on push/PR to `main`/`master`:

1. `mvn -B clean verify` (JDK 21)
2. Build app, gateway, and web images (not pushed)
3. `docker compose config` and `nginx -t`
4. Advisory Trivy filesystem scan (does not fail the job)

## Load testing

k6 suite in `performance/`. Thresholds in `performance/thresholds/baseline.json` are **regression gates for one run**, not product SLOs. Do not quote them as measured performance.

```bash
docker compose up -d
./performance/scripts/seed.sh          # MailHog + verified users → performance/data/seed.json
./performance/run.sh smoke
```

Default Nginx/app limits will 429 a stress run. For stress/soak on a **disposable** database:

```bash
docker compose -f docker-compose.yml -f docker-compose.perf.yml up -d \
  --force-recreate nginx linkflow-app
```

`infrastructure/nginx/linkflow.perf.conf` stays **outside** `conf.d/` so it cannot load next to the normal site.

| Scenario | Writes DB? | Needs seed? |
|----------|------------|-------------|
| `smoke`, `login`, `redirect`, `analytics` | no* | yes (except none for registration) |
| `registration`, `url-creation`, `authenticated-mix`, `soak` | yes | see script |

\*Redirects still enqueue analytics.

Reports: `performance/reports/` (gitignored). `FLUSH_RATE_LIMITS=true` clears hot Redis keys. `docker compose down -v` destroys the local DB volume.

## Troubleshooting

| Symptom | What to check |
|---------|----------------|
| Maven Java version error | `JAVA_HOME` is JDK 21 |
| `Could not find or load main class #` | `#` comments in `.mvn/jvm.config` |
| `role "linkflow" does not exist` | Native Postgres already on 5432 |
| Gateway 500 to app on macOS | `LINKFLOW_APP_URI=http://127.0.0.1:8081` |
| JWT startup failure | `dev` profile or a ≥64-byte decoded secret |
| `NOAUTH Authentication required` | Redis password (`linkflow-local-redis` locally) |
| Redis connection refused on 6379 | Base Compose does not publish Redis — use the dev overlay |
| Integration tests fail | Docker Desktop running |
| Login after register does nothing | Open the MailHog activation link |
| k6 drowned in 429s | Perf overlay; default Nginx auth zone is ~10/min |
