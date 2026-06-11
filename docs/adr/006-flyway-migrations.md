# ADR-006: Flyway for Schema Migrations

## Status

Accepted

## Context

Multiple modules share one PostgreSQL schema. Schema changes must be versioned, repeatable, and validated against JPA entities.

## Problem

How should database schema evolution be managed?

## Decision

Use **Flyway** in `linkflow-app` with migrations in `classpath:db/migration/`. Hibernate `ddl-auto: validate` — no auto DDL.

Current versions: V1 (users/roles) through V6 (url_analytics audit columns).

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Hibernate `ddl-auto: update` | Unsafe in production; non-reviewable changes |
| Liquibase | Flyway sufficient; SQL migrations match team preference |
| Per-module migrations | Single database; one migration owner avoids ordering conflicts |

## Consequences

**Positive:** Reviewable SQL, reproducible environments, CI/Testcontainers use same migrations.

**Negative:** All schema changes funnel through `linkflow-app`; modules cannot migrate independently.

## References

- `linkflow-app/src/main/resources/db/migration/`
- [database-design.md](../database-design.md)
