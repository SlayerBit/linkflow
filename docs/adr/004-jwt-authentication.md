# ADR-004: JWT Access Tokens with Opaque Refresh Tokens

## Status

Accepted

## Context

The API must authenticate stateless requests suitable for horizontal scaling while supporting session extension without re-login.

## Problem

How should clients authenticate to the REST API?

## Decision

- **Access tokens:** JWT (HMAC-SHA512) via `JwtService`, 15-minute default TTL, claims include userId, email, roles
- **Refresh tokens:** Opaque random strings, SHA-256 hash in `refresh_tokens`, 30-day TTL, rotation on refresh
- **Validation:** `JwtAuthenticationFilter` on every request except public paths
- **Passwords:** BCrypt strength 12

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Session cookies on API | Conflicts with stateless scaling; CSRF complexity for SPA/API clients |
| JWT-only (long-lived) | Cannot revoke without blocklist; larger theft window |
| OAuth2 provider | Out of scope for self-contained demo/product |

## Consequences

**Positive:** Stateless API scaling, revocable refresh tokens, rotation detects reuse.

**Negative:** Secret management critical; JWT cannot be revoked until expiry without extra infrastructure; clock skew considerations.

## References

- `JwtService`, `RefreshTokenService`, `SecurityConfig`
- [security-review.md](../security-review.md)
