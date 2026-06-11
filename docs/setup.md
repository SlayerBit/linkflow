# Setup Guide

> **Detailed runbook:** [LOCAL_SETUP.md](../LOCAL_SETUP.md) — troubleshooting, ports, macOS fixes.

## Prerequisites

- JDK **21** (required)
- Maven 3.9+
- Docker Desktop (PostgreSQL, Redis, integration tests)

## Database setup

Use Docker (recommended):

```bash
docker compose up -d postgres redis
```

Or native PostgreSQL — create database `linkflow` and user `linkflow`.

## Configuration

```bash
cp .env.example .env
export LINKFLOW_JWT_SECRET="$(openssl rand -base64 64)"   # recommended even in dev
```

See [environment.md](environment.md) for all variables.

## Run the stack

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn clean package -DskipTests

# Backend (8081)
java -jar linkflow-app/target/linkflow-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# Gateway (8080) — separate terminal
export LINKFLOW_APP_URI=http://127.0.0.1:8081
java -jar linkflow-gateway/target/linkflow-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# Web UI (8082) — separate terminal
java -jar linkflow-web/target/linkflow-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

## Bootstrap admin user

```bash
export LINKFLOW_BOOTSTRAP_ADMIN_ENABLED=true
export LINKFLOW_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export LINKFLOW_BOOTSTRAP_ADMIN_PASSWORD='StrongP@ss1'
```

Start `linkflow-app` with these env vars set (idempotent).

## Related

- [docker.md](docker.md) — full Compose stack
- [testing.md](testing.md) — verify build
- [system-design.md](system-design.md) — architecture
