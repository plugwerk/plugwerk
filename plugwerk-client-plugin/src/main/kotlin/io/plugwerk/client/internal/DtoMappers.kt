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
