# plugwerk-spi

Service Provider Interface (SPI) for the Plugwerk ecosystem. Defines the contract between host applications and the Plugwerk Client SDK.

## Purpose

This module contains **extension point interfaces** and **shared data models** that both the host application and the client SDK depend on. It is the thinnest possible abstraction layer — no HTTP, no persistence, no framework dependencies.

Because PF4J requires that `ExtensionPoint` interfaces are loaded by the **parent classloader** (shared between host and plugin), `plugwerk-spi` must be on the host's classpath, not bundled inside the plugin ZIP.

## Contents

| Package | Description |
|---------|-------------|
| `io.plugwerk.spi.extension` | Extension point interfaces: `PlugwerkCatalog`, `PlugwerkInstaller`, `PlugwerkUpdateChecker`, `PlugwerkMarketplace` (facade) |
| `io.plugwerk.spi.model` | Immutable data classes: `PluginInfo`, `PluginReleaseInfo`, `SearchCriteria`, `UpdateInfo`, `InstallResult` (sealed), `PluginStatus`, `ReleaseStatus` |
| `io.plugwerk.spi.version` | SemVer comparison helpers built on semver4j |

## Extension Points

| Interface | Responsibility |
|-----------|---------------|
| `PlugwerkCatalog` | Browse and search the plugin catalog (read-only) |
| `PlugwerkInstaller` | Download, install, uninstall plugins with SHA-256 verification |
| `PlugwerkUpdateChecker` | Poll for available updates given currently installed versions |
| `PlugwerkMarketplace` | Unified facade combining all three |

## Compatibility

- **JVM target:** 11 (maximum compatibility with host applications)
- **Dependencies:** PF4J, semver4j (both `api` scope)

## Usage

Host applications add this module as a compile-time dependency:

```kotlin
dependencies {
    implementation("io.plugwerk:plugwerk-spi:$plugwerkVersion")
}
```

The client SDK (`plugwerk-client-plugin`) provides the runtime implementations.
