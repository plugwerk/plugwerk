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
package io.plugwerk.client

import io.plugwerk.client.catalog.PlugwerkCatalogImpl
import io.plugwerk.client.installer.PlugwerkInstallerImpl
import io.plugwerk.client.updater.PlugwerkUpdateCheckerImpl
import io.plugwerk.spi.PlugwerkConfig
import io.plugwerk.spi.extension.PlugwerkCatalog
import io.plugwerk.spi.extension.PlugwerkInstaller
import io.plugwerk.spi.extension.PlugwerkMarketplace
import io.plugwerk.spi.extension.PlugwerkUpdateChecker

/**
 * Facade that combines catalog, installer, and update checker into a single
 * [PlugwerkMarketplace] implementation.
 *
 * **PF4J plugin mode (recommended):**
 * Obtain the instance via [io.plugwerk.spi.PlugwerkPlugin.marketplace] after configuring the plugin:
 * ```kotlin
 * val plugin = pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID)
 *     .plugin as PlugwerkPlugin
 * plugin.configure(config)
 * val marketplace = plugin.marketplace()
 * ```
 *
 * **Programmatic construction (testing / embedding without PF4J):**
 * ```kotlin
 * val marketplace = PlugwerkMarketplaceImpl.create(config)
 * ```
 */
class PlugwerkMarketplaceImpl internal constructor(
    internal val client: PlugwerkClient,
    private val catalog: PlugwerkCatalog,
    private val installer: PlugwerkInstaller,
    private val updateChecker: PlugwerkUpdateChecker,
) : PlugwerkMarketplace {

    override fun catalog(): PlugwerkCatalog = catalog

    override fun installer(): PlugwerkInstaller = installer

    override fun updateChecker(): PlugwerkUpdateChecker = updateChecker

    /** Shuts down the underlying HTTP client, releasing connection pool and dispatcher threads. */
    internal fun close() {
        client.close()
    }

    companion object {
        /**
         * Creates a fully wired [PlugwerkMarketplaceImpl] from the given [config].
         *
         * [PlugwerkConfig.pluginDirectory] must be set.
         *
         * @param config server, namespace, and plugin directory configuration
         */
        fun create(config: PlugwerkConfig): PlugwerkMarketplaceImpl {
            val pluginDir = config.pluginDirectory ?: throw IllegalStateException(
                "pluginDirectory is required — set it via PlugwerkConfig.Builder.pluginDirectory()",
            )
            val client = PlugwerkClient(config)
            return PlugwerkMarketplaceImpl(
                client = client,
                catalog = PlugwerkCatalogImpl(client),
                installer = PlugwerkInstallerImpl(client, pluginDir),
                updateChecker = PlugwerkUpdateCheckerImpl(client),
            )
        }
    }
}
