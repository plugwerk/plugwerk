# ADR-0011: Client SDK Authentication Strategy — API Key Primary

## Status

Accepted

## Context

The Plugwerk server supports three authentication methods: JWT (local login), OIDC (external providers), and namespace-scoped API keys (`X-Api-Key` header). The client SDK originally only supported `accessToken` (Bearer token), which was used for both JWT and pre-obtained tokens.

This created ambiguity:
- Should the SDK implement a login flow (username/password → JWT)?
- Should it support API keys natively via the `X-Api-Key` header?
- What is the intended authentication model for CI/CD pipelines and automated consumers?

Key concerns:
- **Security risk** — embedding user credentials in CI pipelines or plugin configurations
- **Token lifecycle** — JWTs expire after 8 hours, requiring refresh logic
- **Simplicity** — the SDK should be simple to configure for automated use cases

## Decision

The client SDK supports two authentication methods with clear priority:

1. **API Key** (`apiKey` field → `X-Api-Key` header) — **recommended** for all automated/CI/CD use cases
2. **Bearer Token** (`accessToken` field → `Authorization: Bearer` header) — for pre-obtained OIDC/JWT tokens

**Priority:** If both are configured, `apiKey` takes precedence.

**No login flow:** The SDK does not implement a username/password → JWT login. Consumers who need a JWT must obtain it externally and pass it via `accessToken`.

### Configuration

```kotlin
// Recommended: API Key
val config = PlugwerkConfig.Builder("https://plugwerk.example.com", "my-namespace")
    .apiKey("pwk_...")
    .build()

// Alternative: Bearer Token (OIDC, pre-obtained JWT)
val config = PlugwerkConfig.Builder("https://plugwerk.example.com", "my-namespace")
    .accessToken("eyJhbG...")
    .build()
```

### Properties file

```properties
plugwerk.serverUrl=https://plugwerk.example.com
plugwerk.namespace=my-namespace
plugwerk.apiKey=pwk_...
# OR
plugwerk.accessToken=eyJhbG...
```

## API Key Permissions

API keys grant **READ_ONLY** access to their namespace:

| Operation | API Key | JWT (MEMBER+) | JWT (ADMIN) |
|-----------|:---:|:---:|:---:|
| List / search / download plugins | ✅ | ✅ | ✅ |
| `plugins.json` (pf4j-update) | ✅ | ✅ | ✅ |
| Check for updates | ✅ | ✅ | ✅ |
| Upload releases | ❌ | ✅ | ✅ |
| Approve / reject releases | ❌ | ❌ | ✅ |
| Delete plugins / releases | ❌ | ❌ | ✅ |
| Manage members | ❌ | ❌ | ✅ |
| Manage access keys | ❌ | ❌ | ✅ |
| Create / delete namespaces | ❌ | ❌ | ✅ (superadmin) |

This is intentional: API keys are designed for **SDK polling and plugin discovery**,
not for management operations. Write operations require a JWT Bearer token.

## Anonymous Reads on Public Namespaces

A namespace flagged `publicCatalog = true` accepts **unauthenticated** GETs against the read-only catalog surface. The carve-out lets a CLI or pf4j-update client browse and download published releases without provisioning credentials at all — the most reduced privilege level in the model. The flag is set per namespace by an ADMIN; absence of the flag (default) keeps the namespace fully private.

**Pinned scope (server-side, `PublicNamespaceFilter`):** anonymous GETs are recognised only on:

```
/api/v1/namespaces/{ns}/plugins
/api/v1/namespaces/{ns}/plugins/{id}
/api/v1/namespaces/{ns}/plugins/{id}/releases
/api/v1/namespaces/{ns}/plugins/{id}/releases/{ver}
/api/v1/namespaces/{ns}/plugins/{id}/releases/{ver}/download
/api/v1/namespaces/{ns}/updates/check
```

Anything else under `/api/v1/namespaces/{ns}/...` (members, access keys, settings, audit) returns `401` for unauthenticated callers irrespective of the flag.

**Token shape (implementation contract, fixed by issue #374):** the carve-out is implemented by installing a `UsernamePasswordAuthenticationToken` whose principal is `"public:<ns>"` and whose only granted authority is `ROLE_PUBLIC_CATALOG`. An `AnonymousAuthenticationToken` is **not** used because Spring's `AuthenticatedAuthorizationManager.authenticated()` rejects every anonymous token via `AuthenticationTrustResolver.isAnonymous` regardless of `isAuthenticated()` — the symptom of #374. The `public:` prefix is a stable identifier downstream filters and services use to distinguish the carve-out token from real users (UUID-based) and access-key tokens (`key:` prefix); see `PublicNamespaceFilter.isPublicCatalogPrincipal`.

**Behaviour matrix on a public namespace:**

| Caller | `GET /plugins` | `GET /members` | `POST /plugins` |
|---|:---:|:---:|:---:|
| Unauthenticated | ✅ 200 (carve-out) | ❌ 401 | ❌ 401 |
| API key (READ_ONLY) | ✅ 200 | ❌ 403 | ❌ 403 |
| JWT MEMBER | ✅ 200 | ✅ 200 | ✅ 200 |
| JWT ADMIN | ✅ 200 | ✅ 200 | ✅ 200 |

When both an `X-Api-Key` and a public-namespace path are present, the API-key principal takes precedence over the carve-out token, so the request is attributed to a named, audited identity.

## Consequences

### Positive
- Simple, secure authentication for SDK clients and automated consumers
- No token refresh logic needed in the SDK (API keys are long-lived)
- No risk of user credentials leaking into plugin configurations
- Clear separation: API keys for read-only machine access, JWTs for management
- Principle of least privilege: keys cannot modify data

### Negative
- CI/CD pipelines that need to upload releases must use JWT, not API keys
- Two code paths (interceptors) to maintain, though both are trivial
- Existing consumers using `accessToken` for JWT continue to work unchanged (backwards compatible)
