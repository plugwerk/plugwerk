# ADR-0006: Authentication Strategy — JWT + API Key (Phase 1), OIDC (Phase 2)

## Status

Accepted

## Context

Plugwerk needs authentication for two distinct use cases:

1. **Human users** interacting via the Web UI (browser-based login)
2. **Machine-to-machine** access from the Client SDK and CI/CD pipelines

Phase 1 targets self-hosted deployments with a small number of known users and scripts. Full OIDC integration would require an external identity provider, which adds operational overhead that is not justified for MVP.

## Decision

**Phase 1 (MVP):**

- Self-issued JWTs signed with HMAC-SHA256 (`plugwerk.auth.jwt-secret`)
- A provisional `dev-users` list in `application.yml` for human login (no database user table)
- API key support via `X-Api-Key` header for machine access (`ApiKeyAuthFilter`)
- Token validity: 8 hours (configurable)

**Phase 2+:**

- Replace the provisional credential validator with a database-backed `UserRepository`
- Integrate an external OIDC provider (e.g. Keycloak, Auth0) via Spring Security OAuth2 Resource Server
- Remove `dev-users` list from configuration

## Consequences

- **Easier:** Zero external dependencies in Phase 1 — no identity provider to run or configure
- **Easier:** API key flow is simple and CI/CD-friendly
- **Harder:** `dev-users` are committed to source control — not suitable for real production use
- **Harder:** No self-service user registration in Phase 1
- **Risk:** `jwt-secret` must be rotated if exposed; enforced via startup validation and documented warning
