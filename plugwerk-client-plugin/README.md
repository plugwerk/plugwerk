# plugwerk-client-plugin

PF4J plugin that implements the Plugwerk Client. Provides catalog browsing, plugin installation, and update checking for host applications.

## Purpose

This module is the runtime implementation of the extension points defined in `plugwerk-spi`. It is packaged as a **PF4J plugin ZIP** that host applications load via `PluginManager`. The plugin communicates with a Plugwerk Server instance over HTTP (OkHttp).

## Architecture

```
Host Application (parent classloader)
├── plugwerk-spi        ← ExtensionPoint interfaces (shared)
├── pf4j                ← Plugin framework (shared)
└── slf4j-api           ← Logging facade (shared)

Plugin Classloader (isolated)
└── plugwerk-client-plugin.zip
    ├── META-INF/MANIFEST.MF     ← loose file (PF4J requirement)
    └── lib/
        ├── plugwerk-client-plugin-<version>.jar
        ├── plugwerk-api-model-<version>.jar
        ├── okhttp-4.x.jar
        ├── jackson-*.jar
        └── kotlin-stdlib-*.jar
```

`plugwerk-spi`, `pf4j`, and `slf4j-api` are **excluded** from the ZIP because PF4J requires that `ExtensionPoint` interfaces share classloader identity between host and plugin.

## Key Classes

| Class | Responsibility |
|-------|---------------|
| `PlugwerkPluginImpl` | PF4J plugin entry point, implements `PlugwerkPlugin` from `plugwerk-spi` |
| `PlugwerkMarketplaceImpl` | Facade combining catalog, installer, and update checker |
| `PlugwerkClient` | Low-level OkHttp HTTP client, namespace-scoped URL construction, auth headers |
| `PlugwerkCatalogImpl` | `GET /plugins`, search, filtering — maps server DTOs to SPI models |
| `PlugwerkInstallerImpl` | Verified download (SHA-256), `install` = download + PF4J `loadPlugin` + `startPlugin`, `uninstall` = `stopPlugin` + `unloadPlugin` + delete artifact (#424) |
| `PlugwerkUpdateCheckerImpl` | `POST /updates/check` with the installed-plugins list |
| `DtoMappers` | Converts `plugwerk-api-model` DTOs to `plugwerk-spi` model classes |

## Configuration

The host application opens a marketplace connection by passing a `PlugwerkConfig` instance to `PlugwerkPlugin.connect`. The returned `PlugwerkMarketplace` is `AutoCloseable` — the host owns the lifecycle.

```kotlin
val pluginManager = DefaultPluginManager(pluginsDir)
pluginManager.loadPlugins()
pluginManager.startPlugins()

val plugin = pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID)
    .plugin as PlugwerkPlugin

plugin.connect(
    PlugwerkConfig.Builder("https://plugwerk.example.com", "acme-crm")
        .accessToken("eyJhbG...")
        .pluginDirectory(Path.of("/var/app/plugins"))
        .build(),
    pluginManager,
).use { marketplace ->
    marketplace.catalog().listPlugins()
}
```

For long-lived references, store the marketplace and close it explicitly when the host shuts down:

```kotlin
val marketplace = plugin.connect(config, pluginManager)
// ... use it across the app's lifetime
marketplace.close()
```

### Multiple Servers

The plugin is a stateless factory — every `connect()` call returns a fresh marketplace with its own HTTP client. The host owns composition (a property, a DI bean, a small map). Spring example:

```kotlin
@Configuration
class PlugwerkBeans(
    private val plugin: PlugwerkPlugin,
    private val pluginManager: PluginManager,
) {

    @Bean(destroyMethod = "close")
    fun productionMarketplace(): PlugwerkMarketplace = plugin.connect(
        PlugwerkConfig.Builder("https://prod.example.com", "acme")
            .accessToken("prod-token")
            .pluginDirectory(Path.of("/var/app/plugins"))
            .build(),
        pluginManager,
    )

    @Bean(destroyMethod = "close")
    fun stagingMarketplace(): PlugwerkMarketplace = plugin.connect(
        PlugwerkConfig.Builder("https://staging.example.com", "acme")
            .accessToken("staging-token")
            .pluginDirectory(Path.of("/var/app/plugins"))
            .build(),
        pluginManager,
    )
}
```

`destroyMethod = "close"` lets Spring drive shutdown; the PF4J plugin's `stop()` keeps a weak-ref list of handed-out marketplaces and closes any still alive as defense-in-depth, so hosts without DI also do not leak HTTP clients on plugin unload.

Or load the config from a properties file:

```properties
plugwerk.serverUrl=https://plugwerk.example.com
plugwerk.namespace=acme-crm
plugwerk.accessToken=eyJhbG...
plugwerk.pluginDirectory=/var/app/plugins
```

```kotlin
val config = PlugwerkConfig.fromProperties(Path.of("plugwerk-client.properties"))
val marketplace = plugin.connect(config, pluginManager)
```

> **Security:** Never pass access tokens as JVM system properties (`-Dplugwerk.accessToken=…`) —
> they are visible in `ps aux` and `/proc/PID/cmdline`. Use the builder or a properties file
> with restricted filesystem permissions instead.

## Compatibility

- **JVM target:** 17
- **Dependencies:** `plugwerk-spi` (api), `plugwerk-api-model` (api), OkHttp, Jackson, PF4J

## Distribution

The client plugin is published to **Maven Central** in two formats:

| Artifact | Coordinates | Use case |
|----------|------------|----------|
| **JAR** | `io.plugwerk:plugwerk-client-plugin:<version>` | Compile-time dependency (rarely needed directly) |
| **PF4J Plugin ZIP** | `io.plugwerk:plugwerk-client-plugin:<version>:pf4j@zip` | Runtime plugin — drop into your PF4J plugins directory |

### Download via Gradle

```kotlin
// Download the PF4J plugin ZIP (e.g., for copying into a plugins directory)
val plugwerkPlugin by configurations.creating
dependencies {
    plugwerkPlugin("io.plugwerk:plugwerk-client-plugin:<version>:pf4j@zip")
}

tasks.register<Copy>("copyPlugwerkPlugin") {
    from(plugwerkPlugin)
    into(layout.projectDirectory.dir("plugins"))
}
```

### Download via Maven

```xml
<dependency>
    <groupId>io.plugwerk</groupId>
    <artifactId>plugwerk-client-plugin</artifactId>
    <version>1.0.0</version>
    <classifier>pf4j</classifier>
    <type>zip</type>
</dependency>
```

### Download from GitHub Release

Every release also attaches the ZIP as a GitHub Release asset at:
`https://github.com/plugwerk/plugwerk/releases/download/v<version>/plugwerk-client-plugin-<version>.zip`

## Build

```bash
./gradlew :plugwerk-client-plugin:build
```

The plugin ZIP is written to `build/pf4j/plugwerk-client-plugin-<version>.zip`.
