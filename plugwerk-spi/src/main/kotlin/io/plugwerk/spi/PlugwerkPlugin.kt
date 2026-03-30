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
package io.plugwerk.spi

import io.plugwerk.spi.extension.PlugwerkMarketplace

/**
 * Host-facing contract for configuring and accessing Plugwerk marketplace instances.
 *
 * A single plugin can manage connections to **multiple Plugwerk servers** simultaneously.
 * Each server is identified by a string ID chosen by the host application.
 *
 * **Single server (convenience):**
 * ```kotlin
 * val plugin = wrapper.plugin as PlugwerkPlugin
 * plugin.configure(config)
 * val marketplace = plugin.marketplace()
 * ```
 *
 * **Multiple servers:**
 * ```kotlin
 * val plugin = wrapper.plugin as PlugwerkPlugin
 * plugin.configure("production", prodConfig)
 * plugin.configure("staging", stagingConfig)
 *
 * val prodCatalog = plugin.marketplace("production").catalog()
 * val stagingInstaller = plugin.marketplace("staging").installer()
 * ```
 *
 * **Java:**
 * ```java
 * PlugwerkPlugin plugin = (PlugwerkPlugin)
 *     pluginManager.getPlugin("plugwerk-client").getPlugin();
 * plugin.configure("production", prodConfig);
 * plugin.configure("staging", stagingConfig);
 *
 * PlugwerkMarketplace prod = plugin.marketplace("production");
 * PlugwerkMarketplace staging = plugin.marketplace("staging");
 * ```
 *
 * @see PlugwerkConfig
 * @see PlugwerkMarketplace
 */
interface PlugwerkPlugin {

    companion object {
        /** Default server ID used by the single-server convenience methods. */
        const val DEFAULT_SERVER_ID = "default"
    }

    /**
     * Registers a server configuration under the given [serverId].
     *
     * If a server with this ID was already configured, the old marketplace instance is
     * closed and replaced on the next [marketplace] call.
     *
     * @param serverId identifier chosen by the host application (e.g. `"production"`, `"vendor-a"`)
     * @param config server URL, namespace, credentials, and plugin directory
     */
    fun configure(serverId: String, config: PlugwerkConfig)

    /**
     * Convenience overload that registers the config under [DEFAULT_SERVER_ID].
     *
     * Equivalent to `configure(DEFAULT_SERVER_ID, config)`.
     */
    fun configure(config: PlugwerkConfig) {
        configure(DEFAULT_SERVER_ID, config)
    }

    /**
     * Returns the [PlugwerkMarketplace] facade for the given [serverId], creating it lazily.
     *
     * @throws IllegalStateException if no server with this ID has been configured.
     * @throws IllegalStateException if [PlugwerkConfig.pluginDirectory] is not set.
     */
    fun marketplace(serverId: String): PlugwerkMarketplace

    /**
     * Convenience overload that returns the marketplace for [DEFAULT_SERVER_ID].
     *
     * Equivalent to `marketplace(DEFAULT_SERVER_ID)`.
     */
    fun marketplace(): PlugwerkMarketplace = marketplace(DEFAULT_SERVER_ID)

    /** Returns an immutable snapshot of all registered server IDs. */
    fun serverIds(): Set<String>

    /**
     * Removes the server entry for the given [serverId] and closes its HTTP client.
     *
     * @return `true` if a server with this ID was registered, `false` otherwise.
     */
    fun remove(serverId: String): Boolean

    /** Removes all server entries and closes their HTTP clients. */
    fun removeAll()
}
