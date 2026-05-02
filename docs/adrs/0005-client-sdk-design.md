# ADR-0005: Client SDK Design

## Status

Accepted, with three later refinements documented inline:
- the original `configure()` + `marketplace()` two-step flow on `PlugwerkPlugin` was collapsed into a single JDBC-style `connect(config)` factory — see [SPI shape update](#spi-shape-update-2026-04-28),
- `PlugwerkInstaller` was rewired to drive PF4J's `PluginManager` lifecycle directly — see [Installer lifecycle update](#installer-lifecycle-update-2026-05-02),
- `connect()` gained an explicit `pluginManager` parameter so the SDK does not depend on PF4J's deprecated `Plugin#wrapper` injection — see [Installer connect-signature update](#installer-connect-signature-update-2026-05-02).

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
- `pluginDirectory` — optional local directory for downloaded/installed plugin artifacts

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
`Plugin-Version`, `Plugin-Provider`). The plugin class is `PlugwerkPluginImpl`
(extends `org.pf4j.Plugin` and implements the `PlugwerkPlugin` interface from `plugwerk-spi`).

Host applications interact with the plugin through the `PlugwerkPlugin` interface, which is
defined in `plugwerk-spi` alongside `PlugwerkConfig`. This means host applications only need
`plugwerk-spi` on their compile classpath — no dependency on `plugwerk-client-plugin` internals.

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

`PlugwerkInstallerImpl.install()` follows a safe protocol — historically just
a filesystem move, expanded in [issue #424](https://github.com/plugwerk/plugwerk/issues/424)
to also drive PF4J's `PluginManager` so the method honours its name:

1. Look up release info on the server (`/plugins/{id}/releases/{version}`)
2. Download artifact to a temporary file
3. Verify SHA-256 checksum against the server-provided hash
4. Atomically move the temp file to its final name in the plugin directory (`Files.move` with `ATOMIC_MOVE`)
5. `pluginManager.loadPlugin(path)` + `pluginManager.startPlugin(id)`

Steps 1–4 are exposed independently as `PlugwerkInstaller.download(...)`
for headless / CI / dry-run callers that want a verified file on disk
without a live PF4J load.

On any failure between download and start, the artifact is rolled back
(deleted from the plugin directory; if `loadPlugin` succeeded but `startPlugin`
threw, `unloadPlugin` is also called as defence-in-depth). No partial state
is left in the plugin directory.

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

## SPI shape update (2026-04-28)

The original SPI exposed a two-step flow on `PlugwerkPlugin`:

```kotlin
plugin.configure(config)     // step 1
val mp = plugin.marketplace() // step 2 — IllegalStateException if step 1 was skipped
```

…plus an internal `serverId → marketplace` registry (`configure(id, config)`,
`marketplace(id)`, `serverIds()`, `remove(id)`, `removeAll()`) for the multi-server case.

That shape was collapsed into a single JDBC-style factory method:

```kotlin
fun connect(config: PlugwerkConfig): PlugwerkMarketplace
```

`PlugwerkMarketplace` now extends `AutoCloseable`. Each `connect()` call returns
a fresh marketplace with its own HTTP client; the caller owns lifecycle. PF4J's
`stop()` keeps a weak-ref list of handed-out marketplaces and closes any still
alive as defense-in-depth.

**Why the change:**

1. The old API allowed `marketplace()` to be called before `configure()` —
   only a runtime `IllegalStateException` flagged the misuse. The new API
   requires a config at the only entry point, so the misuse is structurally
   impossible.
2. The internal registry was redundant with whatever DI / composition the
   host already had. Removing it fits the JDBC `DataSource → Connection`
   shape and OkHttp's long-lived-client pattern — both well-understood
   Java/Kotlin idioms.

**Multi-server became host responsibility.** Hosts compose their own
collection (typically Spring `@Bean(destroyMethod = "close")` per server, or
a small explicit class implementing `AutoCloseable`). Section 5
("Multi-Server Support") above is preserved for historical context but the
canonical pattern is now host-driven, not plugin-driven.

The change landed in plugwerk/plugwerk#365.

## Installer lifecycle update (2026-05-02)

Pre-#424, `PlugwerkInstaller.install()` only manipulated the filesystem
(download + verify + atomic move). Hosts had to call
`pluginManager.loadPlugin(path)` + `startPlugin(id)` themselves afterwards —
the method's name lied about what it did. Symmetric problem for `uninstall()`,
which only deleted the JAR and left the host to call `stopPlugin` /
`unloadPlugin`. `download()` did a "dumb" download with no checksum verify,
and the smart bits (release-info lookup + SHA-256) lived inline inside
`install()`.

The SPI was reshaped (#424) so the method names match what they do:

- **`download(pluginId, version, targetDir): Path`** — verified download
  (release-info lookup + SHA-256 + atomic move + cleanup-on-failure).
  Throws on failure. The path callers want when they need a file but no
  live PF4J load (CI, audit, dry-run).
- **`install(pluginId, version): InstallResult`** — composes `download`
  with `pluginManager.loadPlugin` + `startPlugin`. After successful return
  the plugin is **live** in PF4J. Reinstall semantics: same version → no-op
  success; different version → upgrade in place (stop + unload old → load +
  start new). Failure between download and start rolls the artifact back.
- **`uninstall(pluginId): UninstallResult`** (was `InstallResult`) — stops +
  unloads via `PluginManager`, then deletes the artifact and any expanded
  ZIP directory. New `UninstallResult` sealed class mirrors `InstallResult`
  but carries only `pluginId` (uninstall does not know the version, and
  the empty-string-version code smell is gone).

**Wire-up:** `PlugwerkInstallerImpl` now requires a `PluginManager`
constructor parameter. The host passes its `PluginManager` explicitly to
`PlugwerkPlugin.connect(config, pluginManager)` — the same instance it
just used to resolve the SDK plugin via `getPlugin(PLUGIN_ID)`. The SDK
deliberately does NOT read it from `wrapper.pluginManager`: PF4J 3.15
deprecates `Plugin#wrapper`, the `(PluginWrapper)` constructor and
`getWrapper()`, and recommends host-defined context injection instead.
Threading `pluginManager` through the SPI signature keeps the SDK off the
deprecated API surface and makes the dependency explicit at the call site.
See [Installer connect-signature update](#installer-connect-signature-update-2026-05-02)
below for the post-#426 follow-up that landed this approach.

**Why the change:**

1. The old method names were dishonest — `install` did not install (in the
   PF4J sense), `uninstall` did not uninstall. Hosts that wanted the lifecycle
   step had to remember to call PF4J themselves afterwards, and that
   coupling was nowhere in the contract.
2. `download(...)` without checksum verification is a footgun in a public
   SPI — moving the smart bits in means `download` is now safe to call
   directly, and `install` becomes a clean composition over it.
3. Pre-1.0 (still on `1.0.0-beta.1`) is the right window to break the SPI
   surface for this kind of cleanup.

The architect briefing for #424 considered three options (rename to
`fetch`/`remove` / full lifecycle / split into a parallel `PlugwerkPluginLifecycle`
extension point) and recommended the rename path. The implementation chose
full lifecycle on the user's call: the SPI is already coupled to PF4J via
`ExtensionPoint`, so the marginal cost of `PluginManager` injection is
small, and the honest naming is worth more than the version-agnostic
flexibility we lose.

The change landed in plugwerk/plugwerk#425.

## Installer connect-signature update (2026-05-02)

#425 originally read the host's `PluginManager` from `wrapper.pluginManager`
inside `PlugwerkPluginImpl.connect()`. Two problems surfaced quickly:

1. **NPE under the canonical PF4J load path (#426).** PF4J's
   `DefaultPluginFactory` looks for `Constructor(PluginWrapper)` first and
   falls back to no-arg only if absent. Because we exposed only no-arg + a
   test-only `(PluginManager)` constructor, PF4J chose no-arg and never
   set the inherited `Plugin#wrapper` field. The first `wrapper.pluginManager`
   read NPEd. Documented host pattern (Spring example) was broken.
2. **PF4J 3.15 deprecates the entire `Plugin#wrapper` injection path.**
   `Plugin(PluginWrapper)`, `Plugin#wrapper` field, and `getWrapper()`
   are all marked `@Deprecated` with the recommendation to use
   "application custom `PluginContext` instead of `PluginWrapper`."
   Adding a `(PluginWrapper)` constructor would have fixed #426 today
   but tied us to a deprecated API forever.

The fix collapses both problems by **threading `pluginManager` through
the `connect()` signature**:

```kotlin
// Before
fun connect(config: PlugwerkConfig): PlugwerkMarketplace

// After (#426 / PR #427)
fun connect(config: PlugwerkConfig, pluginManager: PluginManager): PlugwerkMarketplace
```

The host always has a `PluginManager` reference in scope at the call
site — it's the same instance it just used to resolve the SDK plugin
via `pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID)`. Passing it
costs one extra parameter and:

- removes every read from `Plugin#wrapper` in our code,
- removes the (now-unnecessary) test-only `(PluginManager)` constructor
  and the `explicitPluginManager` field,
- makes the dependency on PF4J's lifecycle explicit at the call site,
- keeps `PlugwerkPluginImpl` on the canonical no-arg `Plugin()`
  constructor — exactly what the deprecation note recommends moving
  toward.

The change landed in plugwerk/plugwerk#427.
