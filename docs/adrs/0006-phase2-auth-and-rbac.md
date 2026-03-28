# ADR-0006: Phase 2 — Database-Backed Authentication, OIDC Support, and Namespace RBAC

## Status

Accepted

## Context

Phase 1 used a provisional `dev-users` list in `application.yml` with plaintext credentials (no database user table).
Phase 2 implements production-ready authentication and access control as described in GitHub Issue #77:

- Local user accounts stored in PostgreSQL, BCrypt-hashed passwords
- Forced password change on first login for admin-created accounts
- Auto-generated admin password on first installation
- OIDC provider configuration via the admin Web UI (database-backed, not YAML)
- Namespace-scoped RBAC with roles: ADMIN, MEMBER, READ_ONLY
- Multi-issuer JWT validation: local HMAC-SHA256 + dynamic OIDC decoders from the database

## Decision

### Database Schema

Three new tables added via Liquibase migration `0002_user_and_rbac.yaml`:

| Table | Purpose |
|-------|---------|
| `plugwerk_user` | Local user accounts with BCrypt password hashes |
| `namespace_member` | Maps a `user_subject` (username or OIDC `sub`) to a `NamespaceRole` within a namespace |
| `oidc_provider` | OIDC/OAuth2 provider configurations with encrypted client secrets |

### Admin Initialization

`AdminInitializationRunner` (implements `ApplicationRunner`) runs at startup:
- If no user named `admin` exists, generates a random 16-character password
- Logs the password once at INFO level
- Creates the admin user with `passwordChangeRequired = true`
- Grants the admin user ADMIN role on all existing namespaces

### Credential Validation

`DatabaseUserCredentialValidator` is the `@Primary` `UserCredentialValidator` bean:
- Looks up the user by username in `plugwerk_user`
- Rejects disabled accounts before password check
- Uses `BCryptPasswordEncoder.matches()` for constant-time comparison

### Password Change Flow

1. `POST /api/v1/auth/login` returns `passwordChangeRequired: boolean` in the response body
2. If `true`, the frontend (`LoginPage`) redirects to `/change-password`
3. `ProtectedRoute` enforces the redirect for all protected pages while `passwordChangeRequired` is set in Zustand state
4. `POST /api/v1/auth/change-password` verifies the current password, updates the hash, and clears the flag

### OIDC Provider Registry

`OidcProviderRegistry` loads all enabled providers from `oidc_provider` at startup:
- Builds a `JwtDecoder` per provider type:
  - `GENERIC_OIDC` / `KEYCLOAK`: `NimbusJwtDecoder.withIssuerLocation(issuerUri)` (OIDC discovery)
  - `GITHUB` / `GOOGLE` / `FACEBOOK`: pre-configured well-known JWKS URLs
- Stores decoders in an `AtomicReference<List<JwtDecoder>>` for thread-safe live reload
- `refresh()` is called by `OidcProviderService.setEnabled()` and `delete()` so changes take effect without restart

`DelegatingJwtDecoder` is the single `JwtDecoder` bean used by Spring Security:
1. Tries the local HMAC-SHA256 decoder first
2. On failure, iterates the OIDC registry decoders until one succeeds

### Namespace RBAC

`NamespaceAuthorizationService.requireRole(slug, authentication, minimumRole)`:
- Namespace Access Keys (principal name starts with `key:`) bypass the check — they are already namespace-scoped
- Looks up the namespace by slug, then checks `namespace_member` for the principal's `user_subject`
- Role hierarchy: ADMIN > MEMBER > READ_ONLY
- Throws `ForbiddenException` (→ 403) on insufficient role

### Client Secret Encryption

OIDC provider client secrets are encrypted at rest using Spring's `TextEncryptor` (AES/PBKDF2):
- Key: `plugwerk.auth.encryption-key` (16 characters, AES-128)
- Salt: fixed hex string `deadbeefcafe0000` (acceptable for PBKDF2 key stretching)
- Secrets are never returned through the API

### Social Login (OAuth2 Authorization Code Flow)

The full browser-based Authorization Code Flow is **not** part of this ADR.
It is tracked separately in GitHub Issue #79.

## Consequences

### Positive

- Production-ready authentication replacing hardcoded dev credentials
- OIDC support is opt-in (disabled by default) and configurable without restart
- Portable identity key (`user_subject`) supports both local and OIDC identities in RBAC without a foreign key
- Forced password change prevents admin accounts from running with auto-generated passwords in production

### Negative / Trade-offs

- `DelegatingJwtDecoder` tries all enabled OIDC decoders sequentially on failure — latency increases linearly with the number of providers
- Fixed PBKDF2 salt for `TextEncryptor` is acceptable but not ideal; rotating the encryption key requires re-encrypting all stored secrets
- GitHub/Google/Facebook OIDC decoders use hard-coded JWKS URLs — URL changes require a code update

## Alternatives Considered

- **Spring Security OAuth2 Resource Server multi-tenancy** (`JwtIssuerAuthenticationManagerResolver`): more idiomatic but requires knowing all issuer URIs upfront at config time; `DelegatingJwtDecoder` allows fully dynamic runtime loading from the database.
- **YAML-based OIDC provider config**: rejected in favor of DB-backed config to allow runtime changes via the admin UI without redeployment.
