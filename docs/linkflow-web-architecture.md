# linkflow-web Architecture

> **Status:** Implemented (port 8082, included in Docker Compose).  
> **Canonical architecture:** [system-design.md](system-design.md)  
> **Request traces:** [code-walkthrough.md](code-walkthrough.md)

`linkflow-web` is a server-rendered **BFF-style** UI: Thymeleaf pages, Spring Security session auth, and HTTP calls to the backend exclusively through the gateway. It has **zero compile-time dependency** on other `com.linkflow.*` modules.

---

## Process role

| Item | Value |
|------|-------|
| Main class | `com.linkflow.web.LinkFlowWebApplication` |
| Port | 8082 |
| Public entry | Via gateway at `http://localhost:8080` (catch-all `/**` route) |
| Backend calls | `RestClient` → `${LINKFLOW_GATEWAY_URL}` (default `http://127.0.0.1:8080`) |

---

## Authentication model

1. User submits login/register form → `AuthController` (web) → `AuthApiClient` → gateway → `POST /api/v1/auth/*`
2. Tokens stored in **`HttpSession`** as `AuthState` (`SessionKeys.AUTH_STATE`) — access token, refresh token, expiry, email, roles
3. `SessionAuthFilter` reads session and sets Spring Security context for page authorization
4. `BackendClient` / `ApiCallHelper` attach `Authorization: Bearer` on API calls; on **401**, refresh once via `/api/v1/auth/refresh`, update session, retry
5. Logout: revoke refresh token via API, then `session.invalidate()`

**Session config:** 30-minute timeout; cookie `http-only`, `same-site=strict`, `secure` configurable via `SERVER_SERVLET_SESSION_COOKIE_SECURE`.

**Deliberate choice:** JWTs never reach browser JavaScript — only the server-side session holds them. QR codes use `/urls/{id}/qr-proxy` to fetch PNG server-side.

**Deliberate non-goal:** Redis-backed Spring Session — in-memory sessions only; horizontal web scaling requires sticky sessions or a future session store. See [system-design.md](system-design.md#web-session-strategy-final-decision).

---

## Page map (MVC routes)

| Area | Routes | Controller |
|------|--------|------------|
| Public | `/`, `/login`, `/register` | `PublicController`, `AuthController` |
| User | `/dashboard`, `/profile`, `/urls/**` | `DashboardController`, `ProfileController`, `UrlController` |
| Admin | `/admin/**` | `AdminController` |
| Tools | `/tools/rate-limit`, `/tools/system-health` | `ToolsController` |

Admin web UI covers user/URL **listing**, URL deactivation, analytics, and system stats. **Admin user disable/enable/delete** exist in the REST API but are **not** exposed in the web UI (API-only by design for v1).

---

## Gateway routes relevant to web

From `linkflow-gateway/src/main/resources/application.yml` (first match wins):

| Path | Upstream |
|------|----------|
| `/api/**`, `/r/**`, `/swagger-ui/**`, `/v3/api-docs/**` | `linkflow-app:8081` |
| `/css/**`, `/js/**`, `/webjars/**` | `linkflow-web:8082` |
| `/**` | `linkflow-web:8082` |

App actuator is **not** proxied through the gateway.

---

## Key classes

| Class | Role |
|-------|------|
| `WebSecurityConfig` | Session security, CSRF (except rate-limit probe) |
| `SessionAuthFilter` | Session → SecurityContext |
| `SessionManager` | Read/write `AuthState` |
| `AuthApiClient`, `UrlApiClient`, `AnalyticsApiClient`, … | Typed gateway clients |
| `ApiCallHelper` | 401 → refresh → retry |
| `WebClientConfig` | `RestClient` bean (despite the name) |

---

## Deliberate web UI non-goals (v1)

| Feature | Status |
|---------|--------|
| Bulk URL create | API only (`POST /api/v1/urls/bulk`) |
| Admin user disable/enable/delete | API only |
| Client-side SPA / JWT in localStorage | Rejected — SSR + session |
| Redis session replication | Future ops concern |

Full feature map: [feature-matrix.md](feature-matrix.md)
