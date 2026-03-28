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
package io.plugwerk.server.controller

import io.plugwerk.api.ManagementApi
import io.plugwerk.api.model.PluginDto
import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.api.model.PluginUpdateRequest
import io.plugwerk.api.model.ReleaseStatusUpdateRequest
import io.plugwerk.server.controller.mapper.PluginMapper
import io.plugwerk.server.controller.mapper.PluginReleaseMapper
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.PluginReleaseService
import io.plugwerk.server.service.PluginService
import io.plugwerk.spi.model.ReleaseStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.net.URI

@RestController
@RequestMapping("/api/v1")
class ManagementController(
    private val pluginService: PluginService,
    private val releaseService: PluginReleaseService,
    private val pluginMapper: PluginMapper,
    private val releaseMapper: PluginReleaseMapper,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : ManagementApi {

    override fun updatePlugin(
        ns: String,
        pluginId: String,
        pluginUpdateRequest: PluginUpdateRequest,
    ): ResponseEntity<PluginDto> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            NamespaceRole.MEMBER,
        )
        val plugin = pluginService.update(
            namespaceSlug = ns,
            pluginId = pluginId,
            name = pluginUpdateRequest.name,
            description = pluginUpdateRequest.description,
            license = pluginUpdateRequest.license,
            homepage = pluginUpdateRequest.homepage?.toString(),
            repository = pluginUpdateRequest.repository?.toString(),
            icon = pluginUpdateRequest.icon?.toString(),
            categories = pluginUpdateRequest.categories?.toTypedArray(),
            tags = pluginUpdateRequest.tags?.toTypedArray(),
        )
        return ResponseEntity.ok(pluginMapper.toDto(plugin, ns, latestRelease = null))
    }

    override fun uploadPluginRelease(ns: String, artifact: MultipartFile): ResponseEntity<PluginReleaseDto> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            NamespaceRole.MEMBER,
        )
        val release = releaseService.upload(
            namespaceSlug = ns,
            content = artifact.inputStream,
            contentLength = artifact.size,
            originalFilename = artifact.originalFilename,
        )
        val dto = releaseMapper.toDto(release, release.plugin.pluginId)
        return ResponseEntity.created(
            URI("/api/v1/namespaces/$ns/plugins/${release.plugin.pluginId}/releases/${release.version}"),
        ).body(dto)
    }

    override fun updateReleaseStatus(
        ns: String,
        pluginId: String,
        version: String,
        releaseStatusUpdateRequest: ReleaseStatusUpdateRequest,
    ): ResponseEntity<PluginReleaseDto> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            NamespaceRole.ADMIN,
        )
        val newStatus = releaseStatusUpdateRequest.status.toServiceStatus()
        val release = releaseService.updateStatus(ns, pluginId, version, newStatus)
        return ResponseEntity.ok(releaseMapper.toDto(release, pluginId))
    }

    private fun ReleaseStatusUpdateRequest.Status.toServiceStatus(): ReleaseStatus = when (this) {
        ReleaseStatusUpdateRequest.Status.PUBLISHED -> ReleaseStatus.PUBLISHED
        ReleaseStatusUpdateRequest.Status.DEPRECATED -> ReleaseStatus.DEPRECATED
        ReleaseStatusUpdateRequest.Status.YANKED -> ReleaseStatus.YANKED
    }
}
