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

import io.plugwerk.api.model.PluginDto
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.spi.model.PluginStatus
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Maps [PluginEntity] to the API v1 [PluginDto].
 *
 * Intentionally a separate component so that a future v2 mapper can coexist without
 * touching this class. The namespace slug and latest version are passed explicitly to
 * avoid triggering lazy-loading outside a JPA session.
 */
@Component
class PluginMapper {

    fun toDto(
        entity: PluginEntity,
        namespaceSlug: String,
        latestVersion: String? = null,
        latestDraftVersion: String? = null,
    ): PluginDto = PluginDto(
        id = entity.id!!,
        pluginId = entity.pluginId,
        name = entity.name,
        status = entity.status.toDto(),
        description = entity.description,
        author = entity.author,
        license = entity.license,
        namespace = namespaceSlug,
        categories = entity.categories.toList().takeIf { it.isNotEmpty() },
        tags = entity.tags.toList().takeIf { it.isNotEmpty() },
        latestVersion = latestVersion,
        latestDraftVersion = if (latestVersion == null) latestDraftVersion else null,
        icon = entity.icon?.let { URI(it) },
        homepage = entity.homepage?.let { URI(it) },
        repository = entity.repository?.let { URI(it) },
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    private fun PluginStatus.toDto(): PluginDto.Status = when (this) {
        PluginStatus.ACTIVE -> PluginDto.Status.ACTIVE
        PluginStatus.SUSPENDED -> PluginDto.Status.SUSPENDED
        PluginStatus.ARCHIVED -> PluginDto.Status.ARCHIVED
    }
}
