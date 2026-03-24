# Plugwerk

**Plugin Marketplace for the Java/PF4J Ecosystem**

Plugwerk is the missing link between [PF4J](https://github.com/pf4j/pf4j) and a product's plugin ecosystem. It provides centralized infrastructure for distributing, managing, and updating plugins at runtime – comparable to Maven Central for build dependencies, but designed for runtime plugins.

## Overview

Plugwerk consists of two artifacts:

- **Plugwerk Server** – A Spring Boot web application serving as a central plugin marketplace: catalog, upload, versioning, download, and REST API.
- **Plugwerk Client SDK** – A Kotlin library (Java 11+ compatible) deployed as a PF4J plugin with isolated classloader: plugin discovery, download, installation, update checking, and PF4J lifecycle integration.

## Status

> **Phase 1 (MVP) is in active development.** The Gradle multi-module project is scaffolded and building. See [Issue #7](https://github.com/devtank42gmbh/plugwerk/issues/7) for the full task breakdown.

## Key Features

- Searchable plugin catalog with full-text search and compatibility filtering
- Plugin upload via Web UI or REST API (CI/CD-ready)
- SemVer-based versioning with compatibility matrix (`requires: >=2.0.0 & <4.0.0`)
- SHA-256 checksum verification and optional code signing
- Drop-in replacement for [`pf4j-update`](https://github.com/pf4j/pf4j-update) – backward compatible `plugins.json` endpoint
- Multi-namespace support: one server serves multiple products/organizations
- Self-hosted (Docker Compose / Kubernetes) or SaaS

## Module Structure

```
plugwerk/
├── plugwerk-api/                  # OpenAPI 3.1 spec (API-First) + generated DTOs/interfaces
├── plugwerk-spi/                  # Shared ExtensionPoint interfaces, DTOs, constants
├── plugwerk-descriptor/           # plugwerk.yml parser/validator + PF4J manifest fallback
├── plugwerk-server/
│   ├── plugwerk-server-backend/   # Spring Boot 4.x + Kotlin REST API
│   └── plugwerk-server-frontend/  # React + TypeScript + Material UI + Zustand
└── plugwerk-client-plugin/        # PF4J plugin with isolated classloader (OkHttp + Jackson)
```

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Server Backend | Spring Boot 4.x / JVM 21+ |
| Server API | Spring Web (REST) + OpenAPI 3.1 (API-First) |
| Database | PostgreSQL 18 + Liquibase |
| Storage | Filesystem (MVP) / S3-compatible (Phase 2) |
| Web UI | React / TypeScript / Material UI / Zustand |
| Auth | API key (MVP) / Spring Security + OIDC (Phase 2) |
| Client SDK | PF4J plugin / OkHttp / Jackson / JVM 11+ |
| Build | Gradle 9.x multi-module (Kotlin DSL) |
| Tests | JUnit 6 + Mockito + Testcontainers |

## Quick Start (Development)

### Prerequisites

- JDK 21+
- Node.js 20+
- Docker (for PostgreSQL)

### Run

```bash
# Start PostgreSQL
docker compose up -d postgres

# Build all modules
./gradlew build

# Run the server (dev profile)
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun --args='--spring.profiles.active=dev'
```

The server starts at `http://localhost:8080`.

### Docker Compose (full stack)

```bash
docker compose up
```

Starts Plugwerk Server + PostgreSQL 18.

## Documentation

- [Product Concept (EN)](docs/concepts/concept-pf4j-marketplace-en.md)
- [Produktkonzept (DE)](docs/concepts/concept-pf4j-marketplace-de.md)
- [Architecture Decision Records](docs/adrs/)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

- Language: **English** for all code, docs, issues, and reviews
- Branches: `feature/<issue-id>_<short-description>` – never commit directly to `main`
- Commits: [Conventional Commits](https://www.conventionalcommits.org/) format
- Use the issue and PR templates in `.github/`

## License

[AGPL-3.0](LICENSE)
