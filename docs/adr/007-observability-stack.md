# ADR-007: Observability Stack (Actuator, Prometheus, Grafana)

## Status

Accepted

## Context

Production-style systems need health checks, metrics export, and dashboards for operations and interviews.

## Problem

What observability should LinkFlow include?

## Decision

1. **Spring Boot Actuator** on app, gateway, and web — expose health, info, metrics, prometheus
2. **Micrometer Prometheus registry** via `linkflow-observability`
3. **Custom health:** `RedisHealthIndicator`
4. **Docker stack:** Prometheus (9090) scrapes app:8081 and gateway:8080; Grafana (3000) provisioned from `docker/grafana/`
5. **Structured logging:** Logstash Logback encoder in `linkflow-common`

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Logs only | No metrics for rate limit, JVM, HTTP latency |
| Managed APM only | Not self-contained for local/demo |
| Actuator authenticated by default | Current code permits public actuator — documented as prod gap |

## Consequences

**Positive:** Standard metrics endpoint, Grafana dashboards in Compose, Redis health visibility.

**Negative:** Public actuator in current security config; no alerting rules in repo; web module metrics not in Prometheus scrape config.

## References

- `linkflow-observability/pom.xml`
- `docker/prometheus/prometheus.yml`
- [production-readiness-audit.md](../production-readiness-audit.md)
