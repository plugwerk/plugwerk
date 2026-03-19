# AGENTS.md

Universal AI agent instructions for PlugWerk. All AI coding agents (Claude Code, GitHub Copilot, Cursor, etc.) should read this file first.

## What PlugWerk Is

PlugWerk is a **plugin marketplace for the Java/PF4J ecosystem** – the missing link between the PF4J plugin framework and a product's plugin ecosystem. Think Maven Central, but for runtime plugins instead of build dependencies.

Two artifacts:
- **PlugWerk Server** – Spring Boot 3.x web application: REST API, catalog, upload, versioning, download
- **PlugWerk Client SDK** – Pure Java 11+ library embedded in host applications: discovery, download, install, update lifecycle

Project concept: `docs/concepts/concept-pf4j-marketplace-en.md`

## Project Status

Currently in the **concept/planning phase**. No code exists yet. Next step: Gradle multi-module project for Phase 1 (MVP).

## Language

All project communication is in **English**: code, documentation, issues, PR descriptions, reviews, ADRs.

## Git Workflow

- **Never commit directly to `main`** – always use a feature branch
- Branch naming: `feature/<issue-id>_<short-description>` (e.g. `feature/42_user-auth`) – every branch ties back to a GitHub Issue
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `chore:`, etc.)
- AI-generated commits include a `Co-Authored-By` trailer
- PRs use the template at `.github/PULL_REQUEST_TEMPLATE.md` (includes AI agent disclosure section)
- One logical change per PR; reference fixed issues with `Closes #N` in the PR body
- Issues use the templates in `.github/ISSUE_TEMPLATE/`:
  - Bugs: `bug_report.md`
  - Features: `feature_request.md`

## Documentation

- Architecture Decision Records: `docs/adrs/` — use `docs/adrs/TEMPLATE.md` for new ADRs
- Feature specifications: `docs/features/` — GitHub Issues link to their corresponding spec file
- Project concept: `docs/concepts/`

## Architecture

### Technology Stack

| Component | Technology |
|-----------|-----------|
| Server Backend | Spring Boot 3.x / Java 21+ |
| Server API | Spring Web (REST) + OpenAPI |
| Database | PostgreSQL |
| Storage | Filesystem / S3-compatible (MinIO) |
| Cache | Redis |
| Web UI | React / TypeScript (or Vaadin) |
| Auth | Spring Security + OIDC/OAuth2 |
| Client SDK | Pure Java 11+ (no Spring dependency) |
| Build | Gradle multi-module |

### Planned Module Structure

```
plugwerk/
├── plugwerk-server/        # Spring Boot application
├── plugwerk-client-sdk/    # Pure Java 11+ library
├── plugwerk-common/        # Shared DTOs, constants
└── plugwerk-descriptor/    # plugwerk.yml parser/validator
```

### Key Design Constraints

- **Client SDK has zero Spring dependency** – must work in any Java 11+ application
- **pf4j-update backward compatibility** – `PlugWerkUpdateRepository` is a drop-in for `DefaultUpdateRepository`; `GET /plugins.json` maintains pf4j-update format
- **Transactional installation** – no partial state on failure; rollback requires retaining previous version
- **Namespace isolation** – all resources are scoped to a namespace; one server serves multiple products/organizations
