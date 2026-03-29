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
package io.plugwerk.client

import io.plugwerk.client.catalog.PlugwerkCatalogImpl
import io.plugwerk.client.installer.PlugwerkInstallerImpl
import io.plugwerk.client.updater.PlugwerkUpdateCheckerImpl
import io.plugwerk.spi.extension.PlugwerkMarketplace
import org.pf4j.Plugin

/**
 * PF4J plugin entry point for the Plugwerk Client SDK.
 *
 * This class is referenced in `MANIFEST.MF` via `Plugin-Class`. PF4J instantiates it
 * when the SDK JAR is loaded as a plugin.
 *
 * The host application must call [configure] before accessing the [marketplace] instance.
 * Each plugin instance carries its own [PlugwerkConfig], so multiple instances can connect
 * to different servers or namespaces within the same host application.
 *
 * **Host application setup:**
 * ```kotlin
 * val pluginManager = DefaultPluginManager()
 * pluginManager.loadPlugins()
 * pluginManager.startPlugins()
 *
 * val plugin = pluginManager.getPlugin("plugwerk-client")
 *     .plugin as PlugwerkMarketplacePlugin
 * plugin.configure(
 *     PlugwerkConfig.Builder("https://plugwerk.example.com", "acme")
 *         .accessToken("eyJhbG...")
 *         .pluginDirectory(Path.of("/var/app/plugins"))
 *         .build()
 * )
 *
 * val marketplace = plugin.marketplace()
 * ```
 */
class PlugwerkMarketplacePlugin : Plugin() {

    private var config: PlugwerkConfig? = null
    private var marketplaceInstance: PlugwerkMarketplace? = null

    /**
     * Configures this plugin instance with the given [config].
     *
     * Must be called **before** accessing [marketplace]. Can be called again to
     * reconfigure — the marketplace instance is recreated on the next [marketplace] call.
     */
    fun configure(config: PlugwerkConfig) {
        this.config = config
        this.marketplaceInstance = null
    }

    /**
     * Returns the [PlugwerkMarketplace] facade, creating it on first access.
     *
     * @throws IllegalStateException if [configure] has not been called yet.
     * @throws IllegalStateException if [PlugwerkConfig.pluginDirectory] is not set.
     */
    fun marketplace(): PlugwerkMarketplace {
        marketplaceInstance?.let { return it }

        val cfg = config ?: throw IllegalStateException(
            "PlugwerkMarketplacePlugin has not been configured. " +
                "Call plugin.configure(config) before accessing the marketplace.",
        )
        val pluginDir = cfg.pluginDirectory ?: throw IllegalStateException(
            "pluginDirectory is required — set it via PlugwerkConfig.Builder.pluginDirectory()",
        )
        val client = PlugwerkClient(cfg)
        val instance = PlugwerkMarketplaceImpl(
            catalog = PlugwerkCatalogImpl(client),
            installer = PlugwerkInstallerImpl(client, pluginDir),
            updateChecker = PlugwerkUpdateCheckerImpl(client),
        )
        marketplaceInstance = instance
        return instance
    }
}
