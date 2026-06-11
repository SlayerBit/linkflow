# ADR-002: PostgreSQL as Primary Datastore

## Status

Accepted

## Context

LinkFlow stores users, roles, refresh tokens, short URLs, idempotency records, and analytics with relational integrity.

## Problem

Which primary database should back the system?

## Decision

Use **PostgreSQL 16** with **Flyway** migrations owned by `linkflow-app`. Hibernate `ddl-auto: validate`.

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| MongoDB | Weak FK integrity for users/tokens/URLs; no Flyway-style migrations in use |
| MySQL | PostgreSQL chosen for JSON support, UUID defaults, partial indexes |
| H2 for prod | Not durable; Testcontainers uses real Postgres in ITs |

## Consequences

**Positive:** ACID, FK constraints, partial indexes (e.g. `idx_users_email WHERE deleted = FALSE`), mature ops tooling.

**Negative:** Vertical scaling limits; schema migrations require Flyway discipline; connection pool tuning needed at scale.

## References

- `linkflow-app/src/main/resources/db/migration/V1–V6`
- [database-design.md](../database-design.md)
