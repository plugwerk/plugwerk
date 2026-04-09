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
