# ADR-005: Spring Cloud Gateway as API Entry Point

## Status

Accepted

## Context

Clients need a single URL for API, redirects, actuator, and Swagger. Cross-cutting concerns like correlation IDs should not clutter business controllers.

## Problem

Should clients call `linkflow-app` directly or through a gateway?

## Decision

Deploy **Spring Cloud Gateway** (`linkflow-gateway`) on port **8080** proxying to `linkflow-app` on **8081**.

Routes (YAML-only):

- `/api/**`, `/r/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`

Global filter: `CorrelationIdGatewayFilter` for `X-Correlation-ID`.

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Direct app access only | No edge routing layer; harder to add TLS/routing later |
| Nginx only | Less Spring-native; gateway demo value for Java interviews |
| Gateway with auth | JWT validation kept in app for single source of truth |

## Consequences

**Positive:** Clean separation, independent gateway scaling, correlation ID injection, models production topology.

**Negative:** Extra hop latency; another service to deploy/monitor; gateway has no integration tests in repo.

## References

- `linkflow-gateway/src/main/resources/application.yml`
- `CorrelationIdGatewayFilter`
