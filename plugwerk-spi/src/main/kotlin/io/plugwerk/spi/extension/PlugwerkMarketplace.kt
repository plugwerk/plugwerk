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
 * Unified facade extension point that provides access to all Plugwerk SDK capabilities.
 *
 * Host applications retrieve this facade from the [io.plugwerk.spi.PlugwerkPlugin]
 * after configuring it, instead of querying [PlugwerkCatalog], [PlugwerkInstaller], and
 * [PlugwerkUpdateChecker] individually. All three sub-components share the same server connection
 * and configuration.
 *
 * Typical usage in a host application:
 *
 * Kotlin:
 * ```kotlin
 * val plugin = pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID)
 *     .plugin as PlugwerkPlugin
 * plugin.configure(config)
 * val marketplace = plugin.marketplace()
 *
 * // Browse the catalog
 * val plugins = marketplace.catalog().listPlugins()
 *
 * // Install a specific version
 * marketplace.installer().install("io.example.my-plugin", "1.0.0")
 *
 * // Check for updates of all installed plugins
 * val updates = marketplace.updateChecker().checkForUpdates(installedVersions)
 * ```
 *
 * Java:
 * ```java
 * PlugwerkPlugin plugin = (PlugwerkPlugin)
 *     pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID).getPlugin();
 * plugin.configure(config);
 * PlugwerkMarketplace marketplace = plugin.marketplace();
 *
 * // Browse the catalog
 * List<PluginInfo> plugins = marketplace.catalog().listPlugins();
 *
 * // Install a specific version
 * marketplace.installer().install("io.example.my-plugin", "1.0.0");
 *
 * // Check for updates of all installed plugins
 * Map<String, String> installed = Map.of("io.example.my-plugin", "1.0.0");
 * List<UpdateInfo> updates = marketplace.updateChecker().checkForUpdates(installed);
 * ```
 *
 * @see PlugwerkCatalog
 * @see PlugwerkInstaller
 * @see PlugwerkUpdateChecker
 */
interface PlugwerkMarketplace : ExtensionPoint {
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
}
