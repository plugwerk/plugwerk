# AGENTS.md

Universal AI agent instructions for Plugwerk. All AI coding agents (Claude Code, GitHub Copilot, Cursor, etc.) should read this file first.

## What Plugwerk Is

Plugwerk is **plugin management and marketplace software for the Java/PF4J ecosystem** – the missing link between the PF4J plugin framework and a product's plugin ecosystem. Think Maven Central, but for runtime plugins instead of build dependencies.

Two artifacts:
- **Plugwerk Server** – Spring Boot 4.x + Kotlin web application: REST API, catalog, upload, versioning, download
- **Plugwerk Client SDK** – Kotlin library (JVM 11+) deployed as a PF4J plugin with isolated classloader: discovery, download, install, update lifecycle

Project concept: `docs/concepts/concept-pf4j-marketplace-en.md`

## Project Status

Phase 1 (Core) is **complete**. Phase 2 (Enterprise) is **in active development** — multi-namespace RBAC, OIDC authentication, and review workflows are implemented. Tracking issue: [#7](https://github.com/devtank42gmbh/plugwerk/issues/7).

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

## License Header (MANDATORY)

### Kotlin files (`src/**/*.kt`)

Every Kotlin source file **must** begin with the AGPL-3.0 license header. This is enforced automatically
by Spotless — running `./gradlew spotlessApply` adds the header to any file missing it.

```kotlin
/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
```

- **Never omit** the header from new files — `spotlessCheck` in CI will fail without it.
- Do **not** modify the header text — the exact wording is controlled in the root `build.gradle.kts` `licenseHeader` block.
- Generated files under `build/generated/` are excluded from this rule (not in `src/`).

### TypeScript / TSX files (`src/**/*.ts`, `src/**/*.tsx`)

Every frontend source file **must** begin with the two-line SPDX header:

```typescript
// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
```

- Not enforced by Spotless (frontend uses ESLint), but **required** by convention.
- Auto-generated files under `src/api/generated/` are excluded.

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
├── plugwerk-descriptor/           # plugwerk.yml parser/validator + PF4J manifest fallback (JVM 11)
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
