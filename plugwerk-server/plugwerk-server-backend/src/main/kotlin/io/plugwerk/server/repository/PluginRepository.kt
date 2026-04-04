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
package io.plugwerk.server.repository

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.spi.model.PluginStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface PluginRepository : JpaRepository<PluginEntity, UUID> {

    fun findByNamespaceAndPluginId(namespace: NamespaceEntity, pluginId: String): Optional<PluginEntity>

    fun findAllByNamespace(namespace: NamespaceEntity): List<PluginEntity>

    fun findAllByNamespace(namespace: NamespaceEntity, pageable: Pageable): Page<PluginEntity>

    fun findAllByNamespaceAndStatus(namespace: NamespaceEntity, status: PluginStatus): List<PluginEntity>

    fun findAllByNamespaceAndStatus(
        namespace: NamespaceEntity,
        status: PluginStatus,
        pageable: Pageable,
    ): Page<PluginEntity>

    fun existsByNamespaceAndPluginId(namespace: NamespaceEntity, pluginId: String): Boolean
}
