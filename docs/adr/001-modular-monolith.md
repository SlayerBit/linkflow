# ADR-001: Modular Monolith Architecture

## Status

Accepted

## Context

LinkFlow needs URL shortening, authentication, analytics, rate limiting, and admin features. The team must balance development velocity, operational simplicity, and clear module boundaries.

## Problem

How should we structure the backend to support multiple domains without premature microservice complexity?

## Decision

Implement a **modular monolith**: feature modules (`linkflow-auth`, `linkflow-user`, `linkflow-url`, `linkflow-rate-limit`, `linkflow-analytics`, `linkflow-observability`) compile as JARs and are assembled by a single runnable `linkflow-app`.

**Rules:**

- Feature modules depend only on `linkflow-common`
- Cross-module calls use port interfaces (`UserLookupPort`, `ClickTrackingPort`)
- One PostgreSQL database, one deployment unit for business logic

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Microservices per domain | Higher ops cost, distributed transactions, no scale trigger yet |
| Single package monolith | No compile-time boundary enforcement |
| Shared library without ports | Tight coupling between auth and user modules |

## Consequences

**Positive:** Simple deployment, ACID transactions across domains, clear Maven boundaries, easier local dev.

**Negative:** Cannot scale modules independently; all modules share JVM failure blast radius; database remains single point of contention.

## References

- `pom.xml` module list
- [system-design.md](../system-design.md)
- [module-dependency-map.md](../module-dependency-map.md)
