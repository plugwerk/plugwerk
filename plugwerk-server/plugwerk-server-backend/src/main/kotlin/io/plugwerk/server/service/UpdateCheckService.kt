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
package io.plugwerk.server.service

import io.plugwerk.api.model.InstalledPluginInfo
import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.api.model.PluginUpdateInfo
import io.plugwerk.api.model.UpdateCheckResponse
import io.plugwerk.common.model.ReleaseStatus
import io.plugwerk.common.version.compareSemVer
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UpdateCheckService(
    private val namespaceRepository: NamespaceRepository,
    private val pluginRepository: PluginRepository,
    private val releaseRepository: PluginReleaseRepository,
) {

    fun checkUpdates(namespaceSlug: String, installed: List<InstalledPluginInfo>): UpdateCheckResponse {
        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }

        val updates = installed.mapNotNull { info ->
            val plugin = pluginRepository.findByNamespaceAndPluginId(namespace, info.pluginId)
                .orElse(null) ?: return@mapNotNull null

            val latestRelease = releaseRepository.findAllByPluginAndStatus(plugin, ReleaseStatus.PUBLISHED)
                .maxWithOrNull(Comparator { a, b -> compareSemVer(a.version, b.version) })
                ?: return@mapNotNull null

            if (compareSemVer(latestRelease.version, info.currentVersion) > 0) {
                PluginUpdateInfo(
                    pluginId = info.pluginId,
                    currentVersion = info.currentVersion,
                    latestVersion = latestRelease.version,
                    release = latestRelease.toDto(),
                )
            } else {
                null
            }
        }

        return UpdateCheckResponse(updates = updates)
    }

    private fun PluginReleaseEntity.toDto(): PluginReleaseDto = PluginReleaseDto(
        id = id!!,
        pluginId = plugin.pluginId,
        version = version,
        status = when (status) {
            ReleaseStatus.DRAFT -> PluginReleaseDto.Status.DRAFT
            ReleaseStatus.PUBLISHED -> PluginReleaseDto.Status.PUBLISHED
            ReleaseStatus.DEPRECATED -> PluginReleaseDto.Status.DEPRECATED
            ReleaseStatus.YANKED -> PluginReleaseDto.Status.YANKED
        },
        artifactSha256 = artifactSha256,
        requiresSystemVersion = requiresSystemVersion,
        createdAt = createdAt,
    )
}
