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
 * Metadata for a plugin as returned by the Plugwerk catalog.
 *
 * A [PluginInfo] describes the plugin itself — not a specific release. To retrieve
 * release-specific data (download URL, checksum, system-version requirement) use
 * [PlugwerkCatalog.getPluginReleases] or [PlugwerkCatalog.getPluginRelease].
 *
 * @property pluginId       unique identifier of the plugin within its namespace
 *   (typically reverse-domain notation, e.g. `"io.example.my-plugin"`)
 * @property name           human-readable display name
 * @property description    short description of what the plugin does; `null` if not provided
 * @property provider       name or organisation of the plugin provider; `null` if not provided
 * @property license        SPDX license identifier (e.g. `"Apache-2.0"`); `null` if not provided
 * @property namespace      slug of the namespace this plugin belongs to;
 *   `null` when the namespace is implicit from the request context
 * @property tags           free-form keywords for search and filtering
 * @property latestVersion  SemVer string of the newest published release;
 *   `null` if the plugin has no published releases yet
 * @property status         current lifecycle state of the plugin
 * @property icon           URL to the plugin's icon image; `null` if no icon is set
 * @property homepage       URL to the plugin's project website; `null` if not provided
 * @property repository     URL to the plugin's source code repository; `null` if not provided
 */
data class PluginInfo(
    val pluginId: String,
    val name: String,
    val description: String? = null,
    val provider: String? = null,
    val license: String? = null,
    val namespace: String? = null,
    val tags: List<String> = emptyList(),
    val latestVersion: String? = null,
    val status: PluginStatus,
    val icon: String? = null,
    val homepage: String? = null,
    val repository: String? = null,
)
