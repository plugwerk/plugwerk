# plugwerk-client-plugin

PF4J plugin that implements the Plugwerk Client SDK. Provides catalog browsing, plugin installation, and update checking for host applications.

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
| `PlugwerkMarketplacePlugin` | PF4J plugin entry point (`start()` / `stop()` lifecycle) |
| `PlugwerkMarketplaceImpl` | Facade combining catalog, installer, and update checker |
| `PlugwerkClient` | Low-level OkHttp HTTP client, namespace-scoped URL construction, auth headers |
| `PlugwerkConfig` | Configuration data class with builder pattern and `.properties` file loader |
| `PlugwerkCatalogImpl` | `GET /plugins`, search, filtering — maps server DTOs to SPI models |
| `PlugwerkInstallerImpl` | Download with SHA-256 verification, transactional install/uninstall |
| `PlugwerkUpdateCheckerImpl` | `POST /updates/check` with installed version map |
| `DtoMappers` | Converts `plugwerk-api-model` DTOs to `plugwerk-spi` model classes |

## Configuration

```kotlin
val config = PlugwerkConfig.Builder()
    .serverUrl("https://plugwerk.example.com")
    .namespace("acme-crm")
    .accessToken("eyJhbG...")
    .build()
```

Or from a properties file:

```properties
plugwerk.server-url=https://plugwerk.example.com
plugwerk.namespace=acme-crm
plugwerk.access-token=eyJhbG...
```

## Compatibility

- **JVM target:** 17
- **Dependencies:** `plugwerk-spi` (api), `plugwerk-api-model` (api), OkHttp, Jackson, PF4J

## Build

```bash
./gradlew :plugwerk-client-plugin:build
```

The plugin ZIP is written to `build/pf4j/plugwerk-client-plugin-<version>.zip`.
