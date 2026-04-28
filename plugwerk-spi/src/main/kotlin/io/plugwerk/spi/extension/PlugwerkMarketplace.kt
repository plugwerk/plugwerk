/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.plugwerk.spi.extension

import org.pf4j.ExtensionPoint

/**
 * Unified facade for one Plugwerk server connection. Bundles the catalog, installer,
 * and update-check capabilities so the host does not have to wire three extension
 * points individually; all three share the same HTTP client and configuration.
 *
 * Obtain an instance via [io.plugwerk.spi.PlugwerkPlugin.connect]. The caller owns
 * the lifecycle — `close()` releases the underlying HTTP client. `PlugwerkMarketplace`
 * implements [AutoCloseable], so Kotlin's `use { }` and Java's try-with-resources
 * both apply directly.
 *
 * Kotlin:
 * ```kotlin
 * val plugin = pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID)
 *     .plugin as PlugwerkPlugin
 * plugin.connect(config).use { marketplace ->
 *     val plugins = marketplace.catalog().listPlugins()
 *     marketplace.installer().install("io.example.my-plugin", "1.0.0")
 *     val updates = marketplace.updateChecker().checkForUpdates(installedVersions)
 * }
 * ```
 *
 * Java:
 * ```java
 * PlugwerkPlugin plugin = (PlugwerkPlugin)
 *     pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID).getPlugin();
 * try (PlugwerkMarketplace marketplace = plugin.connect(config)) {
 *     List<PluginInfo> plugins = marketplace.catalog().listPlugins();
 *     marketplace.installer().install("io.example.my-plugin", "1.0.0");
 *     Map<String, String> installed = Map.of("io.example.my-plugin", "1.0.0");
 *     List<UpdateInfo> updates = marketplace.updateChecker().checkForUpdates(installed);
 * }
 * ```
 *
 * Calling [close] is idempotent. Operations on the catalog / installer / update
 * checker after [close] have undefined behaviour — implementations are expected
 * to throw rather than silently no-op, but the contract is "do not use after close".
 *
 * @see PlugwerkCatalog
 * @see PlugwerkInstaller
 * @see PlugwerkUpdateChecker
 */
interface PlugwerkMarketplace :
    ExtensionPoint,
    AutoCloseable {
    /**
     * Returns the catalog component for browsing and searching available plugins.
     */
    fun catalog(): PlugwerkCatalog

    /**
     * Returns the installer component for downloading and managing plugin artifacts.
     */
    fun installer(): PlugwerkInstaller

    /**
     * Returns the update checker component for detecting newer plugin versions.
     */
    fun updateChecker(): PlugwerkUpdateChecker

    /**
     * Releases the underlying HTTP client and any other resources held by this
     * marketplace. Idempotent — calling more than once is a no-op.
     */
    override fun close()
}
