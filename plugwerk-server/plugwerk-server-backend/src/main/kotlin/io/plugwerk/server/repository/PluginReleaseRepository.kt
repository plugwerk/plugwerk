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
package io.plugwerk.server.repository

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.spi.model.ReleaseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface PluginReleaseRepository : JpaRepository<PluginReleaseEntity, UUID> {

    fun findByPluginAndVersion(plugin: PluginEntity, version: String): Optional<PluginReleaseEntity>

    fun findAllByPluginOrderByCreatedAtDesc(plugin: PluginEntity): List<PluginReleaseEntity>

    fun findAllByPlugin(plugin: PluginEntity, pageable: Pageable): Page<PluginReleaseEntity>

    fun findAllByPluginAndStatus(plugin: PluginEntity, status: ReleaseStatus): List<PluginReleaseEntity>

    fun existsByPluginAndVersion(plugin: PluginEntity, version: String): Boolean

    @Modifying
    @Query("UPDATE PluginReleaseEntity r SET r.downloadCount = r.downloadCount + 1 WHERE r.id = :id")
    fun incrementDownloadCount(@Param("id") id: UUID)

    @Query(
        """
        SELECT r FROM PluginReleaseEntity r JOIN FETCH r.plugin p
        WHERE p.namespace = :namespace AND r.status = :status
        ORDER BY r.createdAt DESC
        """,
    )
    fun findPendingByNamespace(
        @Param("namespace") namespace: NamespaceEntity,
        @Param("status") status: ReleaseStatus,
    ): List<PluginReleaseEntity>

    /**
     * Returns the latest published version string per plugin for a given set of plugin IDs.
     * Result is a list of [pluginId, version] pairs where version is the most recently
     * created PUBLISHED release. One DB round-trip for the entire page.
     */
    @Query(
        """
        SELECT r.plugin.id, r.version
        FROM PluginReleaseEntity r
        WHERE r.plugin.id IN :pluginIds
          AND r.status = io.plugwerk.spi.model.ReleaseStatus.PUBLISHED
          AND r.createdAt = (
            SELECT MAX(r2.createdAt)
            FROM PluginReleaseEntity r2
            WHERE r2.plugin.id = r.plugin.id
              AND r2.status = io.plugwerk.spi.model.ReleaseStatus.PUBLISHED
          )
        """,
    )
    fun findLatestPublishedVersionsForPlugins(@Param("pluginIds") pluginIds: Collection<UUID>): List<Array<Any>>

    /**
     * Returns the latest draft version string per plugin for a given set of plugin IDs.
     * Used as fallback for plugins that have no published release yet.
     * Result is a list of [pluginId, version] pairs where version is the most recently
     * created DRAFT release.
     */
    @Query(
        """
        SELECT r.plugin.id, r.version
        FROM PluginReleaseEntity r
        WHERE r.plugin.id IN :pluginIds
          AND r.status = io.plugwerk.spi.model.ReleaseStatus.DRAFT
          AND r.createdAt = (
            SELECT MAX(r2.createdAt)
            FROM PluginReleaseEntity r2
            WHERE r2.plugin.id = r.plugin.id
              AND r2.status = io.plugwerk.spi.model.ReleaseStatus.DRAFT
          )
        """,
    )
    fun findLatestDraftVersionsForPlugins(@Param("pluginIds") pluginIds: Collection<UUID>): List<Array<Any>>
}
