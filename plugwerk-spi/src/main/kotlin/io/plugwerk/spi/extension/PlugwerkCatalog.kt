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
package io.plugwerk.spi.extension

import io.plugwerk.spi.model.PluginInfo
import io.plugwerk.spi.model.PluginReleaseInfo
import io.plugwerk.spi.model.SearchCriteria
import org.pf4j.ExtensionPoint

/**
 * Extension point for read-only access to the Plugwerk plugin catalog.
 *
 * Implement this interface to provide catalog browsing and search capabilities
 * to a PF4J host application. The default implementation in the client SDK
 * communicates with a remote Plugwerk server over HTTP.
 *
 * Typical usage in a host application:
 *
 * Kotlin:
 * ```kotlin
 * val catalog = pluginManager.getExtensions(PlugwerkCatalog::class.java).first()
 * val plugins = catalog.searchPlugins(SearchCriteria(tag = "analytics"))
 * ```
 *
 * Java:
 * ```java
 * PlugwerkCatalog catalog = pluginManager.getExtensions(PlugwerkCatalog.class).get(0);
 * List<PluginInfo> plugins = catalog.searchPlugins(new SearchCriteria(null, "analytics", null, null));
 * ```
 *
 * @see PlugwerkMarketplace for a unified facade that also exposes [PlugwerkInstaller] and
 *   [PlugwerkUpdateChecker]
 */
interface PlugwerkCatalog : ExtensionPoint {
    /**
     * Returns all plugins available in the configured namespace.
     *
     * The returned list includes only plugins with status [io.plugwerk.spi.model.PluginStatus.ACTIVE].
     * Suspended or archived plugins are excluded.
     *
     * @return unordered list of available plugins; empty list if the catalog is empty
     */
    fun listPlugins(): List<PluginInfo>

    /**
     * Looks up a single plugin by its unique identifier.
     *
     * @param pluginId the plugin's unique ID within the namespace (e.g. `"io.example.my-plugin"`)
     * @return the plugin metadata, or `null` if no plugin with that ID exists in the catalog
     */
    fun getPlugin(pluginId: String): PluginInfo?

    /**
     * Searches the catalog using the given criteria.
     *
     * All non-null fields in [criteria] are combined with AND semantics.
     * Passing an empty [SearchCriteria] is equivalent to calling [listPlugins].
     *
     * @param criteria filter criteria; fields left `null` are ignored
     * @return plugins matching all specified criteria; empty list if none match
     */
    fun searchPlugins(criteria: SearchCriteria): List<PluginInfo>

    /**
     * Returns all known releases for a plugin, including drafts and deprecated versions.
     *
     * Results are not guaranteed to be version-sorted. Use
     * [io.plugwerk.spi.version.compareSemVer] to sort them if needed.
     *
     * @param pluginId the plugin's unique ID within the namespace
     * @return list of all releases; empty list if the plugin has no releases or does not exist
     */
    fun getPluginReleases(pluginId: String): List<PluginReleaseInfo>

    /**
     * Looks up a specific release of a plugin.
     *
     * @param pluginId the plugin's unique ID within the namespace
     * @param version  the exact SemVer version string (e.g. `"1.2.3"`)
     * @return the release metadata, or `null` if no matching release exists
     */
    fun getPluginRelease(pluginId: String, version: String): PluginReleaseInfo?
}
