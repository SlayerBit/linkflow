# ADR-007: Observability Stack (Actuator, Prometheus, Grafana)

## Status

Accepted

## Context

Production-style systems need health checks, metrics export, and dashboards for operations and interviews.

## Problem

What observability should LinkFlow include?

## Decision

1. **Spring Boot Actuator** on app, gateway, and web — expose health, info, metrics, prometheus (exposure varies by profile)
2. **Micrometer Prometheus registry** via `linkflow-observability`
3. **Business metrics** through a `LinkflowMetrics` port in `linkflow-common` (Micrometer implementation + `EmailDeliveryEvent` listener in observability) — redirects, URL cache, auth, URL creation, rate limits, analytics flush, email delivery
4. **Custom health:** `RedisHealthIndicator` on the backend app
5. **Docker stack:** Prometheus (9090) scrapes app:8081 and gateway:8080; alert rules in `docker/prometheus/alerts.yml`; Grafana (3000) provisioned with the **LinkFlow Overview** dashboard
6. **Structured logging:** Logstash Logback encoder in `linkflow-common`

**Prod profile (app):** Swagger denied; only `/actuator/health` public by default; Prometheus/metrics public when `LINKFLOW_METRICS_PUBLIC=true` (set in Compose demo stack).

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Logs only | No metrics for rate limit, JVM, HTTP latency |
| Managed APM only | Not self-contained for local/demo |
| Actuator authenticated by default | Dev ergonomics; prod profile restricts exposure via `LinkflowSecurityProperties` |
| Micrometer injected into every feature module | Couples domain modules to the metrics library; the common port keeps that dependency in observability |

## Consequences

**Positive:** Standard metrics endpoint, business counters for interview-relevant hot paths, Grafana overview dashboard, Prometheus alert rules, Redis health visibility, profile-gated prod exposure.

**Negative:** Demo Compose enables public app metrics; web module is intentionally not scraped; Alertmanager is not bundled (rules evaluate in Prometheus; notifications would be a follow-up).

## References

- `linkflow-observability/` (`MicrometerLinkflowMetrics`, `EmailDeliveryMetricsListener`)
- `linkflow-common/.../metrics/LinkflowMetrics.java`
- `docker/prometheus/prometheus.yml`, `docker/prometheus/alerts.yml`
- `docker/grafana/provisioning/dashboards/`
- [security-review.md](../security-review.md)
