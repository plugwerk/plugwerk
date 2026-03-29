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

import io.plugwerk.api.ReviewsApi
import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.api.model.ReviewDecisionRequest
import io.plugwerk.api.model.ReviewItemDto
import io.plugwerk.server.controller.mapper.PluginReleaseMapper
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.PluginReleaseService
import io.plugwerk.spi.model.ReleaseStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class ReviewsController(
    private val releaseService: PluginReleaseService,
    private val releaseMapper: PluginReleaseMapper,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : ReviewsApi {

    override fun listPendingReviews(ns: String): ResponseEntity<List<ReviewItemDto>> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            NamespaceRole.MEMBER,
        )
        val pending = releaseService.findPendingByNamespace(ns).map { release ->
            ReviewItemDto(
                releaseId = release.id!!,
                pluginId = release.plugin.pluginId,
                pluginName = release.plugin.name,
                version = release.version,
                submittedAt = release.createdAt,
                artifactSha256 = release.artifactSha256,
            )
        }
        return ResponseEntity.ok(pending)
    }

    override fun approveRelease(
        ns: String,
        releaseId: UUID,
        reviewDecisionRequest: ReviewDecisionRequest?,
    ): ResponseEntity<PluginReleaseDto> {
        val auth = SecurityContextHolder.getContext().authentication!!
        namespaceAuthorizationService.requireRole(ns, auth, NamespaceRole.ADMIN)
        val isSuperadmin = namespaceAuthorizationService.isSuperadmin(auth)
        val release = releaseService.updateStatusByIdInNamespace(
            releaseId,
            ns,
            ReleaseStatus.PUBLISHED,
            enforceNamespace = !isSuperadmin,
        )
        return ResponseEntity.ok(releaseMapper.toDto(release, release.plugin.pluginId))
    }

    override fun rejectRelease(
        ns: String,
        releaseId: UUID,
        reviewDecisionRequest: ReviewDecisionRequest?,
    ): ResponseEntity<PluginReleaseDto> {
        val auth = SecurityContextHolder.getContext().authentication!!
        namespaceAuthorizationService.requireRole(ns, auth, NamespaceRole.ADMIN)
        val isSuperadmin = namespaceAuthorizationService.isSuperadmin(auth)
        val release = releaseService.updateStatusByIdInNamespace(
            releaseId,
            ns,
            ReleaseStatus.YANKED,
            enforceNamespace = !isSuperadmin,
        )
        return ResponseEntity.ok(releaseMapper.toDto(release, release.plugin.pluginId))
    }
}
