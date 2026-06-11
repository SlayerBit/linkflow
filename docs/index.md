# LinkFlow Documentation Index

Start here for a guided path through the repository documentation. Each topic has one canonical document; secondary files link to it rather than duplicating content.

## Canonical documents

| Document | Purpose |
|----------|---------|
| [system-design.md](system-design.md) | **Canonical architecture** — C4 views, data flows, tradeoffs, deployment |
| [api-inventory.md](api-inventory.md) | **Canonical API reference** — every REST endpoint with DTOs and auth |
| [database-design.md](database-design.md) | **Canonical data model** — tables, migrations, ER diagram |
| [security-review.md](security-review.md) | **Canonical security** — auth, JWT lifecycle, threats, mitigations |

## Onboarding and learning

| Document | Purpose |
|----------|---------|
| [code-walkthrough.md](code-walkthrough.md) | Startup → request lifecycle with actual classes |
| [project-deep-dive.md](project-deep-dive.md) | Subsystem narrative for interviews |
| [learning-roadmap.md](learning-roadmap.md) | Dependency-aware learning order |
| [interview-prep.md](interview-prep.md) | Repo-specific Q&A (75+ questions) |
| [feature-matrix.md](feature-matrix.md) | Feature → code mapping |

## Operations

| Document | Purpose |
|----------|---------|
| [setup.md](setup.md) | Local setup (links to [LOCAL_SETUP.md](../LOCAL_SETUP.md)) |
| [environment.md](environment.md) | Environment variable reference |
| [docker.md](docker.md) | Docker Compose guide |
| [deployment.md](deployment.md) | Production checklist and deployment notes |
| [testing.md](testing.md) | Build, unit, and integration test guide |

## Architecture and dependencies

| Document | Purpose |
|----------|---------|
| [module-dependency-map.md](module-dependency-map.md) | Maven, package, and runtime dependencies |
| [production-readiness-audit.md](production-readiness-audit.md) | Gap analysis and prioritized risks |
| [adr/](adr/) | Architecture Decision Records |

## Secondary / legacy pointers

| Document | Status |
|----------|--------|
| [architecture.md](architecture.md) | Summary → see [system-design.md](system-design.md) |
| [api.md](api.md) | Summary → see [api-inventory.md](api-inventory.md) |
| [linkflow-web-architecture.md](linkflow-web-architecture.md) | Web module notes → see [system-design.md](system-design.md) and [project-deep-dive.md](project-deep-dive.md) |
| [../implementation_plan.md](../implementation_plan.md) | **Archival** Phase 1 specification; verify against code before trusting |

## Recommended reading order

1. [README.md](../README.md) — overview and quick start
2. [system-design.md](system-design.md) — how the system fits together
3. [code-walkthrough.md](code-walkthrough.md) — trace a request through code
4. [api-inventory.md](api-inventory.md) + [database-design.md](database-design.md) — contracts and data
5. [security-review.md](security-review.md) — auth and threats
6. [LOCAL_SETUP.md](../LOCAL_SETUP.md) — run it locally

For interview preparation: [project-deep-dive.md](project-deep-dive.md) → [interview-prep.md](interview-prep.md).
