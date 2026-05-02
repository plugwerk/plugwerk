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
import org.pf4j.PluginManager
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * PF4J plugin entry point for the Plugwerk Client SDK.
 *
 * This class is referenced in `MANIFEST.MF` via `Plugin-Class`. PF4J instantiates it
 * when the SDK JAR is loaded as a plugin; hosts then access it through the
 * [PlugwerkPlugin] interface and call [connect] to open marketplaces.
 *
 * ### Lifecycle
 *
 * The plugin keeps a list of [WeakReference]s to every marketplace it hands out.
 * On PF4J [stop], any still-reachable marketplace is closed as a defence-in-depth
 * measure for hosts that forget [PlugwerkMarketplace.close]. The contract remains
 * "the caller closes what the caller opened" — the weak-ref sweep is a safety net,
 * not the canonical cleanup path.
 *
 * Garbage-collected marketplaces drop out of the list automatically; we don't bother
 * pruning eagerly since the list is consulted only at [stop] time.
 *
 * @see PlugwerkPlugin
 */
class PlugwerkPluginImpl() :
    Plugin(),
    PlugwerkPlugin {

    private val openMarketplaces = CopyOnWriteArrayList<WeakReference<PlugwerkMarketplaceImpl>>()

    /**
     * Test-only constructor bypassing PF4J's wrapper injection. Lets tests pass
     * a mock [PluginManager] without first running a real PF4J load cycle.
     * Production code uses the no-arg constructor and reads the wrapper-supplied
     * `PluginManager` at [connect] time.
     */
    private var explicitPluginManager: PluginManager? = null

    internal constructor(pluginManager: PluginManager) : this() {
        this.explicitPluginManager = pluginManager
    }

    override fun connect(config: PlugwerkConfig): PlugwerkMarketplace {
        // PF4J injects the wrapper after construction; reading it here is safe
        // because `connect` is host-driven and only callable once the plugin is
        // fully loaded. The PluginManager from the wrapper is the same one the
        // host used to load us — that is the one the installer must drive.
        @Suppress("DEPRECATION")
        val pluginManager = explicitPluginManager ?: wrapper.pluginManager
        val marketplace = PlugwerkMarketplaceImpl.create(config, pluginManager)
        openMarketplaces.add(WeakReference(marketplace))
        return marketplace
    }

    override fun stop() {
        // Defense-in-depth: close any marketplaces still alive when PF4J unloads
        // this plugin. Hosts that closed properly leave nothing for us to do here;
        // forgetful hosts get their HTTP clients reclaimed before classloader
        // unloading rather than leaking dispatcher threads.
        openMarketplaces.forEach { ref -> ref.get()?.close() }
        openMarketplaces.clear()
    }
}
