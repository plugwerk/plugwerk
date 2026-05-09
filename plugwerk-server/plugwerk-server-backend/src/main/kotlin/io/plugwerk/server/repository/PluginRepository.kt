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
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface PluginRepository : JpaRepository<PluginEntity, UUID> {

    fun findByNamespaceAndPluginId(namespace: NamespaceEntity, pluginId: String): Optional<PluginEntity>

    /**
     * Batch variant of [findByNamespaceAndPluginId] — returns every plugin in the namespace
     * whose `pluginId` is in the given collection, in a single query. Callers typically
     * group the result by `plugin.pluginId` in memory. Replaces per-id loops in update-check
     * paths to keep the statement count constant in the size of the input.
     */
    fun findAllByNamespaceAndPluginIdIn(
        namespace: NamespaceEntity,
        pluginIds: Collection<String>,
    ): List<PluginEntity>

    fun findAllByNamespace(namespace: NamespaceEntity): List<PluginEntity>

    fun findAllByNamespace(namespace: NamespaceEntity, pageable: Pageable): Page<PluginEntity>

    fun findAllByNamespaceAndStatus(namespace: NamespaceEntity, status: PluginStatus): List<PluginEntity>

    fun findAllByNamespaceAndStatus(
        namespace: NamespaceEntity,
        status: PluginStatus,
        pageable: Pageable,
    ): Page<PluginEntity>

    fun existsByNamespaceAndPluginId(namespace: NamespaceEntity, pluginId: String): Boolean

    /**
     * Projects only the `tags` column for plugins matching the given namespace and status
     * set. Each result row is the raw tag array of one plugin; callers flatten and distinct
     * in memory. Avoids loading full [PluginEntity] instances (including the potentially
     * large `description` TEXT column) when only tags are needed.
     *
     * Audit row DB-015 — replaces `findAllByNamespace(...).flatMap { it.tags }` in
     * `PluginService.findDistinctTags`.
     */
    @Query(
        """
        SELECT p.tags FROM PluginEntity p
        WHERE p.namespace = :namespace AND p.status IN :statuses
        """,
    )
    fun findTagsByNamespaceAndStatusIn(
        @Param("namespace") namespace: NamespaceEntity,
        @Param("statuses") statuses: Collection<PluginStatus>,
    ): List<Array<String>>
}
