# LinkFlow

URL shortener built as a **modular monolith** in Java 21 and Spring Boot 3.4.1. Users create short links (optional alias and expiry). Visitors follow `GET /r/{shortCode}`. Clicks are recorded asynchronously. Operators use a REST API or a server-rendered web UI.

## Features

- Register, login, refresh, logout — JWT access tokens (HS512) and rotating opaque refresh tokens
- Email activation, resend, password reset, and email change over SMTP (hashed single-use tokens)
- Single and bulk URL create with optional `Idempotency-Key`
- Public redirect with Redis cache-aside (stale-while-revalidate, negative cache, stampede lock)
- Per-URL and system analytics (aggregates, 7/30/90-day trends, recent feeds; user IPs masked)
- QR PNG (ZXing)
- Redis Lua sliding-window rate limits (per user / per IP) plus Nginx edge limits in Compose
- Admin API and UI: users (disable/enable/delete/roles), URLs, analytics, system health
- Prometheus metrics, Grafana **LinkFlow Overview**, Prometheus alert rules

## Hosted layout

Two EC2 instances: **#1** is the public edge (Nginx, Redis, Prometheus, Grafana); **#2** runs gateway + app + web. PostgreSQL is Neon (external); SMTP is external. Details: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Processes

On a laptop, **Nginx** is the only public application entry (`https://localhost`). On the hosted stack, that role is EC2 #1.

| Process | Port | Role |
|---------|------|------|
| nginx | 80, 443 | TLS, edge rate limits, `/actuator` deny |
| `linkflow-gateway` | 8080 | Routes `/api/**`, `/r/**`, Swagger, and the UI |
| `linkflow-app` | 8081 | Backend — all feature modules, Flyway, schedulers |
| `linkflow-web` | 8082 | Thymeleaf UI; JWTs live in a Redis `HttpSession` |

Infrastructure in the full stack: PostgreSQL 16, Redis 7, MailHog, Prometheus, Grafana.

## Modules

Feature modules depend only on `linkflow-common`. Cross-module calls use ports. `linkflow-web` has no compile dependency on `com.linkflow.*`.

| Module | Role |
|--------|------|
| `linkflow-common` | Envelopes, exceptions, Redis config, ports, metrics interface |
| `linkflow-auth` | JWT, refresh tokens, account recovery |
| `linkflow-user` | Profiles, admin users |
| `linkflow-url` | Short URLs, redirects, QR, cache, idempotency |
| `linkflow-rate-limit` | Sliding-window limiter |
| `linkflow-analytics` | Click stream, flush, queries |
| `linkflow-notification` | SMTP + mail templates |
| `linkflow-observability` | Actuator extras, Micrometer, Redis health |
| `linkflow-app` | Runnable backend |
| `linkflow-gateway` | Path routing + `X-Correlation-ID` |
| `linkflow-web` | SSR UI |

## Quick start

**JDK 21** is required (`JAVA_HOME` must point at 21). Docker is required for the stack and for integration tests.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS

./infrastructure/nginx/generate-dev-certs.sh
cp .env.example .env                               # set LINKFLOW_JWT_SECRET
docker compose up --build
```

Open **https://localhost**. The browser warns about the self-signed certificate. Read activation mail at http://localhost:8025.

Host JAR workflow (Postgres, Redis, MailHog published on localhost):

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres redis mailhog
mvn clean package -DskipTests
java -jar linkflow-app/target/linkflow-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
# then gateway and web; on macOS use LINKFLOW_APP_URI=http://127.0.0.1:8081
```

Use the `dev` profile for host JARs. Do not use `prod` locally — it refuses to start without a real SMTP relay, `https` mail/base URLs, explicit CORS origins, and a Redis password. Compose uses the `docker` profile for that reason.

## Build and test

```bash
mvn clean package -DskipTests
mvn test
mvn clean verify          # unit + integration; Docker required for Testcontainers
```

GitHub Actions (`.github/workflows/ci.yml`) runs `mvn verify`, builds the three images, and validates Compose/Nginx config.

## Repository layout

```text
.
├── README.md
├── pom.xml                         # Maven parent (modules stay at root)
├── docker-compose.yml              # local stack: docker compose up
├── docker-compose.dev.yml          # publish Postgres/Redis for host JARs
├── docker-compose.perf.yml         # k6 overlay
├── docker-compose.ec2-*.yml        # hosted edge + app node
├── .env.example / .env.ec2.example # copy to .env at this directory
├── docs/                           # architecture, API, deployment, interview
├── infrastructure/                 # Dockerfile, Nginx, Prometheus, Grafana
├── performance/                    # k6 scenarios and seed scripts
├── linkflow-*/                     # Maven modules
└── .github/workflows/ci.yml
```

## Documentation

| File | Contents |
|------|----------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, flows, Redis, schema, security |
| [docs/API.md](docs/API.md) | REST inventory and web routes |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Local Compose, 2-EC2 hosted stack, env vars, load tests |
| [docs/INTERVIEW_GUIDE.md](docs/INTERVIEW_GUIDE.md) | Pitches and design Q&A for this codebase |

