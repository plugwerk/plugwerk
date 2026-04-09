# AGENTS.md

Universal AI agent instructions for Plugwerk. All AI coding agents (Claude Code, GitHub Copilot, Cursor, etc.) should read this file first.

## What Plugwerk Is

Plugwerk is **plugin management and marketplace software for the Java/PF4J ecosystem** – the missing link between the PF4J plugin framework and a product's plugin ecosystem. Think Maven Central, but for runtime plugins instead of build dependencies.

Two artifacts:
- **Plugwerk Server** – Spring Boot 4.x + Kotlin web application: REST API, catalog, upload, versioning, download
- **Plugwerk Client SDK** – Kotlin library (JVM 11+) deployed as a PF4J plugin with isolated classloader: discovery, download, install, update lifecycle

Project concept: `docs/concepts/concept-pf4j-marketplace-en.md`

## Project Status

Phase 1 (Core) is **complete**. Phase 2 (Enterprise) is **in active development** — multi-namespace RBAC, OIDC authentication, and review workflows are implemented. Tracking issue: [#7](https://github.com/plugwerk/plugwerk/issues/7).

## Naming

Use **"Plugwerk"** (not "PlugWerk") everywhere. Base package: `io.plugwerk`.

## Language

All project communication is in **English**: code, documentation, issues, PR descriptions, reviews, ADRs.

This includes **source code comments and KDoc** — inline comments, KDoc (`/** … */`), and `TODO`/`FIXME` notes must all be written in English. German is never acceptable in source files.

## Git Workflow

### Mandatory pre-commit checks

Before **every commit**, run the formatter and linter for the language(s) you changed:

| Language | Command | Directory |
|---|---|---|
| Kotlin | `./gradlew spotlessApply` | repo root |
| TypeScript / TSX | `npm run lint` | `plugwerk-server/plugwerk-server-frontend/` |

CI enforces both (`spotlessKotlinCheck` and `eslint`) and will fail if violations are committed.
Never skip these checks — even for "small" renames or refactors (longer class names can exceed line limits).

- **Never commit directly to `main`** – always use a feature branch
- Branch naming: `feature/<issue-id>_<short-description>` (e.g. `feature/42_user-auth`) – every branch ties back to a GitHub Issue
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `chore:`, etc.)
- AI-generated commits include a `Co-Authored-By` trailer
- PRs use the template at `.github/PULL_REQUEST_TEMPLATE.md` (includes AI agent disclosure section)
- One logical change per PR; reference fixed issues with `Closes #N` in the PR body
- Issues use the templates in `.github/ISSUE_TEMPLATE/`:
  - Bugs: `bug_report.md`
  - Features: `feature_request.md`

## Licensing (MANDATORY)

Plugwerk uses a **dual-license model** (see [ADR-0014](docs/adrs/0014-dual-license-library-modules.md)):

| Module | License | Header file |
|--------|---------|-------------|
| `plugwerk-server` (backend + frontend) | **AGPL-3.0** | `license-header.txt` |
| `plugwerk-spi` | **Apache-2.0** | `license-header-apache.txt` |
| `plugwerk-descriptor` | **Apache-2.0** | `license-header-apache.txt` |
| `plugwerk-client-plugin` | **Apache-2.0** | `license-header-apache.txt` |
| `plugwerk-api-model` | **Apache-2.0** | `license-header-apache.txt` |

Every source file — Kotlin and TypeScript — **must** begin with the correct license header for its module. Spotless is configured per-module to enforce the right header automatically.

### AGPL-3.0 header (server modules)

Canonical text in **`license-header.txt`**:

```
/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
```

### Apache-2.0 header (library modules)

Canonical text in **`license-header-apache.txt`**:

```
/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

### Enforcement

| Language | Check | Fix | Scope |
|---|---|---|---|
| Kotlin (`src/**/*.kt`) | `./gradlew spotlessCheck` | `./gradlew spotlessApply` | Spotless applies per-module header automatically |
| TypeScript (`src/**/*.ts`, `src/**/*.tsx`) | `npm run license:check` | `npm run license:add` | Shell script in `scripts/license-check.sh` |

- **Never omit** the header from new files — CI enforces both checks.
- Do **not** modify headers directly in `build.gradle.kts` — edit the header text files instead.
- Generated files are excluded: `build/generated/` (Kotlin), `src/api/generated/` and `vite-env.d.ts` (TypeScript).

## Issue Management

Every GitHub Issue must have (see [ADR-0002](docs/adrs/0002-issue-management-guidelines.md)):
- **Type** set (Feature / Bug / Task)
- **Milestone** assigned
- **Labels** applied
- **Relationships** (parent/child) if applicable

## Configuration Property Documentation (MANDATORY)

Every configuration property — whether in `application.yml` or bound via `@ConfigurationProperties` — **must** be documented in both places:

1. **`application.yml`** — inline YAML comment (`#`) directly above or beside the property explaining:
   - What the property controls and why it exists
   - The environment variable that overrides it (`PLUGWERK_*`)
   - At least one concrete example value
   - Any constraints or warnings (e.g. "never set to `create` in production")

2. **`PlugwerkProperties.kt`** (or the relevant `@ConfigurationProperties` class) — KDoc on the corresponding field explaining the same, plus code examples in a ```` ```yaml ``` ```` block.

Undocumented properties are non-compliant and must not be merged.

## Pull Request Requirements (MANDATORY)

Every pull request **must** be created with:
- **Labels** — at minimum one label matching the change type (e.g. `enhancement`, `bug`, `chore`)
- **Milestone** — the milestone of the issue(s) being closed (e.g. `Phase 1 — MVP`)

PRs without labels or milestone are non-compliant. Set them via `gh pr edit <number> --add-label "<label>" --milestone "<milestone>"` or through the GitHub UI before requesting review.

## Documentation

- Architecture Decision Records: `docs/adrs/` — use `docs/adrs/TEMPLATE.md` for new ADRs
  - [ADR-0001](docs/adrs/0001-collaboration-workflow.md) — Collaboration workflow
  - [ADR-0002](docs/adrs/0002-issue-management-guidelines.md) — Issue management guidelines
  - [ADR-0003](docs/adrs/0003-spring-boot-4-backend-conventions.md) — Spring Boot 4.x backend conventions
  - [ADR-0004](docs/adrs/0004-frontend-conventions.md) — Frontend conventions (React + TypeScript + MUI + Zustand)
  - [ADR-0011](docs/adrs/0011-client-auth-api-key-strategy.md) — Client SDK authentication (API key primary)
  - [ADR-0014](docs/adrs/0014-dual-license-library-modules.md) — Dual-license: Apache-2.0 for libraries, AGPL-3.0 for server
- Feature specifications: `docs/features/` — GitHub Issues link to their corresponding spec file
- Project concept: `docs/concepts/`
- Design system: `docs/design/` — HTML prototypes and design tokens (`tokens.css`)

## Architecture

### Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Server Backend | Spring Boot 4.x / JVM 21+ |
| Server API | Spring Web (REST) + OpenAPI 3.1 (API-First) |
| Database | PostgreSQL + Liquibase |
| Storage | Filesystem (local) / S3-compatible (planned) |
| Web UI | React 19 / TypeScript / Material UI 7 / Zustand 5 / Vite |
| Auth | Spring Security: JWT (local login) + OIDC multi-issuer + API keys |
| Client SDK | PF4J plugin / OkHttp / Jackson / JVM 11+ |
| Build | Gradle 9.x multi-module (Kotlin DSL) + Vite (frontend) |
| Tests (backend) | JUnit 5 + Mockito + Testcontainers |
| Tests (frontend) | Vitest + @testing-library/react |

### Module Structure

```
plugwerk/
├── plugwerk-api/                  # OpenAPI 3.1 spec (API-First) + generated DTOs/interfaces
├── plugwerk-spi/                  # Shared ExtensionPoint interfaces, DTOs, constants (JVM 11)
├── plugwerk-descriptor/           # MANIFEST.MF parser/validator + plugin.properties fallback (JVM 11)
├── plugwerk-server/
│   ├── plugwerk-server-backend/   # Spring Boot 4.x + Kotlin REST API (JVM 21)
│   └── plugwerk-server-frontend/  # React + TypeScript + MUI + Zustand (embedded in server JAR)
└── plugwerk-client-plugin/        # PF4J plugin, OkHttp, Jackson (JVM 17)
```

### Key Design Constraints

- **Client SDK is a PF4J plugin** with isolated classloader – no dependency conflicts with host application
- **Hybrid Extension Point pattern** – `PlugwerkMarketplace` facade + granular `PlugwerkCatalog`, `PlugwerkInstaller`, `PlugwerkUpdateChecker` as separate ExtensionPoints (interfaces in `plugwerk-spi`)
- **pf4j-update compatible endpoint** – `GET /plugins.json` serves the pf4j-update format for legacy integrations
- **API-First** – OpenAPI 3.1 spec in `plugwerk-api` is the single source of truth
- **Transactional installation** – no partial state on failure; rollback requires retaining previous version
- **Namespace isolation** – all resources are scoped to a namespace; one server serves multiple products/organizations
- **Shared `DataTable` component** – all tabular views use `src/components/common/DataTable.tsx` for consistent styling (see [ADR-0004](docs/adrs/0004-frontend-conventions.md) § Tables)
- **Shared `Toast` component** – all user-facing notifications must use `useUiStore.addToast()` from `src/stores/uiStore.ts`, rendered by `src/components/common/Toast.tsx`. Never use MUI `<Snackbar>` or `<Alert>` for toast notifications — they bypass the centralized styling and produce inconsistent UI

### Core Data Model

```
Namespace (slug, name, description, settings JSON)
  └── Plugin (plugin_id unique per ns, tags[], status)
        └── PluginRelease (SemVer version, artifact_sha256, requires_system_version,
        │                   plugin_dependencies JSON, status: draft/published/deprecated/yanked)
        └── DownloadEvent (release FK, downloaded_at, client_ip, user_agent)

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

### Client SDK Authentication ([ADR-0011](docs/adrs/0011-client-auth-api-key-strategy.md))

Two authentication methods, API key takes precedence:

| Method | Config Field | HTTP Header | Permissions | Use Case |
|--------|-------------|-------------|-------------|----------|
| **API Key** (recommended) | `apiKey` | `X-Api-Key` | **READ_ONLY** (list, search, download) | SDK polling, plugin discovery |
| **Bearer Token** | `accessToken` | `Authorization: Bearer` | Per user role (ADMIN/MEMBER/READ_ONLY) | Management, upload, CI/CD |

```kotlin
// Recommended: API Key (read-only access for SDK consumers)
val config = PlugwerkConfig.Builder("https://plugwerk.example.com", "my-namespace")
    .apiKey("pwk_...")
    .build()
```

The SDK does **not** implement a login flow — JWTs must be obtained externally.
API keys grant **read-only** access; write operations (upload, delete, approve) require a JWT.

### Plugin Descriptor (`MANIFEST.MF`)

Plugin metadata is read from the standard Java `MANIFEST.MF` inside the plugin JAR. Plugwerk extends the PF4J-standard attributes with custom `Plugin-*` headers:

| MANIFEST.MF Attribute | Purpose | Required | PF4J Standard |
|---|---|---|---|
| `Plugin-Id` | Unique plugin identifier | **Yes** | Yes |
| `Plugin-Version` | SemVer version | **Yes** | Yes |
| `Plugin-Class` | Plugin class name | No | Yes |
| `Plugin-Provider` | Provider/organisation | No | Yes |
| `Plugin-Description` | Short description | No | Yes |
| `Plugin-Dependencies` | Comma-separated deps | No | Yes |
| `Plugin-Requires` | SemVer range for host | No | Yes |
| `Plugin-License` | SPDX license | No | Yes |
| `Plugin-Name` | Display name | No | No (custom) |
| `Plugin-Tags` | Comma-separated tags | No | No (custom) |
| `Plugin-Icon` | Icon URL/path | No | No (custom) |
| `Plugin-Screenshots` | Comma-separated URLs | No | No (custom) |
| `Plugin-Homepage` | Project URL | No | No (custom) |
| `Plugin-Repository` | Source code URL | No | No (custom) |

If `MANIFEST.MF` is absent, the server falls back to `plugin.properties`.

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
npm run license:check        # Verify license headers on all TS/TSX files
npm run license:add          # Add missing license headers
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
- `PLUGWERK_TRACKING_ENABLED` — enable/disable download event audit log (default: `true`)
- `PLUGWERK_TRACKING_CAPTURE_IP` — capture client IP in download events (default: `true`)
- `PLUGWERK_TRACKING_ANONYMIZE_IP` — anonymize IP to /24 IPv4 or /48 IPv6 (default: `true`)
- `PLUGWERK_TRACKING_CAPTURE_USER_AGENT` — capture User-Agent header (default: `true`)

See `.env.example` for a complete template.

Health: `/actuator/health` | Metrics: Prometheus | Logging: structured JSON
