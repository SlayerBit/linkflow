# Testing Guide

## Requirements

- **JDK 21** (Maven Enforcer)
- **Docker Desktop** (Testcontainers for integration tests)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn clean verify
```

## Run all tests

```bash
mvn clean verify          # unit + integration
mvn test                  # unit only
mvn verify -pl linkflow-app   # integration tests only
```

## Unit test coverage

| Module | Test classes |
|--------|--------------|
| `linkflow-common` | `Base62Test`, `SensitiveDataMaskingConverterTest` |
| `linkflow-auth` | `AuthServiceTest`, `RefreshTokenServiceTest` |
| `linkflow-url` | `ShortCodeGeneratorTest`, `IdempotencyServiceTest`, `RedirectServiceTest`, `UrlCacheServiceTest` |
| `linkflow-rate-limit` | `RateLimitServiceTest` |

## Integration tests (`linkflow-app`)

Base: `com.linkflow.app.support.AbstractIntegrationTest` — Testcontainers PostgreSQL 16 + Redis 7.

| Test class | Coverage |
|------------|----------|
| `AuthFlowIT` | Register, login, refresh rotation, logout |
| `UrlFlowIT` | CRUD, bulk, idempotency, redirect, expiry |
| `RateLimitIT` | Headers and HTTP 429 |
| `AdminAuthorizationIT` | Admin vs user, bootstrap admin |
| `AdminUserManagementIT` | Disable, enable, soft-delete users |
| `ActuatorExposureIT` | Prod profile actuator/Swagger rules |
| `AuthRateLimitRedisDownIT` | Auth paths return 503 when Redis unavailable |
| `AnalyticsAndCacheIT` | Click tracking, cache, recent click events |

## Gateway integration tests (`linkflow-gateway`)

| Test class | Coverage |
|------------|----------|
| `GatewayRoutingIT` | API/redirect/web/static routing; gateway health not proxied |

## Modules without dedicated tests

`linkflow-web`, `linkflow-user`, `linkflow-analytics`, `linkflow-observability` — covered indirectly via app ITs where applicable.

## Manual verification

1. Register/login via Swagger UI (gateway)
2. Create short URL with Bearer token
3. Visit `http://localhost:8080/r/{shortCode}`
4. Check `GET /api/v1/urls/{id}/analytics`
5. Prometheus targets: http://localhost:9090/targets (Docker stack)

## Related

- [production-readiness-audit.md](production-readiness-audit.md) — test gaps
