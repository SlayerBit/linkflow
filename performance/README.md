# LinkFlow performance suite (k6)

Reusable k6 harness for local and CI-adjacent load testing. **This directory is a runner, not a results report.** Default threshold numbers are regression gates for a single run — they are not LinkFlow SLOs and must not be quoted as measured product performance.

Measured numbers belong in a run's HTML/JSON report under `performance/reports/` after you execute against a real stack.

## Prerequisites

1. Stack up (Compose + Nginx recommended): `docker compose up -d`
2. [k6](https://k6.io/docs/get-started/installation/) installed
3. Seeded verified users + URLs (email verification is required in Docker)

Optional: raise **app and Nginx** rate limits for stress/soak so 429s do not dominate
(literal high limits in the overlay — a `.env` entry cannot keep the defaults).
Default Nginx edges are tight (`auth` ≈ 10/min, `general` ≈ 100/s) and will otherwise
measure the reverse proxy, not the JVM:

```bash
docker compose -f docker-compose.yml -f docker-compose.perf.yml up -d \
  --force-recreate nginx linkflow-app
```

## Seed

```bash
./performance/scripts/seed.sh
# writes performance/data/seed.json (gitignored)
```

Overrides: `BASE_URL`, `MAILHOG_URL`, `SEED_USERS`, `SEED_URLS_PER_USER`, `PERF_PASSWORD`, `SEED_FILE`.

## Run a scenario

```bash
./performance/run.sh smoke
./performance/run.sh redirect
VUS=20 DURATION=2m ./performance/run.sh login
RAMP_VUS=100 ./performance/run.sh stress
SOAK_DURATION=10m ./performance/run.sh soak
```

Reports land in `performance/reports/<scenario>-<timestamp>.{html,json}`.

After a prior flood left Redis rate-limit keys hot, flush them before a clean run:

```bash
FLUSH_RATE_LIMITS=true ./performance/run.sh smoke
```

## Scenarios

| Scenario | What it exercises | Writes DB? | Needs seed? |
|----------|-------------------|------------|-------------|
| `smoke` | login, `/me`, redirect, analytics | no | yes |
| `login` | `POST /api/v1/auth/login` | no | yes |
| `registration` | `POST /api/v1/auth/register` | yes | no |
| `redirect` | `GET /r/{code}` → 302 | no* | yes |
| `url-creation` | `POST /api/v1/urls` | yes | yes |
| `analytics` | analytics GETs for owned URLs | no | yes |
| `authenticated-mix` | list / create / analytics / redirect | yes | yes |
| `stress` | ramping redirect (+ sparse login) | no* | yes |
| `soak` | long steady redirect + light writes | yes | yes |

\*Redirects enqueue analytics asynchronously; expect Redis/DB activity.

## Environment

| Variable | Default | Purpose |
|----------|---------|---------|
| `BASE_URL` | `https://localhost` | Nginx edge (use `http://localhost:8080` for gateway-only) |
| `INSECURE_SKIP_TLS_VERIFY` | `true` | Self-signed Compose cert |
| `SEED_FILE` | `performance/data/seed.json` | Seed path for k6 `open()` |
| `VUS` / `DURATION` | `10` / `1m` | Constant-VU scenarios |
| `ARRIVAL_RATE` / `MAX_VUS` | `50` / `50` | Redirect open-loop RPS |
| `RAMP_RATE` / `RAMP_UP` / `SUSTAIN` / `RAMP_DOWN` | `200` / `1m` / `2m` / `1m` | Stress arrival stages |
| `SOAK_VUS` / `SOAK_DURATION` / `SOAK_SLEEP_SECONDS` | `5` / `30m` / `0.2` | Soak |
| `THRESHOLD_HTTP_P95_MS` etc. | see `thresholds/baseline.json` | Gate overrides |

## Rate limiting

Two layers throttle load tests if left at defaults:

| Layer | Default | Perf overlay |
|-------|---------|--------------|
| Nginx `auth` zone | ~10 req/min | 1000 req/s |
| Nginx `general` zone | ~100 req/s | 10000 req/s |
| App `LINKFLOW_RATE_LIMIT_*` | 100 user / 200 IP RPM | 10_000_000 |

Use `docker-compose.perf.yml` on a disposable database for stress/soak — never as a production profile.

## Layout

```
performance/
  config/          env + threshold helpers
  lib/             HTTP, auth, seed, checks, report, options
  scenarios/       one k6 script per workload
  scripts/seed.sh  MailHog-aware verified-user seed
  thresholds/      documented gate values (JSON)
  data/            seed.json (local, gitignored)
  reports/         HTML + JSON (local, gitignored)
  run.sh
```

## Teardown note

`registration`, `url-creation`, `authenticated-mix`, and `soak` insert rows. Use a throwaway Compose volume or reset Postgres between serious runs:

```bash
docker compose down -v   # destroys local DB volume — irreversible for that volume
```
