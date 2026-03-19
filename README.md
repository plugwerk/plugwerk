# PlugWerk

**Plugin Marketplace for the Java/PF4J Ecosystem**

PlugWerk is the missing link between [PF4J](https://github.com/pf4j/pf4j) and a product's plugin ecosystem. It provides centralized infrastructure for distributing, managing, and updating plugins at runtime – comparable to Maven Central for build dependencies, but designed for runtime plugins.

## Overview

PlugWerk consists of two artifacts:

- **PlugWerk Server** – A Spring Boot web application serving as a central plugin marketplace: catalog, upload, versioning, download, and REST API.
- **PlugWerk Client SDK** – A pure Java 11+ library that can be embedded into any Java application: plugin discovery, download, installation, update checking, and PF4J lifecycle integration.

## Status

> This project is currently in the **concept and planning phase**. See [`docs/concepts/`](docs/concepts/) for the full product concept.

## Key Features

- Searchable plugin catalog with full-text search and compatibility filtering
- Plugin upload via Web UI or REST API (CI/CD-ready)
- SemVer-based versioning with compatibility matrix (`requires: >=2.0.0 & <4.0.0`)
- SHA-256 checksum verification and optional code signing
- Drop-in replacement for [`pf4j-update`](https://github.com/pf4j/pf4j-update) – backward compatible `plugins.json` endpoint
- Multi-namespace support: one server serves multiple products/organizations
- Self-hosted (Docker Compose / Kubernetes) or SaaS

## Architecture

```
PlugWerk Server  ←──REST/HTTPS──  PlugWerk Client SDK
  ├── REST API                       ├── PlugWerkUpdateRepository  (pf4j-update drop-in)
  ├── Web UI                         ├── PlugWerkInstaller
  ├── Service Layer                  └── PlugWerkUpdateChecker
  ├── PostgreSQL
  ├── S3/Filesystem (artifacts)
  └── Redis (cache)
```

**Tech stack:** Spring Boot 3.x / Java 21+ · PostgreSQL · React/TypeScript · Spring Security + OIDC · Gradle multi-module

## Documentation

- [Product Concept (EN)](docs/concepts/concept-pf4j-marketplace-en.md)
- [Produktkonzept (DE)](docs/concepts/concept-pf4j-marketplace-de.md)
- [Architecture Decision Records](docs/adrs/)

## Contributing

- Language: **English** for all code, docs, issues, and reviews
- Branches: `feature/<issue-id>_<short-description>` – never commit directly to `main`
- Commits: [Conventional Commits](https://www.conventionalcommits.org/) format
- Use the issue and PR templates in `.github/`
