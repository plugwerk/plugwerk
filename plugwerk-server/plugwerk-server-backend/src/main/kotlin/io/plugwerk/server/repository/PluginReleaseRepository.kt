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

    /**
     * Batch variant of [findAllByPluginAndStatus] — returns every release with the given
     * [status] across the entire [plugins] collection in a single query. Callers typically
     * group the result by `release.plugin.id` in memory. Uses `JOIN FETCH` so accessing
     * `release.plugin` does not trigger a per-release SELECT.
     *
     * Audit row DB-016 — replaces the per-plugin loop in `Pf4jCompatibilityService.buildPluginsJson`.
     */
    @Query(
        """
        SELECT r FROM PluginReleaseEntity r JOIN FETCH r.plugin
        WHERE r.plugin IN :plugins AND r.status = :status
        """,
    )
    fun findAllByPluginInAndStatus(
        @Param("plugins") plugins: Collection<PluginEntity>,
        @Param("status") status: ReleaseStatus,
    ): List<PluginReleaseEntity>

    /**
     * Batch variant of [findAllByPluginOrderByCreatedAtDesc] — returns every release for the
     * entire [plugins] collection in a single query, newest first. Uses `JOIN FETCH` so
     * accessing `release.plugin` does not trigger a per-release SELECT.
     *
     * Audit row DB-017 — replaces the per-plugin loop in `NamespaceService.deleteStorageArtifacts`.
     */
    @Query(
        """
        SELECT r FROM PluginReleaseEntity r JOIN FETCH r.plugin
        WHERE r.plugin IN :plugins
        ORDER BY r.createdAt DESC
        """,
    )
    fun findAllByPluginInOrderByCreatedAtDesc(
        @Param("plugins") plugins: Collection<PluginEntity>,
    ): List<PluginReleaseEntity>

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

    @Query("SELECT r FROM PluginReleaseEntity r JOIN FETCH r.plugin WHERE r.id = :id")
    fun findByIdWithPlugin(@Param("id") id: UUID): Optional<PluginReleaseEntity>

    /**
     * Returns every artifact key referenced by a `plugin_release` row (#190).
     *
     * String-projection rather than full-entity load so a 100k-release deployment
     * doesn't hydrate every join graph just to compare keys. Used by the storage
     * consistency check to compute the symmetric difference against
     * `ArtifactStorageService.listObjects()`.
     */
    @Query("SELECT r.artifactKey FROM PluginReleaseEntity r")
    fun findAllArtifactKeys(): List<String>

    /**
     * Returns the total download count per plugin for a given set of plugin IDs.
     * Each result row is [pluginId, sumDownloadCount].
     */
    @Query(
        """
        SELECT r.plugin.id, SUM(r.downloadCount)
        FROM PluginReleaseEntity r
        WHERE r.plugin.id IN :pluginIds
        GROUP BY r.plugin.id
        """,
    )
    fun sumDownloadCountsByPluginIds(@Param("pluginIds") pluginIds: Collection<UUID>): List<Array<Any>>

    /**
     * Returns the latest PUBLISHED release per plugin for the given set of
     * plugin IDs. One DB round-trip for the entire page.
     *
     * **Tiebreaker (#482)**: when two releases of the same plugin share the
     * same `created_at` (rapid CI imports, batched API calls, or any other
     * sub-millisecond burst that the JVM clock cannot resolve), we want a
     * **deterministic** winner so the catalog shows the same "latest
     * version" on every read. The previous JPQL formulation
     * (`r.createdAt = (SELECT MAX(r2.createdAt) ...)`) returned **both**
     * tied rows, and `associateBy { plugin.id }` at the call site collapsed
     * them implementation-defined-last-wins. Catalog "latest" therefore
     * flickered between versions on identical inputs.
     *
     * The window-function variant below partitions by `plugin_id`, sorts
     * each partition by `created_at DESC, id DESC`, and keeps the first
     * row. `id` is a UUIDv7 so it carries time-ordering past the
     * timestamp's resolution — `id DESC` semantically continues "later
     * wins" past the millisecond precision boundary.
     *
     * Native query because JPQL does not support window functions
     * portably across Hibernate versions. Postgres (production) and H2 in
     * default mode (tests) both implement `ROW_NUMBER() OVER (PARTITION BY)`
     * to ANSI SQL spec; no DB-specific syntax escapes the wrapper.
     */
    @Query(
        value = """
        SELECT pr.* FROM (
          SELECT inner_pr.*,
                 ROW_NUMBER() OVER (
                   PARTITION BY plugin_id
                   ORDER BY created_at DESC, id DESC
                 ) AS rn
          FROM plugin_release inner_pr
          WHERE inner_pr.plugin_id IN (:pluginIds)
            AND inner_pr.status = 'PUBLISHED'
        ) pr
        WHERE pr.rn = 1
        """,
        nativeQuery = true,
    )
    fun findLatestPublishedReleasesForPlugins(
        @Param("pluginIds") pluginIds: Collection<UUID>,
    ): List<PluginReleaseEntity>

    /**
     * Counts the number of active plugins in a namespace that have at least one draft release.
     * Used for the "pending review" banner on the catalog page.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT p.id)
        FROM PluginEntity p
        JOIN PluginReleaseEntity r ON r.plugin = p
        WHERE p.namespace.id = :namespaceId
          AND p.status = io.plugwerk.spi.model.PluginStatus.ACTIVE
          AND r.status = io.plugwerk.spi.model.ReleaseStatus.DRAFT
        """,
    )
    fun countPluginsWithDraftReleases(@Param("namespaceId") namespaceId: UUID): Long

    /**
     * Returns the IDs of active plugins in a namespace that have at least one draft release
     * but no published release. Used to identify "pending review" plugins for catalog display.
     */
    @Query(
        """
        SELECT DISTINCT p.id
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
    fun findPluginIdsWithOnlyDraftReleases(@Param("namespaceId") namespaceId: UUID): Set<UUID>

    /**
     * Counts the total number of draft releases across all plugins in the namespace.
     * This reflects the actual size of the review queue.
     */
    @Query(
        """
        SELECT COUNT(r)
        FROM PluginReleaseEntity r
        WHERE r.plugin.namespace.id = :namespaceId
          AND r.status = io.plugwerk.spi.model.ReleaseStatus.DRAFT
        """,
    )
    fun countDraftReleasesByNamespace(@Param("namespaceId") namespaceId: UUID): Long
}
