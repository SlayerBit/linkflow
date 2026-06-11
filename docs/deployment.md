# Deployment Guide

> Audit status: [production-readiness-audit.md](production-readiness-audit.md)  
> Canonical architecture: [system-design.md](system-design.md)

## Production checklist

- [ ] Set strong `LINKFLOW_JWT_SECRET` (Base64-encoded, ≥32 decoded bytes)
- [ ] Use `spring.profiles.active=prod`
- [ ] Configure PostgreSQL and Redis with persistence and backups
- [ ] Set `LINKFLOW_BASE_URL` to public domain (e.g. `https://links.example.com`)
- [ ] Keep `LINKFLOW_SECURITY_SWAGGER_PUBLIC=false` and `LINKFLOW_SECURITY_ACTUATOR_PUBLIC=false`
- [ ] Set `LINKFLOW_METRICS_PUBLIC=true` only if Prometheus scrapes metrics without auth on a trusted network
- [ ] Tighten `LINKFLOW_CORS_ALLOWED_ORIGINS` to explicit origins
- [ ] Enable HTTPS at load balancer or reverse proxy
- [ ] Change default Grafana/DB credentials in Compose before any shared environment
- [ ] Deploy all three processes (gateway, app, web) or route web separately — see [docker.md](docker.md)

## Docker Compose (recommended local and demo deployment)

```bash
cp .env.example .env   # set LINKFLOW_JWT_SECRET
docker compose up -d --build
```

**Public entry:** `http://localhost:8080` (gateway — web UI + API + redirects)

| Service | Host port | Role |
|---------|-----------|------|
| linkflow-gateway | 8080 | Public entry point |
| linkflow-web | 8082 | Web UI (also reachable via gateway `/`) |
| linkflow-app | 8081 | Backend API (direct access for debugging) |
| postgres | 5432 | Database |
| redis | 6379 | Cache / rate limits |
| prometheus | 9090 | Metrics |
| grafana | 3000 | Dashboards |

## Process layout (non-Docker)

Run three JARs with `dev` profile locally:

```bash
java -jar linkflow-app/target/linkflow-app-*.jar --spring.profiles.active=dev
java -jar linkflow-gateway/target/linkflow-gateway-*.jar
java -jar linkflow-web/target/linkflow-web-*.jar
```

Set `LINKFLOW_WEB_URI=http://127.0.0.1:8082` on the gateway for single-host UX.

## Kubernetes — deliberate non-goal

This repository is **Compose-first**. No Kubernetes manifests are maintained here.

If you deploy to Kubernetes, you will need at minimum:

1. Managed PostgreSQL and Redis
2. `linkflow-app` Deployment + ClusterIP Service (8081)
3. `linkflow-web` Deployment + ClusterIP Service (8082)
4. `linkflow-gateway` Deployment + Ingress (8080, TLS termination)
5. Secrets for JWT and database credentials
6. Prometheus Operator or managed observability scraping app/gateway metrics on internal networks

See [system-design.md](system-design.md) for topology diagrams.

## Health checks

| Target | URL | Notes |
|--------|-----|-------|
| Gateway (public) | `GET :8080/actuator/health` | Gateway-local health |
| Backend (internal) | `GET :8081/actuator/health` | Public in prod; details hidden |
| Web | `GET :8082/actuator/health` | Health only |
| Metrics | `GET :8081/actuator/prometheus` | Requires `LINKFLOW_METRICS_PUBLIC=true` in prod |

## Related

- [docker.md](docker.md)
- [security-review.md](security-review.md)
