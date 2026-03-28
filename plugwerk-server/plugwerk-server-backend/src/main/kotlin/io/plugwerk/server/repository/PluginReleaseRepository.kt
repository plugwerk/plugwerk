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
     * Returns the full latest published release entity per plugin for a given set of plugin IDs.
     * Replaces the three individual queries for version, draft version, and artifact size.
     * One DB round-trip for the entire page.
     */
    @Query(
        """
        SELECT r
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
    fun findLatestPublishedReleasesForPlugins(
        @Param("pluginIds") pluginIds: Collection<UUID>,
    ): List<PluginReleaseEntity>

    /**
     * Counts the number of active plugins in a namespace that have at least one draft release
     * but no published release. Used for the "pending review" banner on the catalog page.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT p.id)
        FROM PluginEntity p
        JOIN PluginReleaseEntity r ON r.plugin = p
        WHERE p.namespace.id = :namespaceId
          AND p.status = io.plugwerk.spi.model.PluginStatus.ACTIVE
          AND r.status = io.plugwerk.spi.model.ReleaseStatus.DRAFT
          AND NOT EXISTS (
            SELECT 1
            FROM PluginReleaseEntity r2
            WHERE r2.plugin = p
              AND r2.status = io.plugwerk.spi.model.ReleaseStatus.PUBLISHED
          )
        """,
    )
    fun countPluginsWithOnlyDraftReleases(@Param("namespaceId") namespaceId: UUID): Long
}
