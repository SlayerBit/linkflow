# LinkFlow System Design

**Canonical architecture document.** Other architecture summaries cross-link here.

LinkFlow is a production-style URL shortener implemented as a **modular monolith** with a **Spring Cloud Gateway** entry point and a **server-rendered web UI**. Source of truth: Maven modules under `com.linkflow`, Flyway migrations in `linkflow-app/src/main/resources/db/migration/`, and runtime configs in `application.yml` files.

---

## System overview

LinkFlow provides:

1. **User authentication** — register, login, JWT access tokens, opaque refresh tokens with rotation
2. **URL management** — create, bulk create, list, update, soft-delete short URLs with optional custom aliases and expiry
3. **Public redirects** — `GET /r/{shortCode}` returns HTTP 302 to the original URL
4. **Analytics** — async click tracking, aggregate counters, recent click event listing per URL
5. **Rate limiting** — Redis Lua counter per authenticated user or anonymous IP; auth paths fail closed when Redis is down
6. **Admin operations** — list users, disable/enable/soft-delete users, deactivate URLs, system-wide analytics
7. **Observability** — profile-based actuator exposure, Prometheus scrape, Grafana dashboards (Docker stack)

### Runnable processes

| Application | Main class | Port | Config file |
|-------------|------------|------|-------------|
| `linkflow-app` | `com.linkflow.app.LinkFlowApplication` | 8081 | `linkflow-app/src/main/resources/application.yml` |
| `linkflow-gateway` | `com.linkflow.gateway.LinkFlowGatewayApplication` | 8080 | `linkflow-gateway/src/main/resources/application.yml` |
| `linkflow-web` | `com.linkflow.web.LinkFlowWebApplication` | 8082 | `linkflow-web/src/main/resources/application.yml` |

**User-facing entry point:** `http://localhost:8080` (gateway) serves the web UI at `/`, API at `/api/**`, and redirects at `/r/**`.

---

## System context diagram

```mermaid
flowchart TB
    RegisteredUser["Registered user\n(API client or browser)"]
    AnonymousVisitor["Anonymous visitor\n(follows short link)"]
    Administrator["Administrator"]

    subgraph LinkFlowSystem["LinkFlow system"]
        Gateway["API Gateway\nport 8080"]
        WebUI["Web UI\nThymeleaf port 8082"]
        BackendApp["Backend application\nmodular monolith port 8081"]
    end

    PostgreSQL[(PostgreSQL 16)]
    Redis[(Redis 7)]
    Prometheus["Prometheus"]
    Grafana["Grafana"]

    RegisteredUser --> Gateway
    RegisteredUser --> WebUI
    Administrator --> Gateway
    AnonymousVisitor --> Gateway

    Gateway --> BackendApp
    Gateway --> WebUI
    WebUI --> Gateway

    BackendApp --> PostgreSQL
    BackendApp --> Redis
    Prometheus --> BackendApp
    Prometheus --> Gateway
    Grafana --> Prometheus
```

---

## Container diagram

```mermaid
flowchart LR
    subgraph GatewayContainer["Gateway :8080"]
        RouteTable["Route table\n/api, /r, /swagger, /css, /js, /**"]
        CorrelationFilter["Correlation ID filter"]
    end

    subgraph BackendContainer["Backend app :8081"]
        AuthModule["Authentication"]
        UserModule["Users"]
        UrlModule["URLs and redirects"]
        RateLimitModule["Rate limiting"]
        AnalyticsModule["Analytics"]
        ObservabilityModule["Observability"]
    end

    subgraph WebContainer["Web UI :8082"]
        PageControllers["Page controllers"]
        ApiClients["HTTP clients to gateway"]
        HttpSession["HttpSession auth state"]
    end

    GatewayContainer --> BackendContainer
    GatewayContainer --> WebContainer
    WebContainer --> GatewayContainer
    BackendContainer --> PostgreSQL[(PostgreSQL)]
    BackendContainer --> Redis[(Redis)]
```

Gateway contains **no business logic** — only routing and `CorrelationIdGatewayFilter`.

Web module has **zero compile-time dependency** on other `com.linkflow.*` modules; it consumes the gateway over HTTP.

---

## Gateway routing diagram

Routes are defined in `linkflow-gateway/src/main/resources/application.yml`. **First match wins** — specific API paths precede the web catch-all.

```mermaid
flowchart TD
    Incoming["Incoming request\ngateway :8080"] --> Match{Path prefix?}

    Match -->|"/api/**"| BackendApi["Backend app :8081\nREST API"]
    Match -->|"/r/**"| BackendRedirect["Backend app :8081\nRedirect handler"]
    Match -->|"/swagger-ui/**, /v3/api-docs/**"| BackendDocs["Backend app :8081\nOpenAPI docs"]
    Match -->|"/css/**, /js/**, /webjars/**"| WebStatic["Web UI :8082\nStatic assets"]
    Match -->|"/**"| WebPages["Web UI :8082\nServer-rendered pages"]

    GatewayHealth["Gateway /actuator/health\nhandled locally, not proxied"]
```

**Deliberate non-routes:**

- **App actuator** is not proxied through the gateway. Prometheus scrapes `linkflow-app:8081/actuator/prometheus` directly inside Docker. This keeps gateway `/actuator/**` reserved for gateway health only.

---

## Module dependency diagram

Feature modules depend **only** on `linkflow-common`. Cross-module integration uses port interfaces:

| Port | Implemented by | Used by |
|------|----------------|---------|
| `UserLookupPort` | `UserLookupAdapter` | `AuthService` |
| `ClickTrackingPort` | `ClickTrackingAdapter` | `RedirectService` |

```mermaid
flowchart TD
    Common["linkflow-common"]
    Auth["linkflow-auth"] --> Common
    User["linkflow-user"] --> Common
    Url["linkflow-url"] --> Common
    RateLimit["linkflow-rate-limit"] --> Common
    Analytics["linkflow-analytics"] --> Common
    Observability["linkflow-observability"] --> Common
    App["linkflow-app"] --> Auth & User & Url & RateLimit & Analytics & Observability
    Gateway["linkflow-gateway"]
    Web["linkflow-web"]
```

See [module-dependency-map.md](module-dependency-map.md) for Maven detail.

---

## Backend component diagram

```mermaid
flowchart TB
    subgraph Controllers["REST controllers"]
        AuthController["AuthController"]
        UserControllers["UserController / AdminUserController"]
        UrlControllers["UrlController / RedirectController / AdminUrlController"]
        AnalyticsControllers["AnalyticsController / AdminAnalyticsController"]
    end

    subgraph SecurityLayer["Security layer"]
        SecurityConfig["SecurityConfig\nprofile-based exposure"]
        JwtFilter["JwtAuthenticationFilter"]
        RateLimitFilter["RateLimitFilter"]
    end

    subgraph Services["Application services"]
        AuthServices["AuthService / JwtService / RefreshTokenService"]
        UserService["UserService"]
        UrlServices["UrlService / RedirectService"]
        AnalyticsServices["ClickTrackingService / AnalyticsQueryService"]
        RateLimitService["RateLimitService"]
    end

    subgraph Infrastructure["Infrastructure"]
        JpaRepositories["JPA repositories"]
        UrlCache["UrlCacheService"]
        RedisLock["RedisLockService"]
        Flyway["Flyway migrations"]
    end

    Client["HTTP client"] --> JwtFilter --> RateLimitFilter --> Controllers
    Controllers --> Services --> JpaRepositories & UrlCache & RedisLock
    JpaRepositories --> PostgreSQL[(PostgreSQL)]
    UrlCache & RateLimitService --> Redis[(Redis)]
```

---

## Authentication sequence

```mermaid
sequenceDiagram
    participant Client as API client
    participant Gateway as Gateway
    participant AuthController as AuthController
    participant AuthService as AuthService
    participant UserLookup as UserLookupPort
    participant JwtService as JwtService
    participant RefreshTokens as RefreshTokenService
    participant Database as PostgreSQL

    Client->>Gateway: POST /api/v1/auth/login
    Gateway->>AuthController: forward request
    AuthController->>AuthService: login(credentials)
    AuthService->>UserLookup: findByEmail
    UserLookup->>Database: SELECT user
    AuthService->>AuthService: verify password (BCrypt)
    AuthService->>JwtService: generate access token
    AuthService->>RefreshTokens: create refresh token
    RefreshTokens->>Database: INSERT refresh_tokens
    AuthService-->>Client: TokenResponse
```

**Web UI:** JWTs stored in `HttpSession` as `AuthState` — never in browser `localStorage`. See [linkflow-web-architecture.md](linkflow-web-architecture.md).

---

## URL create and redirect sequence

```mermaid
sequenceDiagram
    participant Visitor as Visitor
    participant Gateway as Gateway
    participant RedirectController as RedirectController
    participant RedirectService as RedirectService
    participant UrlCache as UrlCacheService
    participant Database as PostgreSQL
    participant ClickTracking as ClickTrackingPort
    participant Analytics as ClickTrackingService

    Visitor->>Gateway: GET /r/{shortCode}
    Gateway->>RedirectController: forward request
    RedirectController->>RedirectService: resolveRedirect
    RedirectService->>UrlCache: get cached URL
    alt cache hit
        UrlCache-->>RedirectService: cached entry
    else cache miss
        RedirectService->>Database: findByShortCode
        RedirectService->>UrlCache: put with TTL
    end
    RedirectService->>ClickTracking: trackClick (async)
    ClickTracking->>Analytics: persist click event
    Analytics->>Database: INSERT click_events, UPDATE url_analytics
    RedirectService-->>Visitor: 302 redirect
```

---

## Rate limiting decision flow

```mermaid
flowchart TD
    Request["HTTP request"] --> SkipDocs{Actuator or Swagger path?}
    SkipDocs -->|yes| Continue["Continue filter chain"]
    SkipDocs -->|no| AuthPath{"/api/v1/auth/* path?"}

    AuthPath -->|yes| IpLimitAuth["IP rate limit\nfail-closed if Redis down"]
    AuthPath -->|no| Authenticated{Authenticated user?}

    Authenticated -->|yes| UserLimit["User rate limit\nfail-open if Redis down"]
    Authenticated -->|no| IpLimit["IP rate limit\nfail-open if Redis down"]

    IpLimitAuth --> RedisCheck{Redis available?}
    UserLimit --> RedisCheck
    IpLimit --> RedisCheck

    RedisCheck -->|yes, under limit| Headers["Set X-RateLimit-* headers"]
    RedisCheck -->|yes, over limit| TooMany["429 Too Many Requests"]
    RedisCheck -->|no, auth path| Unavailable["503 Service Unavailable"]
    RedisCheck -->|no, other paths| Allow["Allow request (fail-open)"]

    Headers --> Continue
```

Configuration: `linkflow.rate-limit.auth-fail-closed` (default `true`).

---

## Analytics flow

```mermaid
sequenceDiagram
    participant RedirectService as RedirectService
    participant Port as ClickTrackingPort
    participant TrackingService as ClickTrackingService
    participant ClickEvents as click_events table
    participant UrlAnalytics as url_analytics table
    participant ReadApi as AnalyticsQueryService

    RedirectService->>Port: trackClick (async)
    Port->>TrackingService: save event
    TrackingService->>ClickEvents: INSERT row
    TrackingService->>UrlAnalytics: increment total_clicks

    Note over ReadApi: Read paths (sync)
    ReadApi->>UrlAnalytics: aggregate counts
    ReadApi->>ClickEvents: recent events (paginated, max 100)
```

**Product decision:** Aggregate counts plus **recent click listing** are exposed. Time-series rollups, geo breakdown, and referer analytics dashboards are **deliberate non-goals** for v1. See [feature-matrix.md](feature-matrix.md).

---

## Role model (final decision)

Roles are stored in a normalized `roles` table (`USER`, `ADMIN`) seeded by Flyway V1. Users reference roles via `user_roles.role_id` using `@ElementCollection Set<Long> roleIds` on the `User` entity. `RoleEntity` and `RoleService` resolve IDs ↔ names at runtime.

**Why this design:** Only two fixed roles exist. A full `@ManyToMany` entity graph adds complexity without benefit at this scale. JWT claims embed role names at login/refresh; admin role assignment at runtime is a **deliberate non-goal** (roles set at registration or bootstrap only).

---

## Security and actuator exposure

Controlled by `linkflow.security.*` properties (`LinkflowSecurityProperties`):

| Profile | swagger-public | actuator-public | metrics-public |
|---------|----------------|-----------------|----------------|
| default / dev | `true` | `true` | `false` |
| prod | `false` | `false` | `${LINKFLOW_METRICS_PUBLIC:false}` |

In **prod**: only `/actuator/health` is public on the backend. Swagger is denied. Prometheus metrics are public only when `LINKFLOW_METRICS_PUBLIC=true` (set in Docker Compose for the demo stack).

`JwtSecretValidator` fails fast on startup if `prod` profile is active and JWT secret is missing or too short.

Full threat analysis: [security-review.md](security-review.md)

---

## Web session strategy (final decision)

`linkflow-web` uses **in-memory `HttpSession`** with `http-only`, `same-site=strict` cookies. Session stores access/refresh tokens server-side.

**Operational implication:** Horizontal scaling of the web tier requires **sticky sessions** or a future migration to Spring Session Redis. Redis-backed sessions are a **deliberate non-goal** for the current Compose-first scope.

---

## Deployment topology

```mermaid
flowchart TB
    subgraph DockerHost["Docker Compose host"]
        Gateway["linkflow-gateway\n:8080 public entry"]
        WebUI["linkflow-web\n:8082 internal + optional host map"]
        Backend["linkflow-app\n:8081 internal + optional host map"]
        Postgres[(postgres :5432)]
        Redis[(redis :6379)]
        Prometheus["prometheus :9090"]
        Grafana["grafana :3000"]
    end

    Browser["Browser"] --> Gateway
    Gateway --> Backend
    Gateway --> WebUI
    WebUI --> Gateway
    Backend --> Postgres
    Backend --> Redis
    Prometheus --> Backend
    Prometheus --> Gateway
    Grafana --> Prometheus
```

Dockerfiles: `docker/Dockerfile.app`, `docker/Dockerfile.gateway`, `docker/Dockerfile.web`.

**Kubernetes:** Out of scope for this repository. See [deployment.md](deployment.md) for a reference outline only.

---

## Entity relationship diagram

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : assigned
    USERS ||--o{ REFRESH_TOKENS : owns
    USERS ||--o{ SHORT_URLS : owns
    SHORT_URLS ||--o| URL_ANALYTICS : aggregates
    SHORT_URLS ||--o{ CLICK_EVENTS : receives
    USERS ||--o{ IDEMPOTENCY_RECORDS : owns

    USERS {
        uuid id PK
        string email UK
        string password_hash
        boolean enabled
        boolean deleted
    }

    ROLES {
        bigint id PK
        string name UK
    }

    USER_ROLES {
        uuid user_id FK
        bigint role_id FK
    }

    SHORT_URLS {
        uuid id PK
        uuid owner_id FK
        string short_code UK
        string original_url
        boolean active
    }

    URL_ANALYTICS {
        uuid short_url_id FK
        bigint total_clicks
        timestamp last_accessed_at
    }

    CLICK_EVENTS {
        uuid id PK
        uuid short_url_id FK
        timestamp clicked_at
        string ip_address
    }
```

Schema detail: [database-design.md](database-design.md)

---

## Deliberate non-goals

| Item | Rationale |
|------|-----------|
| Kubernetes manifests | Compose-first local/demo deployment; K8s left to consumer environments |
| Spring Session Redis | In-memory sessions sufficient for single-instance web; document sticky-session requirement |
| Runtime role assignment API | Two fixed roles; set at registration/bootstrap only |
| Time-series analytics API | Aggregate + recent clicks cover v1; rollups add scope without demo value |
| Email verification / OAuth | Out of scope for architecture demo |
| HTTPS in repository | Terminate TLS at load balancer or reverse proxy |

---

## Known intentional limitations

| Limitation | Mitigation |
|------------|------------|
| Rate limit fail-open for non-auth paths | Availability over strict throttling; auth paths fail closed |
| JWT role snapshot until refresh | Acceptable for two-role model; refresh reloads from DB |
| Soft-deleted emails block re-registration | Unique email constraint is global by design |
| Click events grow unbounded | Retention/purge job is future ops work; document in security review |

---

## Related documents

- [api-inventory.md](api-inventory.md) — endpoints
- [database-design.md](database-design.md) — schema
- [deployment.md](deployment.md) — production checklist
- [docker.md](docker.md) — Compose guide
- [security-review.md](security-review.md) — threat analysis
- [production-readiness-audit.md](production-readiness-audit.md) — audit status
- [adr/](adr/) — decision records
