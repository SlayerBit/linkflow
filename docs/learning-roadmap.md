# LinkFlow Learning Roadmap

Dependency-aware order for understanding this repository. Times are estimates for a developer familiar with Java but new to the stack.

**Legend:** Must Know = required to work on core flows | Good To Know = important for interviews/ops | Advanced = optional depth

---

## Phase 1 — Repository and runtime (4–6 hours)

| # | Topic | Why | Where in repo | Depth | Time | Interview | Project |
|---|-------|-----|---------------|-------|------|-----------|---------|
| 1 | Maven multi-module layout | Understand build boundaries | `pom.xml`, module `pom.xml` files | Must Know | 1h | High | Critical |
| 2 | Runnable applications & ports | Know what to start | `LinkFlowApplication`, gateway, web main classes; `application.yml` | Must Know | 30m | High | Critical |
| 3 | Docker Compose stack | Run full infra | `docker-compose.yml`, `docker/` | Good To Know | 1h | Medium | High |
| 4 | Environment variables | Configure locally/prod | `application.yml`, `.env.example`, [environment.md](environment.md) | Must Know | 45m | Medium | Critical |
| 5 | LOCAL_SETUP troubleshooting | Avoid common blockers | [LOCAL_SETUP.md](../LOCAL_SETUP.md) | Good To Know | 30m | Low | High |

**Resources:** [README.md](../README.md), [system-design.md](system-design.md), [index.md](index.md)

---

## Phase 2 — Spring Boot core (8–12 hours)

| # | Topic | Why | Where | Depth | Time | Interview | Project |
|---|-------|-----|-------|-------|------|-----------|---------|
| 6 | Spring Boot 3.4 auto-config | How beans appear | App startup, `@SpringBootApplication` | Must Know | 2h | High | Critical |
| 7 | Spring MVC REST controllers | API surface | `*Controller` in auth/user/url/analytics | Must Know | 2h | High | Critical |
| 8 | Jakarta Validation | Request DTO rules | `RegisterRequest`, `CreateUrlRequest` | Must Know | 1h | Medium | High |
| 9 | `@Transactional` services | Business logic layer | `AuthService`, `UrlService`, etc. | Must Know | 2h | High | Critical |
| 10 | Global exception handling | Error JSON shape | `GlobalExceptionHandler` in common | Good To Know | 1h | Medium | Medium |
| 11 | Spring profiles | dev vs prod | `application-dev.yml`, `application-prod.yml` | Must Know | 30m | Medium | High |

**Resources:** Spring Boot reference docs; [code-walkthrough.md](code-walkthrough.md)

---

## Phase 3 — Security and auth (6–10 hours)

| # | Topic | Why | Where | Depth | Time | Interview | Project |
|---|-------|-----|-------|-------|------|-----------|---------|
| 12 | Spring Security filter chain | Request auth order | `SecurityConfig`, `JwtAuthenticationFilter` | Must Know | 2h | High | Critical |
| 13 | JWT (jjwt) | Access token mechanics | `JwtService`, `JwtProperties` | Must Know | 2h | High | Critical |
| 14 | Refresh token rotation | Session extension | `RefreshTokenService`, `refresh_tokens` table | Must Know | 2h | High | Critical |
| 15 | BCrypt passwords | Credential storage | `PasswordEncoder` bean | Must Know | 30m | Medium | High |
| 16 | `@PreAuthorize` / roles | Admin authorization | Admin controllers | Must Know | 1h | High | High |
| 17 | Web session auth | UI security model | `WebSecurityConfig`, `SessionManager` | Good To Know | 2h | Medium | High |

**Resources:** [security-review.md](security-review.md), [interview-prep.md](interview-prep.md)

---

## Phase 4 — Data layer (8–12 hours)

| # | Topic | Why | Where | Depth | Time | Interview | Project |
|---|-------|-----|-------|-------|------|-----------|---------|
| 18 | Flyway migrations | Schema evolution | `db/migration/V1–V6` | Must Know | 2h | High | Critical |
| 19 | JPA entities | Domain mapping | `User`, `ShortUrl`, `UrlAnalytics`, etc. | Must Know | 2h | High | Critical |
| 20 | Spring Data repositories | DB access | `*Repository` interfaces | Must Know | 2h | High | Critical |
| 21 | JPA auditing | createdAt/updatedAt | `AuditableEntity`, `@EnableJpaAuditing` | Good To Know | 1h | Medium | Medium |
| 22 | Soft delete pattern | Data lifecycle | `ShortUrl.softDelete`, partial indexes | Good To Know | 1h | Medium | High |
| 23 | Port/adapter pattern | Module decoupling | `UserLookupPort`, `ClickTrackingPort` | Must Know | 2h | High | Critical |

**Resources:** [database-design.md](database-design.md)

---

## Phase 5 — Redis and performance (4–8 hours)

| # | Topic | Why | Where | Depth | Time | Interview | Project |
|---|-------|-----|-------|-------|------|-----------|---------|
| 24 | Spring Data Redis | Connection config | `RedisConfig` | Must Know | 1h | Medium | High |
| 25 | Cache-aside redirect cache | Hot path optimization | `UrlCacheService` | Must Know | 2h | High | Critical |
| 26 | Redis Lua rate limiter | Atomic counters | `rate_limiter.lua`, `RateLimitService` | Must Know | 2h | High | Critical |
| 27 | Distributed locks | Alias collision | `RedisLockService` | Good To Know | 1h | Medium | Medium |
| 28 | Fail-open vs fail-closed rate limiting | Auth fail-closed; general fail-open | `RateLimitFilter`, `RateLimitService` | Good To Know | 30m | High | Medium |

**Resources:** [system-design.md](system-design.md), [adr/003-redis.md](adr/003-redis.md)

---

## Phase 6 — Domain features (6–8 hours)

| # | Topic | Why | Where | Depth | Time | Interview | Project |
|---|-------|-----|-------|-------|------|-----------|---------|
| 29 | URL creation & Base62 | Core product | `ShortCodeGenerator`, `UrlService` | Must Know | 2h | High | Critical |
| 30 | Redirect flow | Public path | `RedirectService`, `RedirectController` | Must Know | 1h | High | Critical |
| 31 | Idempotency | Safe retries | `IdempotencyService`, V5 migration | Good To Know | 2h | High | High |
| 32 | Async click tracking | Non-blocking analytics | `ClickTrackingService`, `AsyncConfig` | Must Know | 2h | High | Critical |
| 33 | QR codes (ZXing) | Feature completeness | `QrCodeService` | Good To Know | 1h | Low | Medium |
| 34 | Scheduled cleanup | Expired URLs | `ExpiredUrlCleanupJob` | Good To Know | 30m | Medium | Medium |

**Resources:** [feature-matrix.md](feature-matrix.md), [project-deep-dive.md](project-deep-dive.md)

---

## Phase 7 — Gateway and web (4–6 hours)

| # | Topic | Why | Where | Depth | Time | Interview | Project |
|---|-------|-----|-------|-------|------|-----------|---------|
| 35 | Spring Cloud Gateway routes | Edge routing | `linkflow-gateway/application.yml` | Must Know | 1h | High | High |
| 36 | Correlation IDs | Observability | `CorrelationIdGatewayFilter` | Good To Know | 30m | Medium | Medium |
| 37 | Thymeleaf SSR | Web UI | `templates/`, controllers in web | Good To Know | 2h | Medium | High |
| 38 | RestClient BFF pattern | Web → API | `BackendClient`, `*ApiClient` | Good To Know | 2h | Medium | High |

**Resources:** [linkflow-web-architecture.md](linkflow-web-architecture.md), [adr/005-spring-cloud-gateway.md](adr/005-spring-cloud-gateway.md)

---

## Phase 8 — Observability and testing (4–8 hours)

| # | Topic | Why | Where | Depth | Time | Interview | Project |
|---|-------|-----|-------|-------|------|-----------|---------|
| 39 | Spring Actuator | Health/metrics | `management.endpoints` in yml | Must Know | 1h | High | High |
| 40 | Micrometer + Prometheus | Metrics export | observability module, `prometheus.yml` | Good To Know | 2h | Medium | Medium |
| 41 | Testcontainers ITs | Integration testing | `AbstractIntegrationTest`, `*IT.java` | Must Know | 2h | High | High |
| 42 | Unit tests with Mockito | Isolated logic | `AuthServiceTest`, `RateLimitServiceTest` | Good To Know | 2h | Medium | Medium |

**Resources:** [testing.md](testing.md), [adr/007-observability-stack.md](adr/007-observability-stack.md)

---

## Phase 9 — Advanced / optional (4+ hours)

| # | Topic | Why | Where | Depth | Time | Interview | Project |
|---|-------|-----|-------|-------|------|-----------|---------|
| 43 | Modular monolith vs microservices | Architecture rationale | ADR-001, module map | Advanced | 1h | High | Medium |
| 44 | Caffeine (url module dep) | Local caching if used | `linkflow-url/pom.xml` | Advanced | 30m | Low | Low |
| 45 | Logstash JSON encoding | Structured logs | `linkflow-common` dependency | Advanced | 1h | Low | Medium |
| 46 | Production hardening | Go-live gaps | [production-readiness-audit.md](production-readiness-audit.md) | Advanced | 2h | High | High |

---

## Suggested learning path (summary)

```
Maven/ports → Spring Boot MVC → Security/JWT → Flyway/JPA → Redis/cache/rate-limit
    → URL/redirect/analytics → Gateway → Web UI → Actuator/tests → Production audit
```

**Minimum viable understanding (≈40h):** Phases 1–6 + items 35, 39, 41.

**Interview-ready (≈60h):** All Must Know + Good To Know items + [interview-prep.md](interview-prep.md).

---

## Related documents

- [code-walkthrough.md](code-walkthrough.md)
- [interview-prep.md](interview-prep.md)
- [index.md](index.md)
