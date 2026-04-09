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
