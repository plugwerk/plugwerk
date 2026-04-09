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
