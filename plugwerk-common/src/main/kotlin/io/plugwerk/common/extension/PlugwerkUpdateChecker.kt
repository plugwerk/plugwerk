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

import io.plugwerk.common.model.UpdateInfo
import org.pf4j.ExtensionPoint

/**
 * Extension point for checking available plugin updates.
 */
interface PlugwerkUpdateChecker : ExtensionPoint {
    fun checkForUpdates(namespace: String, installedPlugins: Map<String, String>): List<UpdateInfo>
    fun getAvailableUpdates(namespace: String): List<UpdateInfo>
}
