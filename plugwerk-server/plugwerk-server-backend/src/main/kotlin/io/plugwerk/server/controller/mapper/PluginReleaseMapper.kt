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
package io.plugwerk.server.controller.mapper

import io.plugwerk.api.model.PluginDependencyDto
import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.spi.model.ReleaseStatus
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * Maps [PluginReleaseEntity] to the API v1 [PluginReleaseDto].
 *
 * Intentionally a separate component so that a future v2 mapper can coexist without
 * touching this class. The plugin ID is passed explicitly to avoid triggering lazy-loading
 * of the parent [io.plugwerk.server.domain.PluginEntity] outside a JPA session.
 *
 * The [pluginDependencies] JSON column is deserialized using [ObjectMapper].
 */
@Component
class PluginReleaseMapper(private val objectMapper: ObjectMapper) {

    private val dependencyListType = objectMapper.typeFactory
        .constructCollectionType(List::class.java, Map::class.java)

    fun toDto(entity: PluginReleaseEntity, pluginId: String): PluginReleaseDto = PluginReleaseDto(
        id = entity.id!!,
        pluginId = pluginId,
        version = entity.version,
        status = entity.status.toDto(),
        artifactSha256 = entity.artifactSha256,
        artifactSize = entity.artifactSize,
        requiresSystemVersion = entity.requiresSystemVersion,
        pluginDependencies = parseDependencies(entity.pluginDependencies),
        downloadCount = entity.downloadCount,
        createdAt = entity.createdAt,
    )

    private fun parseDependencies(json: String?): List<PluginDependencyDto>? {
        if (json == null) return null
        val raw: List<Map<String, String>> = objectMapper.readValue(json, dependencyListType)
        return raw.map { PluginDependencyDto(id = it["id"]!!, version = it["version"]!!) }
    }

    private fun ReleaseStatus.toDto(): PluginReleaseDto.Status = when (this) {
        ReleaseStatus.DRAFT -> PluginReleaseDto.Status.DRAFT
        ReleaseStatus.PUBLISHED -> PluginReleaseDto.Status.PUBLISHED
        ReleaseStatus.DEPRECATED -> PluginReleaseDto.Status.DEPRECATED
        ReleaseStatus.YANKED -> PluginReleaseDto.Status.YANKED
    }
}
