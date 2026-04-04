/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.spi.model

/**
 * Describes an available update for a currently installed plugin.
 *
 * Returned by [io.plugwerk.spi.extension.PlugwerkUpdateChecker.checkForUpdates] for each plugin
 * that has a newer [ReleaseStatus.PUBLISHED][io.plugwerk.spi.model.ReleaseStatus.PUBLISHED] release
 * than the installed version.
 *
 * @property pluginId          unique identifier of the plugin that can be updated
 * @property currentVersion    SemVer version string currently installed on the host
 * @property availableVersion  SemVer version string of the newest published release
 * @property release           full release metadata for [availableVersion], including the download
 *   URL and SHA-256 checksum needed to install the update
 */
data class UpdateInfo(
    val pluginId: String,
    val currentVersion: String,
    val availableVersion: String,
    val release: PluginReleaseInfo,
)
