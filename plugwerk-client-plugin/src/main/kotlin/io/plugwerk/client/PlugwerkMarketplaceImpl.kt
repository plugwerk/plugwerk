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
import io.plugwerk.spi.extension.PlugwerkCatalog
import io.plugwerk.spi.extension.PlugwerkInstaller
import io.plugwerk.spi.extension.PlugwerkMarketplace
import io.plugwerk.spi.extension.PlugwerkUpdateChecker
import org.pf4j.Extension
import java.nio.file.Path

/**
 * PF4J `@Extension` facade that combines catalog, installer, and update checker into a
 * single [PlugwerkMarketplace] extension point.
 *
 * **PF4J extension discovery (recommended):**
 * Set the required system properties, then let PF4J discover the extension:
 * ```
 * System.setProperty("plugwerk.serverUrl", "https://plugins.example.com")
 * System.setProperty("plugwerk.namespace", "acme")
 * System.setProperty("plugwerk.cacheDirectory", "/var/plugwerk/plugins")
 * val marketplace = pluginManager.getExtensions(PlugwerkMarketplace::class.java).first()
 * ```
 * See [PlugwerkConfig.fromSystemProperties] for all supported properties.
 *
 * **Programmatic construction (testing / embedding without PF4J):**
 * ```kotlin
 * val marketplace = PlugwerkMarketplaceImpl.create(config, pluginDirectory)
 * ```
 */
@Extension
class PlugwerkMarketplaceImpl : PlugwerkMarketplace {
    private val catalog: PlugwerkCatalog
    private val installer: PlugwerkInstaller
    private val updateChecker: PlugwerkUpdateChecker

    /**
     * No-arg constructor for PF4J extension discovery via reflection.
     * Reads configuration from system properties via [PlugwerkConfig.fromSystemProperties].
     */
    constructor() {
        val config = PlugwerkConfig.fromSystemProperties()
        val pluginDir = config.cacheDirectory
            ?: throw IllegalStateException(
                "System property 'plugwerk.cacheDirectory' is required in PF4J plugin mode",
            )
        val client = PlugwerkClient(config)
        catalog = PlugwerkCatalogImpl(client)
        installer = PlugwerkInstallerImpl(client, pluginDir)
        updateChecker = PlugwerkUpdateCheckerImpl(client)
    }

    internal constructor(
        catalog: PlugwerkCatalog,
        installer: PlugwerkInstaller,
        updateChecker: PlugwerkUpdateChecker,
    ) {
        this.catalog = catalog
        this.installer = installer
        this.updateChecker = updateChecker
    }

    override fun catalog(): PlugwerkCatalog = catalog

    override fun installer(): PlugwerkInstaller = installer

    override fun updateChecker(): PlugwerkUpdateChecker = updateChecker

    companion object {
        /**
         * Creates a fully wired [PlugwerkMarketplaceImpl] from the given [config].
         *
         * @param config server and namespace configuration
         * @param pluginDirectory directory where plugin artifacts are stored after installation
         */
        fun create(config: PlugwerkConfig, pluginDirectory: Path): PlugwerkMarketplaceImpl {
            val client = PlugwerkClient(config)
            return PlugwerkMarketplaceImpl(
                catalog = PlugwerkCatalogImpl(client),
                installer = PlugwerkInstallerImpl(client, pluginDirectory),
                updateChecker = PlugwerkUpdateCheckerImpl(client),
            )
        }
    }
}
