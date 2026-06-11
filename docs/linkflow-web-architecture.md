# linkflow-web Architecture

> **Status:** `linkflow-web` is **implemented** (port 8082). This document originated as a Phase 1 design plan; the backend API surface and web module structure below were verified against source. For canonical architecture, see [system-design.md](system-design.md). For runtime walkthrough, see [code-walkthrough.md](code-walkthrough.md).

## 1. Repository Scan Summary

### Architecture Confirmed

LinkFlow is a **modular monolith** with two independently-runnable Spring Boot processes:

| Process | Port | Role |
|---|---|---|
| `linkflow-gateway` | 8080 | Spring Cloud Gateway — single public entry point, routes `/api/**`, `/r/**`, `/actuator/**`, `/swagger-ui/**` to linkflow-app |
| `linkflow-app` | 8081 | Main backend — all feature modules wired together |

**`linkflow-web` will run as a third Spring Boot process on port `:8082`, routing API calls through the gateway at `:8080`.**

---

### Backend API Surface (confirmed from source)

All endpoints were verified directly from controllers, not assumed.

#### Auth — `/api/v1/auth/**` (public)
| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Register. Body: `{email, password, firstName, lastName}` → `RegisterResponse{id, email, firstName, lastName, roles[], createdAt}` |
| POST | `/api/v1/auth/login` | Login. Body: `{email, password}` → `TokenResponse{accessToken, refreshToken, tokenType, expiresIn}` |
| POST | `/api/v1/auth/refresh` | Rotate token pair. Body: `{refreshToken}` → new `TokenResponse` |
| POST | `/api/v1/auth/logout` | Revoke. Body: `{refreshToken}` (also needs Bearer header) |

#### User — `/api/v1/users/**` (authenticated)
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/users/me` | Get current user `UserResponse{id, email, firstName, lastName, roles[], createdAt, updatedAt}` |
| PUT | `/api/v1/users/me` | Update profile. Body: `{firstName, lastName}` |

#### Admin Users — `/api/v1/admin/users/**` (ROLE_ADMIN)
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/admin/users?page&size&sortBy&direction` | Paginated user list |
| GET | `/api/v1/admin/users/{id}` | Single user by UUID |

#### URLs — `/api/v1/urls/**` (authenticated)
| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/urls` | Create short URL. Body: `{originalUrl, customAlias?, expiresAt?}` + optional `Idempotency-Key` header → `UrlResponse{id, shortCode, shortUrl, originalUrl, expiresAt, active, createdAt}` |
| POST | `/api/v1/urls/bulk` | Bulk create. Body: `{urls: [{originalUrl, customAlias?, expiresAt?}]}` + required `Idempotency-Key` |
| GET | `/api/v1/urls?page&size&sortBy&direction` | Paginated list of user's URLs |
| GET | `/api/v1/urls/{id}` | Single URL details |
| PATCH | `/api/v1/urls/{id}` | Update. Body: `{expiresAt?, active?}` |
| DELETE | `/api/v1/urls/{id}` | Soft-delete |
| GET | `/api/v1/urls/{id}/qr` | QR code as `image/png` |

#### Admin URLs — `/api/v1/admin/urls/**` (ROLE_ADMIN)
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/admin/urls?page&size&sortBy&direction` | All URLs paginated |
| PATCH | `/api/v1/admin/urls/{id}/deactivate` | Admin force-deactivate |

#### Analytics — `/api/v1/**` (authenticated / ROLE_ADMIN)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/urls/{id}/analytics` | Bearer | Per-URL analytics: `{shortUrlId, shortCode, totalClicks, lastAccessedAt}` |
| GET | `/api/v1/analytics/top?limit=10` | Bearer | User's top URLs by clicks: `[{shortUrlId, shortCode, totalClicks}]` |
| GET | `/api/v1/admin/analytics/top?limit=10` | ADMIN | System-wide top URLs |
| GET | `/api/v1/admin/analytics/stats` | ADMIN | System stats: `{totalUsers, totalUrls, totalClicks, activeUrls, expiredUrls, deletedUrls}` |

#### Redirect — `/r/**` (public)
| Method | Path | Description |
|---|---|---|
| GET | `/r/{shortCode}` | 302 redirect to original URL (handled by backend; frontend does not intercept) |

#### Actuator — `/actuator/**` (public per SecurityConfig)
| Endpoint | Description |
|---|---|
| `/actuator/health` | Health with Redis custom indicator |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/prometheus` | Prometheus scrape endpoint |

#### Rate Limiting (servlet filter, not a controller endpoint)
- Applied to all requests via `RateLimitFilter` (after JWT filter)
- Response headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- Authenticated users: 100 req/min (configurable via `linkflow.rate-limit.user-rpm`)
- Anonymous IPs: 200 req/min (configurable via `linkflow.rate-limit.ip-rpm`)
- Exceeds limit → HTTP 429 with error JSON
- Bypassed for: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`

---

### Key DTOs (confirmed field names)

**`ApiResponse<T>`** wrapper on all success responses:
```json
{ "success": true, "timestamp": "...", "correlationId": "...", "data": { ... } }
```

**`PagedResponse<T>`** for list endpoints:
```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 150, "totalPages": 8 }
```

**`ApiErrorResponse`** on errors:
```json
{ "success": false, "timestamp": "...", "correlationId": "...", "errorCode": "...", "message": "...", "details": [...] }
```

---

### Features NOT Present in the Backend (reported, not invented)

| Requested Feature | Status |
|---|---|
| Admin: get user by ID with URLs | ❌ Only `GET /api/v1/admin/users/{id}` (user profile only) — no per-user URL listing endpoint for admin |
| Click-over-time chart data (time series) | ❌ Only aggregate `totalClicks + lastAccessedAt` per URL; no time-bucketed series endpoint |
| Admin user enable/disable | ❌ No such endpoint exists |
| Admin user delete | ❌ No such endpoint exists |
| QR code customization | ❌ QR endpoint takes no parameters — backend returns PNG for given URL ID |
| Pagination params on bulk create | N/A — bulk create exists, no pagination |
| Individual click event listing | ❌ `click_events` table exists but no API endpoint exposes it |

---

## 2. Frontend Architecture Proposal

### Deployment Model

```
Browser
  └── linkflow-web (:8082)          ← This new module
        ├── Thymeleaf SSR pages
        ├── Session-stored tokens    ← server-side only, never in browser
        └── RestClient calls ──────→ linkflow-gateway (:8080)
                                          └── linkflow-app (:8081)
```

`linkflow-web` is a pure consumer. It has **zero** business logic. It calls the gateway's REST API, renders HTML responses via Thymeleaf, and manages auth state in the server-side HTTP session.

### Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Spring Boot 3.4.1, Java 21 |
| View engine | Thymeleaf 3.x + Spring Security dialect + Layout dialect |
| CSS/UI framework | Tabler 1.0.0 (via CDN — no build toolchain) |
| Base CSS | Bootstrap 5.3 (bundled with Tabler) |
| Charts | Chart.js 4.x (via CDN, analytics pages only) |
| Icons | Tabler Icons (bundled) |
| HTTP client | Spring `RestClient` (synchronous, Spring Boot 3.2+) |
| Session | Spring Session (in-memory for dev; Redis-backed for prod optionally) |
| Security | Spring Security (form-login-style redirects, session-based) |
| Server port | 8082 |

### Why `RestClient` over `WebClient`?
`linkflow-web` uses Servlet stack (Spring MVC + Thymeleaf). `RestClient` is the idiomatic synchronous HTTP client for Spring Boot 3.2+, fits perfectly with blocking Thymeleaf rendering, and avoids pulling in a reactive stack.

---

## 3. Auth / Session Strategy

### Design

The frontend is a server-side rendered app. It **must not** store JWTs in browser `localStorage` or cookies directly.

**Session flow:**
1. User submits login form → `linkflow-web` POSTs to `/api/v1/auth/login` via `RestClient`
2. Backend returns `TokenResponse{accessToken, refreshToken, expiresIn}`
3. `linkflow-web` stores **both tokens in the HTTP session** (`HttpSession`), never exposes them to the browser
4. Every subsequent API call from the web layer reads `accessToken` from session, adds `Authorization: Bearer <token>` header
5. On 401 from backend → web layer automatically calls `/api/v1/auth/refresh` with the stored `refreshToken`
6. If refresh succeeds → update session, retry original request
7. If refresh fails → clear session, redirect to `/login`
8. Logout form → `linkflow-web` calls `/api/v1/auth/logout` with stored refresh token, then invalidates session

**Session object stored:**
```java
// stored in HttpSession under key "AUTH_STATE"
record AuthState(
    String accessToken,
    String refreshToken,
    long   expiresAt,        // Epoch seconds: now + expiresIn
    String email,
    String firstName,
    String roles             // comma-separated for easy Thymeleaf use
) {}
```

**Spring Security in linkflow-web:**
- No JWT filter — the web module does not validate JWTs itself
- Uses a simple `SecurityFilterChain` that protects `/dashboard/**`, `/urls/**`, `/admin/**`, `/profile/**` via session
- A custom `WebAuthFilter` (OncePerRequestFilter) checks if the session contains valid `AuthState`; if not, redirects to `/login`
- Admins are distinguished by checking `AuthState.roles.contains("ADMIN")`
- Thymeleaf templates check `${session.auth.isAdmin}` for conditional rendering

**Security properties:**
- `HttpSession` timeout: 30 minutes (configurable)
- `SameSite=Strict` cookie attribute
- CSRF enabled (Spring Security default for form submissions)

---

## 4. API Client Strategy

### Structure

```
com.linkflow.web.client
├── BackendClient.java            ← RestClient factory, base URL, auth header injection
├── AuthApiClient.java            ← login, register, refresh, logout calls
├── UserApiClient.java            ← /api/v1/users/me, PUT /me, admin/users
├── UrlApiClient.java             ← all /api/v1/urls/** endpoints
├── AnalyticsApiClient.java       ← /api/v1/urls/{id}/analytics, /analytics/top, admin/analytics
└── ActuatorApiClient.java        ← /actuator/health, /actuator/metrics (for admin status page)
```

**Token propagation:**
Each client method receives the current `AuthState` (fetched from session by the calling controller) and passes `Authorization: Bearer {accessToken}` to `RestClient`.

**Token refresh interceptor:**
A shared `ApiCallHelper` wraps the try-call-refresh-retry pattern:
```java
<T> T withTokenRefresh(AuthState state, HttpSession session,
                        Supplier<T> call, Supplier<T> callAfterRefresh)
```
On `HttpClientErrorException` with status 401, it calls the refresh endpoint, updates the session, then retries once. On second 401 or refresh failure, clears session and throws a redirect exception.

**Error handling:**
Clients parse `ApiErrorResponse` from 4xx/5xx and surface the `message` field to the Thymeleaf model so pages can render inline alerts.

**DTOs in linkflow-web:**
Define local Java records mirroring the backend JSON shapes (no dependency on backend modules — `linkflow-web` is a pure REST client, not in the same Spring application context as `linkflow-app`).

---

## 5. Tabler Integration Strategy

### Approach
Tabler is loaded via CDN — no npm, no webpack, no build step. All Thymeleaf templates extend a base layout fragment that includes:
```html
<!-- Tabler CSS -->
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@tabler/core@1.0.0/dist/css/tabler.min.css">
<!-- Tabler Icons -->
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@tabler/icons-webfont@3.27.0/dist/tabler-icons.min.css">
<!-- Tabler JS -->
<script src="https://cdn.jsdelivr.net/npm/@tabler/core@1.0.0/dist/js/tabler.min.js"></script>
<!-- Chart.js (analytics pages only, loaded per-page) -->
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
```

A small `custom.css` in `src/main/resources/static/css/` overrides brand colors and adds minor tweaks.

### Layout Components

**Fragment structure:**
```
templates/
├── layout/
│   ├── base.html                 ← HTML shell, CDN imports, navbar
│   ├── sidebar-user.html         ← User nav sidebar fragment
│   ├── sidebar-admin.html        ← Admin nav sidebar fragment
│   └── fragments.html            ← header, breadcrumb, alert, pagination
├── public/
│   ├── index.html                ← Landing page
│   ├── login.html
│   └── register.html
├── user/
│   ├── dashboard.html
│   ├── urls.html
│   ├── url-new.html
│   ├── url-detail.html
│   ├── url-edit.html
│   ├── url-analytics.html
│   └── profile.html
├── admin/
│   ├── dashboard.html
│   ├── users.html
│   ├── user-detail.html
│   ├── urls.html
│   ├── analytics.html
│   └── system.html
└── tools/
    ├── rate-limit.html
    └── system-health.html
```

**Thymeleaf Layout Dialect** (`thymeleaf-layout-dialect`) provides `layout:decorate` for base layout inheritance.

### Tabler components used

| Feature | Tabler Component |
|---|---|
| Page wrapper | `.page`, `.page-wrapper`, `.page-body` |
| Sidebar | `.navbar-vertical`, `.nav-item`, `.nav-link` |
| Top bar | `.navbar`, `.navbar-brand` |
| Stats cards | `.card`, `.card-stamp`, `.subheader` |
| Tables | `.table`, `.table-responsive`, `.badge` |
| Forms | `.form-control`, `.form-label`, `.input-group` |
| Alerts | `.alert`, `.alert-danger`, `.alert-success` |
| Pagination | `.pagination`, `.page-item`, `.page-link` |
| Modals | `.modal`, `.modal-dialog` (for delete confirmations) |
| Empty states | `.empty`, `.empty-icon`, `.empty-title` |
| Loading | `.placeholder`, `.placeholder-glow` |
| Breadcrumbs | `.page-pretitle`, `.page-title` |
| Badges | `.badge`, `.bg-green`, `.bg-red` |
| Charts | Chart.js inside `.card-body` |

---

## 6. Page Map

### Public Routes (no auth required)

| Route | Page | Key Features |
|---|---|---|
| `GET /` | Landing page | Product pitch, feature bullets, CTA to register/login, how redirect works (explained with copy, not a redirect itself) |
| `GET /login` | Login form | Email + password, error display, link to register |
| `POST /login` | Login handler | Calls backend, stores session, redirects to `/dashboard` |
| `GET /register` | Registration form | All 4 fields, validation display, link to login |
| `POST /register` | Register handler | Calls backend, shows success with login link |

### Authenticated User Routes (role: USER or ADMIN)

| Route | Page | Key Features |
|---|---|---|
| `GET /dashboard` | User dashboard | Welcome card, recent URLs (last 5), top URLs chart (Chart.js bar), quick "create URL" CTA |
| `GET /urls` | My URLs list | Paginated table (shortCode, original, active badge, clicks count, expiry, actions), search/sort |
| `GET /urls/new` | Create URL form | originalUrl, customAlias (optional), expiresAt datetime picker (optional), Idempotency-Key auto-generated |
| `POST /urls/new` | Create handler | Creates URL, redirects to `/urls/{id}` |
| `GET /urls/{id}` | URL detail | Full URL card, QR code image embed, copy-short-url button, analytics summary, edit/delete actions |
| `GET /urls/{id}/edit` | Edit URL form | Edit expiresAt, toggle active, current values pre-filled |
| `POST /urls/{id}/edit` | Edit handler | Calls PATCH, redirects to detail |
| `POST /urls/{id}/delete` | Delete handler | Calls DELETE, redirects to `/urls` with success flash |
| `GET /urls/{id}/analytics` | URL analytics | Total clicks, last accessed, (bar chart of clicks if top-URL data available), short URL info card |
| `GET /profile` | Profile page | Display current user info, edit first/last name form |
| `POST /profile` | Profile update handler | Calls PUT /users/me, redirects with success |
| `POST /logout` | Logout handler | Calls backend logout, invalidates session, redirects to `/` |

### Admin Routes (role: ADMIN only)

| Route | Page | Key Features |
|---|---|---|
| `GET /admin` | Admin dashboard | System stats cards (totalUsers, totalUrls, totalClicks, activeUrls, expiredUrls, deletedUrls), top URLs chart, quick links |
| `GET /admin/users` | User list | Paginated all-users table (email, name, roles badge, createdAt) |
| `GET /admin/users/{id}` | User detail | User profile card (all fields from UserResponse) |
| `GET /admin/urls` | All URLs | Paginated all-urls table + deactivate action button |
| `GET /admin/analytics` | System analytics | System stats + top URLs chart (Chart.js), platform summary cards |
| `GET /admin/system` | System health | Actuator health data, links to Grafana + Prometheus, rate limit config display |

### Engineering / Demo Routes (accessible to any authenticated user)

| Route | Page | Key Features |
|---|---|---|
| `GET /tools/rate-limit` | Rate limiter demo | Explanation panel, "Fire N Requests" button, shows each response status + rate-limit headers in a live table, clearly marks 429s |
| `GET /tools/system-health` | System health | Actuator `/health` response rendered as status cards (DB, Redis), observability links |

---

## 7. Module / Package Structure

```
linkflow-web/
├── pom.xml
└── src/
    └── main/
        ├── java/com/linkflow/web/
        │   ├── LinkFlowWebApplication.java        ← @SpringBootApplication (own package, NOT com.linkflow.app)
        │   ├── client/
        │   │   ├── BackendClient.java              ← RestClient bean factory
        │   │   ├── ApiCallHelper.java              ← token refresh + retry wrapper
        │   │   ├── AuthApiClient.java
        │   │   ├── UserApiClient.java
        │   │   ├── UrlApiClient.java
        │   │   ├── AnalyticsApiClient.java
        │   │   └── ActuatorApiClient.java
        │   ├── dto/
        │   │   ├── auth/
        │   │   │   ├── LoginForm.java              ← form-binding DTO
        │   │   │   ├── RegisterForm.java
        │   │   │   ├── TokenResponse.java          ← mirrors backend
        │   │   │   └── RegisterResponse.java
        │   │   ├── user/
        │   │   │   ├── UserResponse.java
        │   │   │   └── UpdateProfileForm.java
        │   │   ├── url/
        │   │   │   ├── UrlResponse.java
        │   │   │   ├── CreateUrlForm.java
        │   │   │   └── UpdateUrlForm.java
        │   │   ├── analytics/
        │   │   │   ├── UrlAnalyticsResponse.java
        │   │   │   ├── TopUrlResponse.java
        │   │   │   └── SystemStatsResponse.java
        │   │   └── common/
        │   │       ├── ApiResponse.java            ← generic wrapper deserializer
        │   │       ├── PagedResponse.java
        │   │       └── ApiErrorResponse.java
        │   ├── session/
        │   │   └── AuthState.java                  ← session-stored auth record
        │   ├── security/
        │   │   ├── WebSecurityConfig.java          ← form-login-style SecurityFilterChain
        │   │   └── SessionAuthFilter.java          ← checks session, redirects if missing
        │   ├── controller/
        │   │   ├── PublicController.java           ← /, /login, /register GET
        │   │   ├── AuthController.java             ← POST /login, POST /register, POST /logout
        │   │   ├── DashboardController.java        ← GET /dashboard
        │   │   ├── UrlController.java              ← /urls/**
        │   │   ├── ProfileController.java          ← /profile, POST /profile
        │   │   ├── AdminController.java            ← /admin/**
        │   │   └── ToolsController.java            ← /tools/**
        │   └── config/
        │       └── WebClientConfig.java            ← RestClient bean with base URL
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            ├── static/
            │   ├── css/
            │   │   └── custom.css
            │   └── js/
            │       └── app.js                      ← minimal: copy-to-clipboard, rate-limit demo
            └── templates/
                ├── layout/
                │   ├── base.html
                │   ├── sidebar-user.html
                │   └── sidebar-admin.html
                ├── fragments/
                │   └── alerts.html
                ├── public/
                │   ├── index.html
                │   ├── login.html
                │   └── register.html
                ├── user/
                │   ├── dashboard.html
                │   ├── urls.html
                │   ├── url-new.html
                │   ├── url-detail.html
                │   ├── url-edit.html
                │   ├── url-analytics.html
                │   └── profile.html
                ├── admin/
                │   ├── dashboard.html
                │   ├── users.html
                │   ├── user-detail.html
                │   ├── urls.html
                │   ├── analytics.html
                │   └── system.html
                └── tools/
                    ├── rate-limit.html
                    └── system-health.html
```

---

## 8. Maven Module Setup

### pom.xml for `linkflow-web`

**Key dependencies:**
```xml
<!-- Spring Boot Web (Servlet stack) -->
spring-boot-starter-web

<!-- Thymeleaf -->
spring-boot-starter-thymeleaf
thymeleaf-extras-springsecurity6    ← sec:authorize in templates
nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect  ← layout:decorate

<!-- Spring Security (session-based for the web module) -->
spring-boot-starter-security

<!-- Jackson (for RestClient JSON deserialization) -->
spring-boot-starter-json  (pulled by starter-web)

<!-- Validation (form binding) -->
spring-boot-starter-validation

<!-- Actuator (own health endpoint) -->
spring-boot-starter-actuator

<!-- Lombok -->
lombok (provided)
```

**Important:** `linkflow-web` does **NOT** depend on any other `com.linkflow.*` module. It is a standalone Spring Boot app that talks to the gateway over HTTP.

### Parent pom.xml change
Add `<module>linkflow-web</module>` to the `<modules>` section (after `linkflow-gateway`).

### Port: 8082
The web module runs on a new port to avoid conflict with the gateway (8080) and app (8081).

---

## 9. Feature-to-Backend Mapping

| Frontend Feature | Backend Endpoint(s) | Notes |
|---|---|---|
| Registration | POST `/api/v1/auth/register` | ✅ |
| Login | POST `/api/v1/auth/login` | ✅ |
| Logout | POST `/api/v1/auth/logout` | ✅ |
| Token refresh | POST `/api/v1/auth/refresh` | ✅ (automatic on 401) |
| View profile | GET `/api/v1/users/me` | ✅ |
| Update profile | PUT `/api/v1/users/me` | ✅ (firstName, lastName only — email not updatable) |
| Create URL | POST `/api/v1/urls` | ✅ (with optional customAlias + expiresAt) |
| List my URLs | GET `/api/v1/urls` | ✅ (paginated) |
| URL detail | GET `/api/v1/urls/{id}` | ✅ |
| Edit URL | PATCH `/api/v1/urls/{id}` | ✅ (expiresAt + active toggle) |
| Delete URL | DELETE `/api/v1/urls/{id}` | ✅ (soft-delete) |
| QR code | GET `/api/v1/urls/{id}/qr` | ✅ (PNG proxied through web controller) |
| Per-URL analytics | GET `/api/v1/urls/{id}/analytics` | ✅ |
| Top URLs (user) | GET `/api/v1/analytics/top` | ✅ |
| Admin: user list | GET `/api/v1/admin/users` | ✅ |
| Admin: user detail | GET `/api/v1/admin/users/{id}` | ✅ (profile only) |
| Admin: all URLs | GET `/api/v1/admin/urls` | ✅ |
| Admin: deactivate URL | PATCH `/api/v1/admin/urls/{id}/deactivate` | ✅ |
| Admin: system stats | GET `/api/v1/admin/analytics/stats` | ✅ |
| Admin: top URLs | GET `/api/v1/admin/analytics/top` | ✅ |
| System health | GET `/actuator/health` | ✅ |
| Rate limit demo | Any rate-limited endpoint + header inspection | ✅ (reads `X-RateLimit-*` response headers) |
| Redirect behavior | `GET /r/{shortCode}` passes through gateway | ✅ (explained on landing, not intercepted by web) |
| Observability links | Links to Grafana + Prometheus URLs | ✅ (static links, configurable) |

**Not buildable (no backend support):**
- Per-user URL list in admin view (no endpoint)
- Admin user enable/disable (no endpoint)
- Click-over-time chart (no time-series endpoint)
- Custom QR code styling (no backend params)

---

## 10. Rate Limiter Demo Page Design

**Route:** `GET /tools/rate-limit`

**Page sections:**

1. **Explanation panel**: What rate limiting is, what limits are configured (user: 100 RPM, IP: 200 RPM), how the Lua-backed Redis counter works.

2. **Interactive demo**:
   - Dropdown: select which endpoint to hammer (e.g., `/api/v1/urls` — a protected list endpoint)
   - Number input: how many requests to fire (1–200)
   - "Fire Requests" button
   - JavaScript fires requests sequentially via `fetch()` to a web-layer proxy endpoint (`GET /tools/rate-limit/probe?n=N`) that the `ToolsController` handles — it calls the backend N times and collects each HTTP status code + rate-limit headers

3. **Results table**: Each request row shows: request #, HTTP status, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, color-coded (green = 200, red = 429)

4. **429 callout**: If any 429 received, show an alert with the `message` field from the backend error response.

> **Note:** The demo fires requests **from the server side** (ToolsController → RestClient → gateway → backend), not from the browser, so rate limiting is applied on the authenticated user's bucket (not the browser IP).

---

## 11. Analytics Visualization Design

Chart.js is used **only** on pages where it genuinely adds value:

| Page | Chart Type | Data Source |
|---|---|---|
| `/dashboard` (user) | Horizontal bar chart | `/api/v1/analytics/top?limit=5` — top 5 URLs by clicks |
| `/admin` | Horizontal bar chart | `/api/v1/admin/analytics/top?limit=10` — system-wide top 10 |
| `/admin/analytics` | Doughnut chart | Derived from SystemStatsResponse: active vs expired vs deleted URLs |
| `/urls/{id}/analytics` | Single stat card only (no chart) | `totalClicks + lastAccessedAt` — no time series available |

Chart data is injected from Thymeleaf into `<script>` blocks as JSON (server-rendered, no extra AJAX call needed on page load).

---

## 12. Implementation Plan

### Phase 2A — Scaffold & Foundation (implement first)
1. Create `linkflow-web/pom.xml` with all dependencies
2. Add `<module>linkflow-web</module>` to parent pom
3. Create `LinkFlowWebApplication.java`
4. Create `application.yml` and `application-dev.yml`
5. Create `WebSecurityConfig.java` (session-based security)
6. Create `BackendClient.java` + `ApiCallHelper.java`

### Phase 2B — DTOs & API Clients
7. Create all DTO records (mirrors of backend JSON shapes)
8. Implement `AuthApiClient`, `UserApiClient`, `UrlApiClient`, `AnalyticsApiClient`, `ActuatorApiClient`

### Phase 2C — Layout & Fragments
9. Create `base.html` with Tabler CDN imports, nav structure
10. Create `sidebar-user.html` and `sidebar-admin.html`
11. Create `fragments/alerts.html`
12. Create `custom.css` with brand overrides

### Phase 2D — Auth Pages
13. `PublicController` + `index.html` (landing)
14. Login page + `AuthController` POST /login handler
15. Register page + POST /register handler
16. POST /logout handler

### Phase 2E — User Pages
17. `DashboardController` + `dashboard.html`
18. `UrlController` + `urls.html` (list)
19. `url-new.html` + create handler
20. `url-detail.html` (with QR code proxy endpoint)
21. `url-edit.html` + edit handler + delete handler
22. `url-analytics.html`
23. `ProfileController` + `profile.html`

### Phase 2F — Admin Pages
24. `AdminController` + `admin/dashboard.html`
25. `admin/users.html` + `admin/user-detail.html`
26. `admin/urls.html` (with deactivate action)
27. `admin/analytics.html` (system stats + chart)
28. `admin/system.html` (actuator health + links)

### Phase 2G — Tools / Demo Pages
29. `ToolsController` + `tools/rate-limit.html` (with probe endpoint)
30. `tools/system-health.html`

### Phase 2H — Polish & Verification
31. Consistent error handling (4xx/5xx → alert display)
32. Flash message support (redirect after POST)
33. Pagination components
34. Empty state pages (no URLs yet, no users, etc.)
35. Role-based nav rendering validation
36. Build verification: `mvn clean package -pl linkflow-web -am -DskipTests`
37. Start-up verification

---

## 13. Final decisions (formerly open questions)

| Topic | Decision |
|-------|----------|
| Gateway web proxy | **Yes** — gateway proxies `/`, static assets, and `/**` to `linkflow-web:8082`. Single-host UX at `:8080`. |
| Direct web access | Port `:8082` remains available for debugging; production users should use `:8080`. |
| Web API calls | Web module calls backend through gateway (`linkflow.web.gateway-url`). |
| Session storage | **In-memory HttpSession** — sticky sessions required for horizontal web scaling. Spring Session Redis is a deliberate non-goal for v1. |
| Refresh on 401 | Automatic refresh-and-retry in `ApiCallHelper` — acceptable SSR latency tradeoff. |
| QR proxy | Server-side QR proxy keeps access tokens out of `<img>` URLs. |

Canonical reference: [system-design.md](system-design.md)
