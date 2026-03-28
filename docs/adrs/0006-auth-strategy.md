# ADR-0006: Authentication Strategy — Dual-Token Model (Phase 1), OIDC + RBAC (Phase 2)

## Status

Accepted (updated to reflect Phase 1 implementation and Phase 2 design)

## Context

Plugwerk needs authentication for two distinct use cases:

1. **Human users** interacting via the Web UI (browser-based login)
2. **Machine-to-machine** access from the Client SDK and CI/CD pipelines (PF4J host apps)

Phase 1 targets self-hosted deployments with a small number of known users and scripts. Full OIDC integration would require an external identity provider, which adds operational overhead that is not justified for MVP.

## Decision

### Dual-Token Model

Two token types are supported in parallel and intentionally serve different purposes:

| Token type | Header | Issued by | Lifetime | Use case |
|---|---|---|---|---|
| JWT (Bearer) | `Authorization: Bearer <token>` | Plugwerk server (`/api/v1/auth/login`) | 8 h (configurable) | Human users via Web UI |
| Namespace Access Key | `X-Api-Key: <key>` | Namespace admin (via UI/API) | Long-lived, revocable | PF4J host apps, CI/CD pipelines |

**JWT tokens** are stateless and short-lived. No database lookup per request.

**Namespace Access Keys** are stored as SHA-256 hashes in the `namespace_access_key` table (renamed from `api_key` in migration 0002). They are scoped to exactly one namespace and can be revoked or set to expire.

Both token types are handled by separate Spring Security filters that run in order:
1. `PublicNamespaceFilter` — grants anonymous access to GET requests on public namespaces (no token required)
2. `ApiKeyAuthFilter` — validates `X-Api-Key` against `namespace_access_key` table
3. OAuth2 Resource Server — validates `Authorization: Bearer` JWT

### Phase 1 (MVP) — superseded

- Self-issued JWTs signed with HMAC-SHA256 (`plugwerk.auth.jwt-secret`)
- A provisional `dev-users` list in `application.yml` for human login (no database user table)
- Namespace Access Keys via `X-Api-Key` header (`ApiKeyAuthFilter` + `namespace_access_key` table)
- Token validity: 8 hours (configurable)

> **Phase 1 is superseded.** The `dev-users` list and `DevUserCredentialValidator` have been removed.
> See [ADR-0006 Phase 2](./0006-phase2-auth-and-rbac.md) for the current implementation.

### Phase 2+ (see issue #77) — implemented

- Database-backed `UserRepository` replaces the provisional credential validator
- `namespace_member` table for namespace-scoped RBAC (ADMIN / MEMBER / READ_ONLY)
- OIDC provider configuration via the admin Web UI (database-backed)
- Multi-issuer JWT validation: locally issued tokens + externally issued OIDC tokens
- OIDC `sub` claim as user identity key (compatible with `namespace_member.user_subject`)

## Consequences

- **Easier:** Zero external dependencies in Phase 1 — no identity provider to run or configure
- **Easier:** Namespace Access Key flow is simple and CI/CD-friendly; easy to configure in PF4J host app properties
- **Easier:** Clear separation of concerns — human sessions vs. machine identity
- **Risk:** `jwt-secret` must be rotated if exposed; enforced via startup validation and documented warning
