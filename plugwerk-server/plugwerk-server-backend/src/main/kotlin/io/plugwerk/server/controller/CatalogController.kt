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

import io.plugwerk.api.CatalogApi
import io.plugwerk.api.model.Pf4jPluginsJson
import io.plugwerk.api.model.PluginDto
import io.plugwerk.api.model.PluginPagedResponse
import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.api.model.ReleasePagedResponse
import io.plugwerk.server.controller.mapper.PluginMapper
import io.plugwerk.server.controller.mapper.PluginReleaseMapper
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.service.Pf4jCompatibilityService
import io.plugwerk.server.service.PluginReleaseService
import io.plugwerk.server.service.PluginService
import io.plugwerk.spi.model.PluginStatus
import io.plugwerk.spi.model.ReleaseStatus
import org.springframework.core.io.InputStreamResource
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class CatalogController(
    private val pluginService: PluginService,
    private val releaseService: PluginReleaseService,
    private val releaseRepository: PluginReleaseRepository,
    private val namespaceRepository: NamespaceRepository,
    private val pf4jService: Pf4jCompatibilityService,
    private val pluginMapper: PluginMapper,
    private val releaseMapper: PluginReleaseMapper,
) : CatalogApi {

    override fun listPlugins(
        ns: String,
        page: Int,
        size: Int,
        sort: String,
        q: String?,
        category: String?,
        tag: String?,
        status: String?,
    ): ResponseEntity<PluginPagedResponse> {
        val pluginStatus = status?.let { parsePluginStatus(it) } ?: PluginStatus.ACTIVE
        val pageable = buildPageable(page, size, sort)
        val resultPage = pluginService.findPagedByNamespace(
            ns,
            pluginStatus,
            category,
            tag,
            q,
            pageable,
            publishedOnly = true,
        )

        val pluginIds = resultPage.content.mapNotNull { it.id }
        val latestReleases: Map<UUID, PluginReleaseEntity> = if (pluginIds.isEmpty()) {
            emptyMap()
        } else {
            releaseRepository.findLatestPublishedReleasesForPlugins(pluginIds)
                .associateBy { it.plugin.id!! }
        }

        val pendingCount = resolvePendingReviewCount(ns)

        val response = PluginPagedResponse(
            content = resultPage.content.map { pluginMapper.toDto(it, ns, latestReleases[it.id]) },
            totalElements = resultPage.totalElements,
            page = resultPage.number,
            propertySize = resultPage.size,
            totalPages = resultPage.totalPages,
            pendingReviewPluginCount = pendingCount,
        )
        return ResponseEntity.ok(response)
    }

    override fun getPlugin(ns: String, pluginId: String): ResponseEntity<PluginDto> {
        val plugin = pluginService.findByNamespaceAndPluginId(ns, pluginId)
        val allReleases = releaseService.findAllByPlugin(ns, pluginId)
        val latestPublishedRelease = allReleases
            .filter { it.status == ReleaseStatus.PUBLISHED }
            .maxByOrNull { it.createdAt }
        return ResponseEntity.ok(pluginMapper.toDto(plugin, ns, latestPublishedRelease))
    }

    override fun listReleases(
        ns: String,
        pluginId: String,
        page: Int,
        size: Int,
    ): ResponseEntity<ReleasePagedResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val plugin = pluginService.findByNamespaceAndPluginId(ns, pluginId)
        val releasePage = releaseService.findPagedByPlugin(plugin, pageable)
        val response = ReleasePagedResponse(
            content = releasePage.content.map { releaseMapper.toDto(it, pluginId) },
            totalElements = releasePage.totalElements,
            page = releasePage.number,
            propertySize = releasePage.size,
            totalPages = releasePage.totalPages,
        )
        return ResponseEntity.ok(response)
    }

    override fun getRelease(ns: String, pluginId: String, version: String): ResponseEntity<PluginReleaseDto> {
        val release = releaseService.findByVersion(ns, pluginId, version)
        return ResponseEntity.ok(releaseMapper.toDto(release, pluginId))
    }

    override fun downloadRelease(
        ns: String,
        pluginId: String,
        version: String,
    ): ResponseEntity<org.springframework.core.io.Resource> {
        val release = releaseService.findByVersion(ns, pluginId, version)
        val extension = if (release.artifactKey.endsWith(".zip")) "zip" else "jar"
        val stream = releaseService.downloadArtifact(ns, pluginId, version)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$pluginId-$version.$extension\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(stream))
    }

    override fun getPluginsJson(ns: String): ResponseEntity<Pf4jPluginsJson> =
        ResponseEntity.ok(pf4jService.buildPluginsJson(ns))

    /**
     * Returns the number of active plugins pending review (draft-only) for authenticated users.
     * Returns `null` for anonymous requests — no sensitive information is leaked.
     */
    private fun resolvePendingReviewCount(ns: String): Long? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        if (!auth.isAuthenticated) return null
        val namespace = namespaceRepository.findBySlug(ns).orElse(null) ?: return null
        return releaseRepository.countPluginsWithOnlyDraftReleases(namespace.id!!)
    }

    private fun parsePluginStatus(value: String): PluginStatus = when (value.lowercase()) {
        "active" -> PluginStatus.ACTIVE
        "archived" -> PluginStatus.ARCHIVED
        else -> throw IllegalArgumentException("Unknown plugin status: $value")
    }

    private fun buildPageable(page: Int, size: Int, sort: String): org.springframework.data.domain.Pageable {
        val parts = sort.split(",")
        val requestedField = parts[0].trim()
        val allowedSortFields = setOf("name", "downloadCount", "updatedAt", "createdAt")
        val field = if (requestedField in allowedSortFields) requestedField else "name"
        val direction = if (parts.getOrNull(1)?.trim()?.lowercase() == "desc") {
            Sort.Direction.DESC
        } else {
            Sort.Direction.ASC
        }
        return PageRequest.of(page, size, Sort.by(direction, field))
    }
}
