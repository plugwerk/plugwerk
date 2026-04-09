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
package io.plugwerk.client.internal

import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.spi.model.PluginReleaseInfo
import io.plugwerk.spi.model.ReleaseStatus

internal fun PluginReleaseDto.toReleaseInfo(): PluginReleaseInfo = PluginReleaseInfo(
    pluginId = pluginId,
    version = version,
    artifactSha256 = artifactSha256,
    requiresSystemVersion = requiresSystemVersion,
    status = status.toReleaseStatus(),
)

internal fun PluginReleaseDto.Status.toReleaseStatus(): ReleaseStatus = when (this) {
    PluginReleaseDto.Status.DRAFT -> ReleaseStatus.DRAFT
    PluginReleaseDto.Status.PUBLISHED -> ReleaseStatus.PUBLISHED
    PluginReleaseDto.Status.DEPRECATED -> ReleaseStatus.DEPRECATED
    PluginReleaseDto.Status.YANKED -> ReleaseStatus.YANKED
}
