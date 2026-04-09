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
