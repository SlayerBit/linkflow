# LinkFlow interview guide

Answers about **this** repository. Diagrams: [ARCHITECTURE.md](ARCHITECTURE.md). Endpoints: [API.md](API.md). Do not quote k6 threshold numbers as product performance.

## 30 seconds

LinkFlow is a URL shortener implemented as a Java 21 modular monolith. Users create short links; visitors hit `GET /r/{code}` for a 302. PostgreSQL is the source of truth. Redis does redirect cache, sliding-window rate limits, click buffering, stampede locks, access-token revocation, web sessions, mail cooldown, and ShedLock. Auth is HS512 JWT plus rotating opaque refresh tokens, with SMTP activation and recovery. Hosted on a distributed 4-EC2 cluster in AWS (edge proxy + 3 load-balanced application nodes) under `https://linkflow.slayerbit.me`. Spring Cloud Gateway on each app host routes API, redirects, and a Thymeleaf UI.

## 2 minutes

Eleven Maven modules under one parent. Feature modules compile only against `linkflow-common` and talk through ports (`UserLookupPort`, `TokenRevocationPort`, `ClickTrackingPort`, `UrlStatsPort`, `EmailSenderPort`, `LinkflowMetrics`). They assemble in `linkflow-app` on 8081.

Redirects use Redis cache-aside: 15-minute freshness, 30-minute key for stale-while-revalidate, 90-second negative cache, stampede lock. Clicks go to a Redis Stream and flush to Postgres in batches so the 302 does not wait on a write. Rate limits are a Lua sliding window (60s). Auth paths return 503 if Redis is down; other paths fail open.

The web UI is a BFF with no `com.linkflow.*` compile dependency. JWTs sit in a Redis `HttpSession` (`linkflow:web:session`). Tabler and Chart.js are vendored so CSP can stay `'self'` plus a per-request script nonce.

## 5 minutes

Add:

**Email.** After-commit SMTP. Hashed 24h activation and email-change tokens (idempotent — scanners prefetch). 15-minute password reset (not idempotent). Resend/forgot always 200. Per-recipient cooldown, fail-open if Redis is down. `LINKFLOW_MAIL_BASE_URL` falls back to `LINKFLOW_BASE_URL` (`https://linkflow.slayerbit.me`). Login before verification returns 401 `EMAIL_NOT_VERIFIED`.

**Schema.** Flyway V1–V11: users/roles, refresh tokens, short URLs, analytics, idempotency + body hash, stream dedup, password reset, email verification, email change.

**Admin.** REST and UI: disable/enable/delete users, assign roles, deactivate/reactivate URLs, system stats and trends. Role changes in the JWT wait until refresh. Disabling a user goes through `TokenRevocationPort`.

**Ops.** Hosted layout is four EC2 instances: `linkflow-edge` (Nginx with Let's Encrypt TLS on AWS Elastic IP `13.206.178.184`, Redis, Prometheus, Grafana) and three identical app nodes (`linkflow-app-1`, `2`, `3` running gateway + app + web behind `least_conn`). Neon PostgreSQL and external SMTP. One Dockerfile, three targets, UID 1001, 25s graceful shutdown. Automated CI/CD via GitHub Actions: keyless OIDC, ECR push with immutable commit tags, AWS SSM sequential rolling deploy with automated rollback and resilient network timeouts. k6 lives under `performance/`.

## Why this shape

**Modular monolith.** Create, own, redirect, and attribute a click share one database and often one `@Transactional` method. Maven modules enforce boundaries without extra network hops. Analytics could move later because `ClickTrackingPort` is already async.

**PostgreSQL.** Unique short codes, FKs, ACID for refresh rotation and idempotency. Redis is the hot path, not the system of record.

**Redis.** Redirect cache, sliding-window limits, stampede locks, click stream, access-token revoke-after timestamps, web sessions, mail cooldown, ShedLock. QR codes use process-local Caffeine. Compose uses `noeviction` so Redis refuses writes rather than dropping sessions. Refresh tokens stay in PostgreSQL.

**JWT + opaque refresh.** Access tokens validate without a session store. Refresh tokens are revocable and rotated; reuse revokes every session. HS512 matches a ≥64-byte key. Issuer `linkflow` and audience `linkflow-api` are required on verify.

**Nginx and a Java gateway.** Nginx is the public edge on `linkflow-edge` (terminates TLS via Let's Encrypt for `linkflow.slayerbit.me`, enforces HTTP 301 to HTTPS, applies edge rate limiting). It proxies via `least_conn` to the three app nodes' gateways :8080, drops floods, and denies `/actuator`. The gateway is the app-level URL and injects `X-Correlation-ID`. JWT stays in the app.

**Fail-open vs fail-closed.** Unlimited login during a Redis outage is worse than 503. Redirects stay up. Mail cooldown fails open so recovery is not blocked by Redis.

**After-commit eviction and mail.** A rolled-back create must not drop a valid cache entry or email a token that does not exist.

## Likely questions

**Processes and ports?** Hosted: EC2 #1 `linkflow-edge` Nginx/Redis/Prometheus/Grafana; EC2 #2/#3/#4 identical app nodes each with gateway 8080 + app 8081 + web 8082. Local Compose: Nginx 443, then the same three JVM ports.

**Gateway routes?** App: `/api/**`, `/r/**`, swagger. Web: `/css/**`, `/js/**`, `/vendor/**`, `/webjars/**`, `/**`. Actuator is local to the gateway.

**Login?** `POST /api/v1/auth/login` → BCrypt via `UserLookupPort` → `EMAIL_NOT_VERIFIED` if needed → HS512 access token → hashed refresh token.

**Redirect?** Cache fresh / stale+async refresh / negative 404 / miss+lock+DB → async stream → 302. Evict after commit.

**Rate limit?** Lua sliding window, sorted set, 60s. Not a token bucket. Nginx is a coarser edge drop.

**Email rules you must not mix up.** Verify and email-change: idempotent. Reset: not. Resend/forgot: always 200. Cooldown is per recipient.

**Flyway?** V1–V11.

**Sessions?** Redis. Not `localStorage`. Not in-memory.

**Roles at runtime?** `PATCH .../roles` exists. JWT stale until refresh.

**What is not in the repo?** Social login, geo/device analytics, a notifier for Prometheus rules.

## Tests that exist

Unit tests in common, auth, url, rate-limit, notification, observability, web. Integration tests in `linkflow-app` (Testcontainers PostgreSQL 16 + Redis 7): auth, URLs, rate limits, admin, email (GreenMail), cache, streams, metrics, Redis-down auth. `GatewayRoutingIT`. `@WebMvcTest` page renders for public, user, admin, and error GET routes. No coverage percentage is claimed here.

## Current limits (say these out loud)

- Compose demo secrets
- JWT roles delayed until refresh
- `click_events` retained 365 days, unpartitioned
- Password+email auth only
- Counts/trends/recent events — not geo
- Alert rules fire in Prometheus; nothing pages
- k6 numbers are one run’s gates

## Whiteboard

1. Redirect: cache states, lock, async stream, 302
2. Login + refresh: BCrypt, HS512, hash storage, reuse → revoke-all
3. Module graph: features → common; app wires; web/gateway HTTP-only
4. Rate-limit tree: auth vs other; Redis up/down; Nginx vs app
5. Email prefetch: why verify is idempotent and reset is not
