# LinkFlow Production Readiness Audit

Audit basis: repository source after architectural remediation (June 2026).  
Canonical architecture: [system-design.md](system-design.md)

---

## Executive summary

LinkFlow is a production-style modular monolith with integration tests, Flyway migrations, JWT auth, profile-based security, rate limiting with auth-path fail-closed behavior, and a full Docker Compose stack including the web UI. Remaining items are **deliberate non-goals** or **operational concerns** outside this repository (TLS termination, secret managers, click retention jobs).

---

## Resolved findings

| ID | Original finding | Resolution |
|----|------------------|------------|
| C1 | Actuator publicly accessible | Profile-based `LinkflowSecurityProperties`; prod exposes health only (+ optional metrics flag) |
| C2 | JWT secret empty in prod | `JwtSecretValidator` fails fast on prod startup |
| H3 | No gateway tests | `GatewayRoutingIT` with MockWebServer |
| H4 | Web UI not in Compose | `linkflow-web` service + `Dockerfile.web` |
| H5 | Rate limit fail-open on auth | Auth paths fail closed (`RATE_LIMIT_BACKEND_UNAVAILABLE`) |
| H4 (gateway UX) | Web not routed via gateway | Gateway proxies `/`, static assets, and `/**` to web |
| M4 | Swagger public in prod | Disabled via `springdoc` + `denyAll` in prod |
| — | No admin user lifecycle | PATCH disable/enable, DELETE soft-delete |
| — | No click event read API | GET recent clicks for owner and admin |
| — | Open questions in docs | Replaced with final decisions in system-design.md |

---

## Remaining operational items (not code gaps)

| Item | Status | Notes |
|------|--------|-------|
| H1 CORS wildcard default | Configure per environment | Set `LINKFLOW_CORS_ALLOWED_ORIGINS` in prod |
| H2 Default Compose passwords | Change before shared use | Documented in deployment.md |
| H6 HTTPS | Deliberate non-goal in repo | Terminate at load balancer |
| M1 Account lockout / CAPTCHA | Deliberate non-goal | Rate limiting provides baseline protection |
| M2 Click event retention | Future ops job | Documented as known limitation |
| M3 Web session clustering | Deliberate non-goal | In-memory sessions; sticky sessions required for multi-instance web |
| M7 Prometheus alert rules | Out of repo scope | Scrape config present; alerting is deployment-specific |
| L4 Kubernetes manifests | Deliberate non-goal | Compose-first; outline in deployment.md |

---

## Test coverage (current)

| Module | Unit tests | Integration tests |
|--------|------------|-------------------|
| linkflow-common | ✅ | — |
| linkflow-auth | ✅ | via app ITs |
| linkflow-url | ✅ | via app ITs |
| linkflow-rate-limit | ✅ (incl. fail-closed) | via app ITs |
| linkflow-user | — | via AdminUserManagementIT |
| linkflow-analytics | — | via AnalyticsAndCacheIT |
| linkflow-app | — | ✅ 9 IT classes |
| linkflow-gateway | — | ✅ GatewayRoutingIT |
| linkflow-web | — | — (session/UI tests deferred) |

New integration tests: `ActuatorExposureIT`, `AuthRateLimitRedisDownIT`, `AdminUserManagementIT`, click history in `AnalyticsAndCacheIT`.

---

## Related documents

- [security-review.md](security-review.md)
- [system-design.md](system-design.md)
- [deployment.md](deployment.md)
- [testing.md](testing.md)
