# ADR-0005: Client SDK Design

## Status

Accepted

## Context

Milestone 7 (Issue #18) implements the Plugwerk Client SDK (`plugwerk-client-plugin`). During planning,
several architectural decisions were made about configuration, authentication, namespace handling,
multi-server support, and the SDK's role as a PF4J plugin. These decisions need to be documented
to provide a stable foundation for implementation and future contributors.

## Decision

### 1. HTTP Client: OkHttp 5.x

The SDK uses OkHttp 5.3.2 (already declared in `libs.versions.toml`) for all HTTP communication.
A shared `OkHttpClient` instance is held by `PlugwerkClient` and reused across all operations.
Timeouts are configured from `PlugwerkConfig`.

### 2. Authentication: Optional Bearer Token

Authentication is optional. Some Plugwerk namespaces are publicly accessible (anonymous); others
require an access token. The SDK uses the standard HTTP `Authorization: Bearer <token>` header,
added via an OkHttp `Interceptor` only when an access token is configured. The config property is
named `accessToken` (not `apiKey`).

### 3. Namespace is Configuration, Not a Runtime Parameter

The namespace a client talks to is fixed at configuration time — it is a field in `PlugwerkConfig`,
not a parameter passed at every API call. This leads to a clean call site (`catalog.listPlugins()`
instead of `catalog.listPlugins("acme")`) and avoids accidental namespace mismatches at runtime.

As a consequence, the `namespace: String` parameter is **removed from all interface methods** in
`plugwerk-spi` (`PlugwerkCatalog`, `PlugwerkInstaller`, `PlugwerkUpdateChecker`). Each SDK
instance is bound to exactly one namespace.

### 4. PlugwerkConfig: Server + Namespace Configuration

`PlugwerkConfig` is the single configuration object for one SDK instance. It captures:

- `serverUrl` — base URL of the Plugwerk server (e.g. `https://plugins.example.com`)
- `namespace` — the namespace on that server (e.g. `acme`)
- `accessToken` — optional Bearer token; `null` means anonymous access
- `connectionTimeoutMs`, `readTimeoutMs` — OkHttp timeouts
- `cacheDirectory` — optional local directory for downloaded artifacts

The namespace is **not** embedded in `serverUrl` as a path segment. Keeping it as a dedicated field
makes it visible in logs, validatable on startup, and clearly separable from the host URL.

The client SDK constructs API URLs as:
```
{serverUrl}/api/v1/namespaces/{namespace}/...
```

`PlugwerkConfig` provides a builder for programmatic construction and
`fromProperties(path: Path)` / `fromProperties(stream: InputStream)` factory methods for
file-based configuration.

### 5. Multi-Server Support

To connect to multiple Plugwerk servers (or multiple namespaces on different servers), the
application creates one `PlugwerkConfig` + `PlugwerkClient` + `PlugwerkMarketplaceImpl` per
server/namespace. There is no global registry — lifecycle management is left to the host
application or PF4J plugin manager.

### 6. SDK as a PF4J Plugin (ZIP Bundle)

The `plugwerk-client-plugin` is packaged as a PF4J plugin in ZIP format:

```
plugwerk-client-plugin-<version>.zip
├── plugwerk-client-plugin-<version>.jar   (Plugin JAR with MANIFEST.MF)
└── lib/
    ├── jackson-databind-x.y.z.jar
    ├── okhttp-x.y.z.jar
    ├── plugwerk-api-model-x.y.z.jar
    └── ...
```

The plugin JAR contains an embedded `MANIFEST.MF` with PF4J metadata (`Plugin-Id`, `Plugin-Class`,
`Plugin-Version`, `Plugin-Provider`). The plugin class is `PlugwerkMarketplacePlugin`
(extends `org.pf4j.Plugin` using the no-arg constructor).

`PlugwerkMarketplaceImpl` is annotated with `@Extension` and implements `PlugwerkMarketplace`
(the facade extension point from `plugwerk-spi`). Host applications retrieve the marketplace
via PF4J's standard extension lookup.

**Host-provided dependencies** (`plugwerk-spi`, `pf4j`) are excluded from the ZIP — they must be
on the host application's classpath. All other dependencies (OkHttp, Jackson, api-model) are
bundled under `lib/`.

### 7. No pf4j-update Dependency

The SDK does **not** depend on the `pf4j-update` library. The original plan considered implementing
`org.pf4j.update.UpdateRepository` as a drop-in replacement, but this was rejected for two reasons:

1. **CVE exposure:** pf4j-update 2.3.0 transitively depends on Gson with known deserialization
   vulnerabilities (CVE-2022-25647, WS-2021-0419), and no new version will be released.
2. **Classloader isolation conflict:** `UpdateRepository` is not a PF4J extension point. When the
   SDK runs as a PF4J plugin with an isolated classloader, the host application cannot access
   `PlugwerkUpdateRepository` as an `UpdateRepository` instance. The interface only works when the
   SDK is used as a plain library on the host classpath — contradicting the plugin packaging model.

The SDK provides `PlugwerkUpdateChecker` as a proper PF4J extension point for update checking.
Host applications that need pf4j-update integration can build a thin adapter using the
`PlugwerkCatalog` extension point.

### 8. Transactional Install with Rollback

`PlugwerkInstallerImpl.install()` follows a safe three-step protocol:

1. Download artifact to a temporary file
2. Verify SHA-256 checksum against the server-provided hash
3. Atomically move the temp file to the plugin directory (`Files.move` with `ATOMIC_MOVE`)

On any failure (download error, checksum mismatch, move failure), the temporary file is deleted.
No partial state is left in the plugin directory.

### 9. Shared API Model Module

The `plugwerk-api` module is split into two sub-modules:

- **`plugwerk-api-model`** — contains only the OpenAPI-generated data transfer objects (DTOs).
  Dependencies are minimal: Jackson Annotations + Jakarta Validation. No Spring dependency.
  Targets JVM 17 for compatibility with the client SDK.
- **`plugwerk-api-endpoint`** — contains only the OpenAPI-generated Spring controller interfaces.
  Depends on `plugwerk-api-model` + Spring Web.

The client SDK depends on `plugwerk-api-model` directly, reusing the generated DTOs instead of
maintaining hand-written client-side DTOs. The server backend depends on `plugwerk-api-endpoint`,
which transitively includes the model classes. This ensures a single source of truth for the API
contract: the OpenAPI specification.

## Consequences

- **Simpler call sites:** No namespace threading through every method call.
- **Breaking change in `plugwerk-spi`:** All three extension-point interfaces lose their
  `namespace` parameter. Any existing callers must be updated (currently none outside the SDK
  itself).
- **One instance per namespace:** Applications connecting to multiple namespaces hold multiple
  SDK instances. This is intentional — it keeps the SDK stateless and easy to reason about.
- **No pf4j-update dependency:** The SDK is free of known CVEs. Host apps needing pf4j-update
  compatibility can write a thin adapter over `PlugwerkCatalog`.
- **ZIP packaging:** The `assemble` task produces a ready-to-deploy PF4J plugin ZIP. Host apps
  drop it into their plugin directory — no manual dependency management required.
