# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Read `AGENTS.md` first** – it contains the authoritative project conventions (language, git workflow, architecture, documentation structure). This file only adds Claude-specific context.

## Additional Architecture Details

### Core Data Model

```
Namespace (slug, owner_org, settings JSON)
  └── Plugin (plugin_id unique per ns, categories[], tags[], status)
        └── PluginRelease (SemVer version, artifact_sha256, requires_system_version,
                          plugin_dependencies JSON, status: draft/published/deprecated/yanked)

User → Organization → Namespace → Role (RBAC)
```

### API Structure

All endpoints: `/api/v1/namespaces/{ns}/...`

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/plugins` | Catalog with filters |
| `GET` | `/plugins/{id}/releases/{version}/download` | Artifact download |
| `GET` | `/plugins.json` | pf4j-update compatible drop-in |
| `POST` | `/plugins/{id}/releases` | Upload new release |
| `POST` | `/updates/check` | Body: installed plugins+versions → available updates |
| `GET` | `/reviews/pending` | Admin review queue |
| `POST` | `/reviews/{releaseId}/approve` | Approve release |

### Client SDK Core Classes

```java
PlugWerkConfig           // Builder pattern or properties file
PlugWerkClient           // Configurable HTTP client
PlugWerkCatalog          // Catalog queries
PlugWerkInstaller        // Download + SHA-256 verification + transactional install
PlugWerkUpdateChecker    // Update polling
PlugWerkUpdateRepository // Implements pf4j-update UpdateRepository (drop-in replacement)
PlugWerkPluginManager    // Optional: wraps DefaultPluginManager + PlugWerk features
```

### Plugin Descriptor (`plugwerk.yml`)

Embedded in plugin artifacts. Extends the PF4J manifest (`MANIFEST.MF` / `plugin.properties`) with:
- `requires.system-version` – SemVer range (e.g. `>=2.0.0 & <4.0.0`)
- `requires.api-level` – integer (alternative to concrete version)
- `requires.plugins[]` – plugin-to-plugin dependencies with version ranges
- `namespace`, `categories[]`, `tags[]`, `license`, `icon`, `screenshots`

If `plugwerk.yml` is absent, the server falls back to the PF4J manifest.

## Implementation Phases

- **Phase 1 (MVP):** REST API + PostgreSQL + filesystem storage + API key auth + minimal Web UI + `plugins.json` endpoint + Client SDK as pf4j-update drop-in
- **Phase 2 (Enterprise):** Multi-namespace, RBAC/OIDC, review/approval workflow, code signing, S3/MinIO, dependency resolution in client
- **Phase 3 (Ecosystem):** Embeddable UI component, webhooks, vulnerability scanning, Gradle/Maven CI/CD plugin, SaaS multi-tenancy

## Deployment (Self-Hosted Minimal)

```
Docker Compose:
├── plugwerk-server  (Spring Boot)
├── postgres
├── minio            (optional S3 storage)
└── nginx            (reverse proxy, TLS)
```

Health: `/actuator/health` | Metrics: Prometheus | Logging: structured JSON
