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

import io.plugwerk.spi.PlugwerkConfig
import io.plugwerk.spi.PlugwerkPlugin
import io.plugwerk.spi.extension.PlugwerkMarketplace
import org.pf4j.Plugin
import java.util.concurrent.ConcurrentHashMap

/**
 * PF4J plugin entry point for the Plugwerk Client SDK.
 *
 * This class is referenced in `MANIFEST.MF` via `Plugin-Class`. PF4J instantiates it
 * when the SDK JAR is loaded as a plugin.
 *
 * Host applications interact with this class exclusively through the [PlugwerkPlugin]
 * interface defined in `plugwerk-spi`:
 *
 * ```kotlin
 * val plugin = pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID).plugin as PlugwerkPlugin
 * plugin.configure(config)
 * val marketplace = plugin.marketplace()
 * ```
 *
 * ```java
 * PlugwerkPlugin plugin = (PlugwerkPlugin)
 *     pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID).getPlugin();
 * plugin.configure(config);
 * PlugwerkMarketplace marketplace = plugin.marketplace();
 * ```
 *
 * @see PlugwerkPlugin
 */
class PlugwerkPluginImpl :
    Plugin(),
    PlugwerkPlugin {

    private data class ServerEntry(val config: PlugwerkConfig, var marketplace: PlugwerkMarketplaceImpl? = null)

    private val servers = ConcurrentHashMap<String, ServerEntry>()

    override fun configure(serverId: String, config: PlugwerkConfig) {
        val old = servers.put(serverId, ServerEntry(config))
        old?.marketplace?.close()
    }

    override fun marketplace(serverId: String): PlugwerkMarketplace {
        val entry = servers[serverId] ?: throw IllegalStateException(
            "No server configured with ID '$serverId'. " +
                "Call plugin.configure(\"$serverId\", config) first.",
        )
        entry.marketplace?.let { return it }

        val instance = PlugwerkMarketplaceImpl.create(entry.config)
        entry.marketplace = instance
        return instance
    }

    override fun serverIds(): Set<String> = servers.keys.toSet()

    override fun remove(serverId: String): Boolean {
        val entry = servers.remove(serverId)
        entry?.marketplace?.close()
        return entry != null
    }

    override fun removeAll() {
        val entries = servers.values.toList()
        servers.clear()
        entries.forEach { it.marketplace?.close() }
    }

    override fun stop() {
        removeAll()
    }
}
