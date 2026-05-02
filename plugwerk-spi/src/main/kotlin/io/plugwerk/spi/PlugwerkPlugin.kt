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
import org.pf4j.PluginManager

/**
 * Host-facing entry point for the Plugwerk Client SDK — a JDBC-style **factory** for
 * [PlugwerkMarketplace] connections.
 *
 * One method, one job: hand the host a fresh marketplace bound to a given
 * [PlugwerkConfig]. The host owns the returned marketplace; closing it (or letting
 * the PF4J plugin's `stop()` lifecycle do it) releases the underlying HTTP client.
 *
 * **Single server:**
 * ```kotlin
 * val plugin = pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID).plugin as PlugwerkPlugin
 * plugin.connect(config, pluginManager).use { marketplace ->
 *     marketplace.catalog().listPlugins()
 * }
 * ```
 *
 * **Long-lived connections (typical Spring host):**
 * ```kotlin
 * @Configuration
 * class PlugwerkBeans(
 *     private val plugin: PlugwerkPlugin,
 *     private val pluginManager: PluginManager,
 * ) {
 *     @Bean(destroyMethod = "close")
 *     fun marketplace() = plugin.connect(config, pluginManager)
 * }
 * ```
 *
 * **Multiple servers — host owns composition:**
 * ```kotlin
 * class PlugwerkServers(
 *     plugin: PlugwerkPlugin,
 *     pluginManager: PluginManager,
 * ) : AutoCloseable {
 *     val production = plugin.connect(prodConfig, pluginManager)
 *     val staging = plugin.connect(stagingConfig, pluginManager)
 *     override fun close() { production.close(); staging.close() }
 * }
 * ```
 *
 * **Java:**
 * ```java
 * PlugwerkPlugin plugin = (PlugwerkPlugin)
 *     pluginManager.getPlugin(PlugwerkPlugin.PLUGIN_ID).getPlugin();
 * try (PlugwerkMarketplace mp = plugin.connect(config, pluginManager)) {
 *     mp.catalog().listPlugins();
 * }
 * ```
 *
 * ### Why the host passes the PluginManager explicitly
 *
 * After #424 the installer drives PF4J's `PluginManager` (load + start /
 * stop + unload). The SDK could read it from `wrapper.pluginManager`, but
 * PF4J 3.15 deprecates the `Plugin#wrapper` field and its `(PluginWrapper)`
 * constructor in favour of host-defined context injection. Threading
 * `pluginManager` through [connect] keeps the SDK off the deprecated API
 * surface and makes the dependency explicit at the call site — the host
 * always has a `pluginManager` reference in scope (it just used it to
 * resolve the SDK plugin), so passing it costs one parameter (#426).
 *
 * ### Why no internal registry
 *
 * Earlier versions of this SPI tracked configurations under string IDs and required a
 * two-step `configure()` + `marketplace()` flow. That made `marketplace()` callable
 * before `configure()` — a runtime-only error the type system could not catch. The
 * registry was also redundant for any host with a DI container or a small map of
 * its own. The current design follows the JDBC `DataSource → Connection` shape:
 * the plugin is a stateless factory, the host composes its own collection of
 * marketplaces.
 *
 * ### Lifecycle safety net
 *
 * Implementations may track marketplaces handed out via [connect] using weak
 * references and close any still alive when the PF4J plugin stops. This is a
 * defence-in-depth measure against hosts that forget [PlugwerkMarketplace.close];
 * the canonical contract remains "the caller closes what the caller opened".
 *
 * @see PlugwerkConfig
 * @see PlugwerkMarketplace
 */
interface PlugwerkPlugin {

    companion object {
        /** PF4J Plugin-Id of the Plugwerk Client SDK plugin. */
        const val PLUGIN_ID = "plugwerk-client-plugin"
    }

    /**
     * Opens a fresh [PlugwerkMarketplace] backed by [config]. The caller owns the
     * returned instance — call `close()` (or use it in a `use { }` / try-with-resources
     * block) to release the underlying HTTP client.
     *
     * Each call returns a new marketplace with its own HTTP client; there is no
     * internal cache. Hosts that want to reuse a marketplace across call sites
     * should store the reference (a property, a DI bean, a small map) — see the
     * class-level KDoc for typical patterns.
     *
     * @param config         server, namespace, and plugin directory configuration
     * @param pluginManager  the host's PF4J [PluginManager] — required so the
     *                       installer can drive `loadPlugin` / `startPlugin` /
     *                       `stopPlugin` / `unloadPlugin`. The host already has
     *                       a reference (it used it to resolve this SDK plugin
     *                       via `getPlugin(PLUGIN_ID)`); pass the same instance.
     *
     * @throws IllegalStateException if [PlugwerkConfig.pluginDirectory] is not set.
     */
    fun connect(config: PlugwerkConfig, pluginManager: PluginManager): PlugwerkMarketplace
}
