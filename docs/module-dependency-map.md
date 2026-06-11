# LinkFlow Module Dependency Map

Maven compile dependencies, runtime wiring, and infrastructure dependencies.

---

## Maven module graph

```mermaid
flowchart TD
    parent["linkflow-parent\n(pom.xml)"]

    common["linkflow-common"]
    auth["linkflow-auth"]
    user["linkflow-user"]
    url["linkflow-url"]
    rl["linkflow-rate-limit"]
    ana["linkflow-analytics"]
    obs["linkflow-observability"]
    app["linkflow-app"]
    gw["linkflow-gateway"]
    web["linkflow-web"]

    parent --> common & auth & user & url & rl & ana & obs & app & gw & web

    auth --> common
    user --> common
    url --> common
    rl --> common
    ana --> common
    obs --> common

    app --> auth & user & url & rl & ana & obs
    app --> pg["postgresql\n(flyway, springdoc)"]

    gw --> cloud["spring-cloud-starter-gateway"]
    web --> webdeps["spring-boot-starter-web\nthymeleaf, security"]
```

**Rule:** Feature modules never depend on each other — only `linkflow-common`.

---

## Module responsibilities and artifacts

| Module | Packaging | Runnable | Depends on |
|--------|-----------|----------|------------|
| `linkflow-common` | jar | No | Spring Web, JPA, Redis, Security, Logstash encoder |
| `linkflow-auth` | jar | No | common, Security, JPA, jjwt |
| `linkflow-user` | jar | No | common, JPA, Security |
| `linkflow-url` | jar | No | common, JPA, Security, ZXing, Caffeine |
| `linkflow-rate-limit` | jar | No | common, Redis, Security |
| `linkflow-analytics` | jar | No | common, JPA, Security |
| `linkflow-observability` | jar | No | common, Actuator, Prometheus registry |
| `linkflow-app` | jar (boot) | Yes :8081 | all feature modules + PostgreSQL driver + Flyway + springdoc |
| `linkflow-gateway` | jar (boot) | Yes :8080 | Spring Cloud Gateway + Actuator |
| `linkflow-web` | jar (boot) | Yes :8082 | Web, Thymeleaf, Security (no com.linkflow deps) |

---

## Runtime dependency graph

```mermaid
flowchart LR
    Browser --> Web["linkflow-web:8082"]
    Client --> GW["linkflow-gateway:8080"]
    Web --> GW
    GW --> App["linkflow-app:8081"]
    App --> PG[(PostgreSQL)]
    App --> Redis[(Redis)]
    Prom[Prometheus:9090] --> App
    Prom --> GW
    Graf[Grafana:3000] --> Prom
```

---

## Package-level port wiring (cross-module)

Ports defined in `linkflow-common`, implemented in feature modules, consumed at runtime by `linkflow-app` classpath:

```mermaid
flowchart LR
    subgraph common["linkflow-common.port"]
        ULP[UserLookupPort]
        CTP[ClickTrackingPort]
    end

    subgraph auth["linkflow-auth"]
        AS[AuthService]
    end

    subgraph user["linkflow-user"]
        ULA[UserLookupAdapter]
    end

    subgraph url["linkflow-url"]
        RS[RedirectService]
    end

    subgraph ana["linkflow-analytics"]
        CTA[ClickTrackingAdapter]
        CTS[ClickTrackingService]
    end

    AS --> ULP
    ULA -.implements.-> ULP
    RS --> CTP
    CTA -.implements.-> CTP
    CTA --> CTS
```

---

## Spring component scan boundaries

| Application | Scan base | JPA entities/repos |
|-------------|-----------|-------------------|
| `LinkFlowApplication` | `com.linkflow` | `com.linkflow` |
| `LinkFlowGatewayApplication` | `com.linkflow.gateway` | None |
| `LinkFlowWebApplication` | `com.linkflow.web` | None |

---

## External runtime dependencies

| Dependency | Version (Compose) | Used by |
|------------|-------------------|---------|
| PostgreSQL | 16-alpine | linkflow-app |
| Redis | 7-alpine | linkflow-app |
| Prometheus | v2.54.1 | Scrapes app + gateway |
| Grafana | 11.2.0 | Dashboards |
| Eclipse Temurin | 21 (Dockerfiles) | Build/runtime |

---

## CI dependencies

`.github/workflows/ci.yml` — Maven verify with `LINKFLOW_JWT_SECRET` set for builds.

---

## Related documents

- [system-design.md](system-design.md)
- [adr/001-modular-monolith.md](adr/001-modular-monolith.md)
