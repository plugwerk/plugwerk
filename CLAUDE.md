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

All namespace-scoped endpoints: `/api/v1/namespaces/{ns}/...`

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/plugins` | Catalog with filters (status, category, tag, search) |
| `GET` | `/plugins/{id}` | Plugin details with embedded `latestRelease` |
| `GET` | `/plugins/{id}/releases/{version}/download` | Artifact download |
| `GET` | `/plugins.json` | pf4j-update compatible drop-in |
| `POST` | `/plugin-releases` | Upload new release — plugin auto-created from descriptor |
| `PATCH` | `/plugins/{id}/releases/{version}` | Update release status (publish, deprecate, yank) |
| `POST` | `/updates/check` | Body: installed plugins+versions → available updates |
| `GET` | `/reviews/pending` | Admin review queue |
| `POST` | `/reviews/{releaseId}/approve` | Approve release |
| `POST` | `/reviews/{releaseId}/reject` | Reject release |
| `GET` | `/members/me` | Current user's namespace role |
| `GET/POST` | `/members` | List / add namespace members |

Auth & admin endpoints (under `/api/v1/`):

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/auth/login` | Local login → JWT |
| `POST` | `/auth/change-password` | Change own password |
| `GET/POST` | `/admin/users` | User management (superadmin) |
| `GET/POST` | `/admin/oidc-providers` | OIDC provider management |

### Client SDK Core Classes

```kotlin
PlugwerkConfig           // Builder pattern or properties file
PlugwerkClient           // OkHttp-based HTTP client
PlugwerkCatalog          // Catalog queries (ExtensionPoint)
PlugwerkInstaller        // Download + SHA-256 verification + transactional install (ExtensionPoint)
PlugwerkUpdateChecker    // Update polling (ExtensionPoint)
PlugwerkMarketplace      // Facade combining all three (ExtensionPoint)
PlugwerkUpdateRepository // Implements pf4j-update UpdateRepository (drop-in replacement)
```

### Plugin Descriptor (`plugwerk.yml`)

Embedded in plugin artifacts. Extends the PF4J manifest (`MANIFEST.MF` / `plugin.properties`) with:
- `requires.system-version` – SemVer range (e.g. `>=2.0.0 & <4.0.0`)
- `requires.api-level` – integer (alternative to concrete version)
- `requires.plugins[]` – plugin-to-plugin dependencies with version ranges
- `categories[]`, `tags[]`, `license`, `icon`, `screenshots`

If `plugwerk.yml` is absent, the server falls back to the PF4J manifest.

## Build Commands

### Backend (run from repo root)

```bash
./gradlew build                                   # Build all modules + run all tests
./gradlew build -x test                           # Build without tests
./gradlew :plugwerk-api:plugwerk-api-endpoint:openApiGenerate :plugwerk-api:plugwerk-api-model:openApiGenerate  # Regenerate backend DTOs/interfaces from OpenAPI YAML
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun
docker compose up -d postgres                     # Start dev database
```

### Frontend (run from `plugwerk-server/plugwerk-server-frontend/`)

```bash
npm install                  # Install dependencies
npm run dev                  # Vite dev server (port 5173, proxies /api → localhost:8080)
npm run build                # TypeScript check + production build → build/dist/
npm run generate:api         # Regenerate TypeScript client from OpenAPI YAML
npm run test:run             # Vitest single run (CI)
npm run test:coverage        # Vitest with V8 coverage report
npm run lint                 # ESLint
```

**Important**: Frontend tests must be run from `plugwerk-server/plugwerk-server-frontend/` — running
`npx vitest` from the repo root uses the wrong config and picks up unrelated test files.

## Implementation Phases

- **Phase 1 (Core):** REST API, PostgreSQL, filesystem storage, JWT auth, Web UI (React + MUI), `plugins.json` endpoint, Client SDK as pf4j-update drop-in — **completed**
- **Phase 2 (Enterprise):** Multi-namespace, RBAC with namespace roles (ADMIN/MEMBER/READ_ONLY), OIDC multi-issuer auth, review/approval workflow, catalog visibility (status filter, pending review banner) — **in progress**
- **Phase 3 (Ecosystem):** Embeddable UI component, webhooks, vulnerability scanning, Gradle/Maven CI/CD plugin, code signing, S3/MinIO storage, SaaS multi-tenancy

## Deployment (Self-Hosted Minimal)

```
Docker Compose:
├── plugwerk-server  (Spring Boot)
├── postgres         (PostgreSQL 18)
└── nginx            (reverse proxy, TLS – optional)
```

Required environment variables (server refuses to start without them):
- `PLUGWERK_JWT_SECRET` — HMAC signing key, min 32 chars
- `PLUGWERK_ENCRYPTION_KEY` — AES key for OIDC secrets, exactly 16 chars

Optional:
- `PLUGWERK_AUTH_ADMIN_PASSWORD` — fixed initial admin password (random if absent)

See `.env.example` for a complete template.

Health: `/actuator/health` | Metrics: Prometheus | Logging: structured JSON
