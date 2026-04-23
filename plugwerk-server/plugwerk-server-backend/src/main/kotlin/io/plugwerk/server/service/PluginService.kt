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
package io.plugwerk.server.service

import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.spi.model.PluginStatus
import io.plugwerk.spi.model.ReleaseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PluginService(
    private val pluginRepository: PluginRepository,
    private val releaseRepository: PluginReleaseRepository,
    private val storageService: ArtifactStorageService,
    private val namespaceService: NamespaceService,
) {

    fun findByNamespaceAndPluginId(namespaceSlug: String, pluginId: String): PluginEntity {
        val namespace = namespaceService.findBySlug(namespaceSlug)
        return pluginRepository.findByNamespaceAndPluginId(namespace, pluginId)
            .orElseThrow { PluginNotFoundException(namespaceSlug, pluginId) }
    }

    fun findAllByNamespace(namespaceSlug: String, status: PluginStatus? = null): List<PluginEntity> {
        val namespace = namespaceService.findBySlug(namespaceSlug)
        return if (status != null) {
            pluginRepository.findAllByNamespaceAndStatus(namespace, status)
        } else {
            pluginRepository.findAllByNamespace(namespace)
        }
    }

    /**
     * Catalog visibility level, determined by the caller's role.
     */
    enum class CatalogVisibility {
        /** Anonymous or API-key callers: only ACTIVE plugins with published releases. */
        PUBLIC,

        /** Authenticated namespace member / read-only: all statuses except SUSPENDED, incl. draft-only. */
        AUTHENTICATED,

        /** Namespace admin or system admin: all plugins, all statuses. */
        ADMIN,
    }

    /**
     * Result of a paged catalog query, including the set of draft-only plugin IDs
     * so the controller can pass it to the mapper.
     */
    data class PagedCatalogResult(val page: Page<PluginEntity>, val draftOnlyPluginIds: Set<java.util.UUID>)

    /**
     * Returns a paginated, filtered list of plugins for the given namespace (RC-022,
     * KT-001 / #284).
     *
     * Orchestrates four phases, each implemented by a private helper: load catalog data
     * in a single DB pass → apply visibility rules → apply tag/query/version filters →
     * sort and paginate. Tag, full-text, version-compatibility, and visibility filters
     * are applied in-memory after the DB query, which is acceptable for MVP catalog
     * sizes (≤ a few thousand plugins per namespace). A DB-level filter can replace
     * this once scale demands it — the single orchestration body makes that swap a
     * localised change.
     */
    fun findPagedByNamespace(
        namespaceSlug: String,
        status: PluginStatus?,
        tag: String?,
        q: String?,
        pageable: Pageable,
        visibility: CatalogVisibility = CatalogVisibility.PUBLIC,
        version: String? = null,
    ): PagedCatalogResult {
        val namespace = namespaceService.findBySlug(namespaceSlug)
        val data = loadCatalogData(namespace, status, visibility)
        val visible = filterByVisibility(data.plugins, visibility, data)
        val filtered = applyCatalogFilters(visible, tag, q, version, data.latestReleases)
        return sortAndPaginate(filtered, pageable, data.downloadCounts, data.draftOnlyPluginIds)
    }

    /**
     * Single DB-touching phase of [findPagedByNamespace]. Three queries:
     *   1. `plugins` — all plugins in the namespace, optionally narrowed by status.
     *   2. `latestReleases` — the latest published release per plugin, keyed by plugin id.
     *      Shared across the visibility filter (keys → pluginIdsWithPublishedRelease) and
     *      the version-compatibility filter (values → release version). Audit row DB-015
     *      required this to happen exactly once per request.
     *   3. `draftOnlyPluginIds` — only executed for non-PUBLIC visibility (superadmin /
     *      member views); skipped as a no-op for anonymous callers.
     *   4. `downloadCounts` — sum of downloads per plugin, for the downloadCount sort.
     */
    private fun loadCatalogData(
        namespace: io.plugwerk.server.domain.NamespaceEntity,
        status: PluginStatus?,
        visibility: CatalogVisibility,
    ): CatalogData {
        val plugins = if (status != null) {
            pluginRepository.findAllByNamespaceAndStatus(namespace, status)
        } else {
            pluginRepository.findAllByNamespace(namespace)
        }
        val allIds = plugins.mapNotNull { it.id }

        val latestReleases: Map<java.util.UUID, PluginReleaseEntity> = if (allIds.isEmpty()) {
            emptyMap()
        } else {
            releaseRepository.findLatestPublishedReleasesForPlugins(allIds)
                .associateBy { requireNotNull(it.plugin.id) { "Plugin has no persisted id" } }
        }

        val draftOnlyPluginIds: Set<java.util.UUID> =
            if (visibility != CatalogVisibility.PUBLIC && namespace.id != null) {
                releaseRepository.findPluginIdsWithOnlyDraftReleases(namespace.id!!)
            } else {
                emptySet()
            }

        val downloadCounts: Map<java.util.UUID, Long> = if (allIds.isEmpty()) {
            emptyMap()
        } else {
            releaseRepository.sumDownloadCountsByPluginIds(allIds)
                .associate { (it[0] as java.util.UUID) to (it[1] as Long) }
        }

        return CatalogData(plugins, latestReleases, draftOnlyPluginIds, downloadCounts)
    }

    /**
     * Visibility filter — pure, no DB access. The three visibility tiers have distinct
     * rules; see [CatalogVisibility] for the full contract.
     */
    private fun filterByVisibility(
        plugins: List<PluginEntity>,
        visibility: CatalogVisibility,
        data: CatalogData,
    ): List<PluginEntity> {
        val withRelease = data.latestReleases.keys
        return plugins.filter { plugin ->
            when (visibility) {
                CatalogVisibility.PUBLIC ->
                    plugin.status == PluginStatus.ACTIVE && plugin.id in withRelease

                CatalogVisibility.AUTHENTICATED ->
                    plugin.status != PluginStatus.SUSPENDED &&
                        (plugin.id in withRelease || plugin.id in data.draftOnlyPluginIds)

                CatalogVisibility.ADMIN -> true
            }
        }
    }

    /**
     * Tag, full-text (`q`), and version-compatibility filters applied in-memory. Version
     * filtering uses [latestReleases] so plugins without a published release are excluded
     * as soon as a version constraint is active.
     */
    private fun applyCatalogFilters(
        plugins: List<PluginEntity>,
        tag: String?,
        q: String?,
        version: String?,
        latestReleases: Map<java.util.UUID, PluginReleaseEntity>,
    ): List<PluginEntity> {
        val textAndTag = plugins.filter { plugin ->
            (tag == null || plugin.tags.contains(tag)) &&
                (
                    q == null || plugin.name.contains(q, ignoreCase = true) ||
                        plugin.description?.contains(q, ignoreCase = true) == true
                    )
        }
        if (version.isNullOrBlank()) return textAndTag
        return textAndTag.filter { plugin ->
            val release = latestReleases[plugin.id] ?: return@filter false
            matchesVersionConstraint(release.version, version)
        }
    }

    /**
     * In-memory sort + sublist pagination. `downloadCount` and `updatedAt` / `createdAt`
     * sorts are driven off the pre-fetched [downloadCounts] map and the plugin entity's
     * own timestamps respectively; anything else falls back to alphabetical by name.
     */
    private fun sortAndPaginate(
        plugins: List<PluginEntity>,
        pageable: Pageable,
        downloadCounts: Map<java.util.UUID, Long>,
        draftOnlyPluginIds: Set<java.util.UUID>,
    ): PagedCatalogResult {
        val sortOrder = pageable.sort.firstOrNull()
        val sorted = if (sortOrder != null) {
            val comparator: Comparator<PluginEntity> = when (sortOrder.property) {
                "downloadCount" -> compareBy { downloadCounts[it.id] ?: 0L }
                "updatedAt" -> compareBy { it.updatedAt }
                "createdAt" -> compareBy { it.createdAt }
                else -> compareBy { it.name.lowercase() }
            }
            val directed = if (sortOrder.isDescending) comparator.reversed() else comparator
            plugins.sortedWith(directed)
        } else {
            plugins
        }
        val offset = pageable.offset.toInt().coerceAtMost(sorted.size)
        val page = sorted.subList(offset, (offset + pageable.pageSize).coerceAtMost(sorted.size))
        return PagedCatalogResult(
            page = PageImpl(page, pageable, sorted.size.toLong()),
            draftOnlyPluginIds = draftOnlyPluginIds,
        )
    }

    /**
     * Bundle of the four pre-fetched query results that drive a single
     * [findPagedByNamespace] call. Keeps the orchestration body a readable handful of
     * lines and ensures each underlying query runs exactly once per request.
     */
    private data class CatalogData(
        val plugins: List<PluginEntity>,
        val latestReleases: Map<java.util.UUID, PluginReleaseEntity>,
        val draftOnlyPluginIds: Set<java.util.UUID>,
        val downloadCounts: Map<java.util.UUID, Long>,
    )

    /**
     * Checks whether a declared system version satisfies a constraint like `>=3.0.0`.
     * Uses simple numeric prefix comparison for the presets offered by the UI.
     */
    private fun matchesVersionConstraint(declaredVersion: String, constraint: String): Boolean {
        if (!constraint.startsWith(">=")) return declaredVersion == constraint
        val minVersion = constraint.removePrefix(">=")
        return compareVersions(declaredVersion, minVersion) >= 0
    }

    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val pa = partsA.getOrElse(i) { 0 }
            val pb = partsB.getOrElse(i) { 0 }
            if (pa != pb) return pa.compareTo(pb)
        }
        return 0
    }

    @Transactional
    fun create(
        namespaceSlug: String,
        pluginId: String,
        name: String,
        description: String? = null,
        provider: String? = null,
        license: String? = null,
        homepage: String? = null,
        repository: String? = null,
        icon: String? = null,
        tags: Array<String> = emptyArray(),
    ): PluginEntity {
        val namespace = namespaceService.findBySlug(namespaceSlug)
        if (pluginRepository.existsByNamespaceAndPluginId(namespace, pluginId)) {
            throw PluginAlreadyExistsException(namespaceSlug, pluginId)
        }
        return pluginRepository.save(
            PluginEntity(
                namespace = namespace,
                pluginId = pluginId,
                name = name,
                description = description,
                provider = provider,
                license = license,
                homepage = homepage,
                repository = repository,
                icon = icon,
                tags = tags,
            ),
        )
    }

    @Transactional
    fun update(
        namespaceSlug: String,
        pluginId: String,
        name: String? = null,
        description: String? = null,
        provider: String? = null,
        license: String? = null,
        homepage: String? = null,
        repository: String? = null,
        icon: String? = null,
        tags: Array<String>? = null,
        status: PluginStatus? = null,
    ): PluginEntity {
        val entity = findByNamespaceAndPluginId(namespaceSlug, pluginId)
        name?.let { entity.name = it }
        description?.let { entity.description = it }
        provider?.let { entity.provider = it }
        license?.let { entity.license = it }
        homepage?.let { entity.homepage = it }
        repository?.let { entity.repository = it }
        icon?.let { entity.icon = it }
        tags?.let { entity.tags = it }
        status?.let { entity.status = it }
        return pluginRepository.save(entity)
    }

    fun findDistinctTags(
        namespaceSlug: String,
        visibility: CatalogVisibility = CatalogVisibility.PUBLIC,
    ): List<String> {
        val namespace = namespaceService.findBySlug(namespaceSlug)
        val statuses = when (visibility) {
            CatalogVisibility.PUBLIC -> listOf(PluginStatus.ACTIVE)
            CatalogVisibility.AUTHENTICATED -> PluginStatus.entries.filter { it != PluginStatus.SUSPENDED }
            CatalogVisibility.ADMIN -> PluginStatus.entries.toList()
        }
        return pluginRepository.findTagsByNamespaceAndStatusIn(namespace, statuses)
            .flatMap { it.toList() }
            .distinct()
            .sorted()
    }

    @Transactional
    fun delete(namespaceSlug: String, pluginId: String) {
        val entity = findByNamespaceAndPluginId(namespaceSlug, pluginId)
        val releases = releaseRepository.findAllByPluginOrderByCreatedAtDesc(entity)
        releases.forEach { release ->
            storageService.delete(release.artifactKey)
            releaseRepository.delete(release)
        }
        pluginRepository.delete(entity)
    }
}
