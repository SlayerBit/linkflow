# ADR-005: Spring Cloud Gateway as API Entry Point

## Status

Accepted

## Context

Clients need a single public URL for the API, redirects, Swagger, and the web UI. Cross-cutting concerns like correlation IDs should not clutter business controllers.

## Problem

Should clients call `linkflow-app` directly or through a gateway?

## Decision

Deploy **Spring Cloud Gateway** (`linkflow-gateway`) on port **8080** proxying to:

- **`linkflow-app` (8081):** `/api/**`, `/r/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- **`linkflow-web` (8082):** `/css/**`, `/js/**`, `/webjars/**`, and catch-all `/**`

Global filter: `CorrelationIdGatewayFilter` for `X-Correlation-ID`.

**App actuator is not proxied.** Prometheus scrapes `linkflow-app:8081/actuator/prometheus` directly. Gateway `/actuator/**` serves gateway health only.

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Direct app access only | No edge routing layer; harder to add TLS/routing later |
| Nginx only | Less Spring-native; gateway demo value for Java interviews |
| Gateway with auth | JWT validation kept in app for single source of truth |
| Proxy app actuator through gateway | Couples edge health to backend; Prometheus already scrapes app directly |

## Consequences

**Positive:** Clean separation, independent gateway scaling, correlation ID injection, single browser entry at `:8080`, models production topology.

**Negative:** Extra hop latency; another service to deploy/monitor; route ordering matters (API paths before web catch-all).

## References

- `linkflow-gateway/src/main/resources/application.yml`
- `CorrelationIdGatewayFilter`
- `linkflow-gateway/src/test/java/com/linkflow/gateway/GatewayRoutingIT.java`
- [system-design.md](../system-design.md)
