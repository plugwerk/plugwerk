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
 * Metadata for a specific versioned release of a plugin.
 *
 * A [PluginReleaseInfo] is always associated with a parent [PluginInfo] via [pluginId].
 * It carries the information needed to decide whether to install a release and to verify
 * the downloaded artifact's integrity.
 *
 * @property pluginId               unique identifier of the plugin this release belongs to
 * @property version                SemVer version string of this release (e.g. `"1.2.3"`)
 * @property artifactSha256         lowercase hex-encoded SHA-256 digest of the artifact JAR/ZIP;
 *   `null` when the server omits the field (legacy compatibility — treat as unverified)
 * @property requiresSystemVersion  SemVer range expression the host application's version must
 *   satisfy for this release to be compatible (e.g. `">=2.0.0 & <4.0.0"`);
 *   `null` means no system-version constraint
 * @property status                 current lifecycle state of this release
 * @property downloadUrl            pre-resolved URL to download the artifact;
 *   `null` when not yet resolved — call
 *   [io.plugwerk.spi.extension.PlugwerkCatalog.getPluginRelease] to obtain a
 *   release with a populated download URL before invoking
 *   [io.plugwerk.spi.extension.PlugwerkInstaller.download]
 */
data class PluginReleaseInfo(
    val pluginId: String,
    val version: String,
    val artifactSha256: String?,
    val requiresSystemVersion: String? = null,
    val status: ReleaseStatus,
    val downloadUrl: String? = null,
)
