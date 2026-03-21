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
package io.plugwerk.common.extension

import io.plugwerk.common.model.PluginInfo
import io.plugwerk.common.model.PluginReleaseInfo
import io.plugwerk.common.model.SearchCriteria
import org.pf4j.ExtensionPoint

/**
 * Extension point for plugin catalog queries.
 */
interface PlugwerkCatalog : ExtensionPoint {
    fun listPlugins(namespace: String): List<PluginInfo>
    fun getPlugin(namespace: String, pluginId: String): PluginInfo?
    fun searchPlugins(namespace: String, criteria: SearchCriteria): List<PluginInfo>
    fun getPluginReleases(namespace: String, pluginId: String): List<PluginReleaseInfo>
    fun getPluginRelease(namespace: String, pluginId: String, version: String): PluginReleaseInfo?
}
