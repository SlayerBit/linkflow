# Deployment Guide

> Canonical architecture: [system-design.md](system-design.md)

## Production checklist

- [ ] Set strong `LINKFLOW_JWT_SECRET` — must decode to ≥64 bytes for HS512 (`openssl rand -base64 64`)
- [ ] Set `SPRING_DATA_REDIS_PASSWORD` (required in prod)
- [ ] Set explicit `LINKFLOW_CORS_ALLOWED_ORIGINS` (`*` is rejected in prod)
- [ ] Set `LINKFLOW_TRUSTED_PROXIES` to the proxy hops only — see [why this must be narrow](docker.md#why-trusted-proxies-are-32-addresses)
- [ ] Use `spring.profiles.active=prod`
- [ ] Replace the self-signed certificate with a real one (see [TLS](#tls))
- [ ] Set `LINKFLOW_BASE_URL` and `LINKFLOW_MAIL_BASE_URL` to the public `https` domain
- [ ] Configure a real SMTP relay (`SPRING_MAIL_*`) with SPF/DKIM for the sending domain — see [Transactional email](#transactional-email)
- [ ] Configure PostgreSQL and Redis with persistence and backups
- [ ] Keep `LINKFLOW_SECURITY_SWAGGER_PUBLIC=false` and `LINKFLOW_SECURITY_ACTUATOR_PUBLIC=false`
- [ ] Set `LINKFLOW_METRICS_PUBLIC=true` only where the scraper reaches the app over a private network
- [ ] Change the default Grafana and database credentials
- [ ] Point Alertmanager (or a managed notifier) at `docker/prometheus/alerts.yml` if you want pages, not just firing rules in the Prometheus UI
- [ ] Deploy all three processes (gateway, app, web) behind the reverse proxy

The application enforces several of these itself: under `prod`, startup fails on a weak or missing
JWT secret, a wildcard CORS origin, a missing Redis password, a plaintext base URL, or a mail
configuration that cannot deliver. That is deliberate — a misconfigured security control that starts
successfully is worse than one that refuses to.

## Docker Compose

```bash
./docker/nginx/generate-dev-certs.sh
cp .env.example .env   # set LINKFLOW_JWT_SECRET
docker compose up -d --build
```

**Public entry:** `https://localhost` — Nginx, the only service publishing application ports.

Full service table, image properties, and health check details: [docker.md](docker.md).

## Nginx

`docker/nginx/` holds the edge configuration:

| File | Contents |
|------|----------|
| `nginx.conf` | Worker and connection tuning, logging, gzip, rate-limit zones |
| `conf.d/linkflow.conf` | Server blocks, TLS, upstream, per-path routing |
| `conf.d/proxy-headers.inc` | Forwarding headers shared by every location |
| `generate-dev-certs.sh` | Self-signed certificate for local use |

What it does:

- **TLS termination** — TLS 1.2/1.3 only, forward-secret AEAD ciphers, session cache, HTTP/2
- **HTTP to HTTPS** — 301 for everything except the ACME challenge path
- **HSTS** — set here at the TLS boundary and stripped from upstream responses so it appears once.
  Every other security header stays with the application, which knows the context; the CSP in
  particular carries a per-request nonce that Nginx could not reproduce.
- **Compression** — gzip for text, JSON, and SVG; skipped below ~1 KB and for already-compressed types
- **Edge rate limiting** — 100 r/s per IP generally, 10 r/m on credential endpoints. The application
  enforces its own limits with far more context; this exists so a volumetric flood is dropped before
  it costs a servlet thread and a Redis round trip. Credential endpoints are throttled harder
  because each attempt costs a BCrypt hash at strength 12.
- **Actuator denial** — `/actuator` is refused at the edge. Prometheus scrapes the app and gateway
  over the private network instead. This matters most for the gateway, which runs no security filter
  chain of its own and so cannot authenticate a scrape.
- **Static asset caching** — `max-age=300, must-revalidate`. Deliberately short: the filenames are
  not content-hashed, so a long immutable TTL would leave visitors on stale CSS after a deploy with
  no way to correct it.
- **No caching of redirects** — `no-store` on `/r/`. Every hit is a counted click and the target can
  change at any time.
- **Load balancing** — `least_conn` with keepalive to the upstream pool

### Scaling the gateway

Add one `server` line per instance to the `upstream` block. Compose's DNS does return every
replica's address for a scaled service, but Nginx resolves upstream names once at startup, so
`docker compose up --scale` alone will not spread load.

Note that Nginx also resolves those names *while parsing*, so it will not start if no upstream
resolves. `depends_on: service_healthy` handles the ordering in Compose.

### TLS

`generate-dev-certs.sh` produces a self-signed ECDSA certificate for local use only. It exercises
the real TLS path — termination, HSTS, secure cookies, the redirect — without needing a public DNS
name. Browsers will warn, which is correct.

For a real deployment, obtain a certificate from a CA and write it to `docker/nginx/certs/` as
`linkflow.crt` and `linkflow.key`. For Let's Encrypt via HTTP-01, the port 80 server block already
serves `/.well-known/acme-challenge/` from `/var/www/certbot`; mount that path into both the Nginx
container and certbot, and reload Nginx after renewal (`nginx -s reload`, which is graceful).

Certificates and keys are gitignored. Nothing private is committed.

## Process layout (non-Docker)

Run three JARs with the `dev` profile locally:

```bash
java -jar linkflow-app/target/linkflow-app-*.jar --spring.profiles.active=dev
java -jar linkflow-gateway/target/linkflow-gateway-*.jar
java -jar linkflow-web/target/linkflow-web-*.jar
```

Set `LINKFLOW_WEB_URI=http://127.0.0.1:8082` on the gateway for single-host UX. Unlike Compose,
this has no bundled database: `SPRING_DATASOURCE_URL` has no default and startup fails without it.

## Graceful shutdown

All three processes drain in-flight requests on `SIGTERM` rather than severing them, with a 25s
budget (`SPRING_LIFECYCLE_SHUTDOWN_TIMEOUT`). The orchestrator's kill grace period must exceed it —
Compose sets `stop_grace_period: 40s`, and Docker's 10s default would `SIGKILL` mid-drain.

The click-tracking executor also drains its queue on shutdown, so buffered analytics are not
discarded on deploy.

The mail executor drains too, but its queue is bounded and its rejection policy aborts rather than
blocking. That is deliberate: sends are dispatched from an after-commit listener running on the
request thread, so absorbing overflow there would stall an HTTP response behind an SMTP conversation
that is already retrying against an unresponsive relay. Overflow means 200 queued messages and four
stuck delivery threads — an outage to alert on, not a condition to absorb quietly — and every
affected flow has a user-driven retry. Rejections are logged at `ERROR` by the dispatcher.

## Transactional email

Account activation, password reset, and email change are all real SMTP, with no mock path in
production: under the `prod` profile the application refuses to start if mail is disabled, the
sender is left at the `@linkflow.local` default, or the link base URL is not `https`. A user locked
out by an email that was never sent has no other way back in, so this fails at startup rather than
at 3am.

Operationally, the parts worth knowing:

| Concern | Behaviour |
|---------|-----------|
| Delivery | Asynchronous, after transaction commit, so a rolled-back registration never emails a token that does not exist |
| Retries | `LINKFLOW_MAIL_MAX_ATTEMPTS` attempts with exponential backoff; failures are logged and never propagate to the caller |
| Link lifetime | Activation and email change 24h, password reset 15m |
| Supersession | Issuing a new link invalidates any earlier one, so a mailbox never holds two working links |
| Abuse | `LINKFLOW_MAIL_COOLDOWN_INTERVAL` (default 60s) caps how often one address can be mailed |
| Retention | Spent and expired tokens are deleted after `LINKFLOW_AUTH_SINGLE_USE_TOKEN_RETENTION_DAYS` by a nightly ShedLock-guarded job |

Two behaviours surprise people, so they are worth stating outright.

**Activation and email-change links are idempotent.** Opening one twice succeeds rather than
reporting the token as spent. Links in email are followed by more than the recipient — mail clients
prefetch them, corporate scanners open them to check for malware, chat clients fetch them to build
previews — so the token is frequently redeemed by a machine before the person clicks. Password reset
is deliberately *not* idempotent, because that token authorises setting a secret and honouring a
replay would overwrite a password that had since been changed.

**The per-recipient cooldown is not the same control as the IP rate limit.** The IP limit governs
how fast a caller may ask; it does nothing about how much mail a third party receives, because
resend-verification and forgot-password both accept an arbitrary address and send to it. Keying on
the recipient is what stops the application being aimed at someone else's inbox, and it is what
protects the sending domain's reputation. Suppressed requests return exactly the same response as
successful ones, so the throttle cannot be used to discover which addresses are registered.

Deliverability is a DNS problem more than an application one. `LINKFLOW_MAIL_FROM` must be on a
domain you control, with SPF and DKIM records covering the relay, or the messages are filed as spam
and the flows above appear broken for reasons no log will show.

## Kubernetes — deliberate non-goal

This repository is **Compose-first**. No Kubernetes manifests are maintained here.

The images are built for it regardless: non-root with a numeric UID (so `runAsNonRoot` can be
enforced), separate liveness and readiness probes, heap sized from the container limit, and correct
`SIGTERM` handling. A deployment would need at minimum:

1. Managed PostgreSQL and Redis
2. `linkflow-app` Deployment + ClusterIP Service (8081), probes on `/actuator/health/{liveness,readiness}`
3. `linkflow-web` Deployment + ClusterIP Service (8082)
4. `linkflow-gateway` Deployment + Ingress (8080, TLS termination), with `/actuator` denied at the Ingress
5. Secrets for the JWT signing key, database, Redis, and SMTP credentials
6. `terminationGracePeriodSeconds` above 25
7. Prometheus Operator or managed observability scraping app and gateway metrics over the cluster network

## Health checks

| Target | URL | Notes |
|--------|-----|-------|
| Nginx | `GET /nginx-health` | Answered by Nginx itself, so "Nginx down" is distinguishable from "app down" |
| Liveness | `GET /actuator/health/liveness` | Process only — excludes external dependencies on purpose |
| Readiness | `GET /actuator/health/readiness` | App: database + Redis. Web: Redis. Gateway: process only |
| Metrics | `GET /actuator/prometheus` | App and gateway; denied at the edge, scraped privately |

Liveness excluding dependencies is the important distinction: a database blip should take an
instance out of the load balancer, not have the orchestrator kill and restart a process that is
working fine.

## Related

- [docker.md](docker.md)
- [security-review.md](security-review.md)
