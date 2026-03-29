/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.spi.extension

import org.pf4j.ExtensionPoint

/**
 * Unified facade extension point that provides access to all Plugwerk SDK capabilities.
 *
 * Host applications retrieve this facade from the [io.plugwerk.client.PlugwerkMarketplacePlugin]
 * after configuring it, instead of querying [PlugwerkCatalog], [PlugwerkInstaller], and
 * [PlugwerkUpdateChecker] individually. All three sub-components share the same server connection
 * and configuration.
 *
 * Typical usage in a host application:
 *
 * Kotlin:
 * ```kotlin
 * val plugin = pluginManager.getPlugin("plugwerk-client")
 *     .plugin as PlugwerkMarketplacePlugin
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
 * PlugwerkMarketplacePlugin plugin = (PlugwerkMarketplacePlugin)
 *     pluginManager.getPlugin("plugwerk-client").getPlugin();
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
