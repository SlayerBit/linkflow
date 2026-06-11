# API Reference

> **Canonical document:** [api-inventory.md](api-inventory.md) — full endpoint detail with DTOs, validation, and rate limits.

## Quick reference

Base URL (via gateway): `http://localhost:8080`

Standard success envelope (`ApiResponse`):

```json
{
  "success": true,
  "timestamp": "2026-06-12T10:00:00Z",
  "correlationId": "abc-123",
  "data": { }
}
```

Interactive docs: http://localhost:8080/swagger-ui/index.html

## Authentication

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/auth/register` | None |
| POST | `/api/v1/auth/login` | None |
| POST | `/api/v1/auth/refresh` | None |
| POST | `/api/v1/auth/logout` | Bearer |

## Users

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/users/me` | Bearer |
| PUT | `/api/v1/users/me` | Bearer |
| GET | `/api/v1/admin/users` | ADMIN |
| GET | `/api/v1/admin/users/{id}` | ADMIN |

## URLs

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/v1/urls` | Bearer | Optional `Idempotency-Key` |
| POST | `/api/v1/urls/bulk` | Bearer | Required `Idempotency-Key` |
| GET | `/api/v1/urls` | Bearer | Paginated |
| GET | `/api/v1/urls/{id}` | Bearer | Owner |
| PATCH | `/api/v1/urls/{id}` | Bearer | Owner |
| DELETE | `/api/v1/urls/{id}` | Bearer | Soft delete |
| GET | `/api/v1/urls/{id}/qr` | Bearer | PNG |
| GET | `/r/{shortCode}` | None | 302 redirect |

## Analytics

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/urls/{id}/analytics` | Bearer (owner) |
| GET | `/api/v1/analytics/top` | Bearer |
| GET | `/api/v1/admin/analytics/top` | ADMIN |
| GET | `/api/v1/admin/analytics/stats` | ADMIN |

## Rate limit headers

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1717776060
```

See [system-design.md](system-design.md#rate-limiting-design) for behavior.
