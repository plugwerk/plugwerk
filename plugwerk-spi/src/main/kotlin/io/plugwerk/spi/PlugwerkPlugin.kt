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
 *     pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID).getPlugin();
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
        /** PF4J Plugin-Id of the Plugwerk Client SDK plugin. */
        const val PLUGIN_ID = "plugwerk-client-plugin"

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
