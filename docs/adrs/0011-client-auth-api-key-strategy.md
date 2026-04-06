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
