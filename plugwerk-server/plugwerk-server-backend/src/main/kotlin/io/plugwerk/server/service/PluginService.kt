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
     * Returns a paginated, filtered list of plugins for the given namespace.
     *
     * Tag, full-text (q), version compatibility, and visibility filters are applied
     * in-memory after the DB query, which is acceptable for MVP catalog sizes.
     * A DB-level filter can replace this when catalog size demands it.
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

        // Load all plugins — optionally filtered by explicit status
        val all = if (status != null) {
            pluginRepository.findAllByNamespaceAndStatus(namespace, status)
        } else {
            pluginRepository.findAllByNamespace(namespace)
        }

        val allIds = all.mapNotNull { it.id }

        // Determine which plugins have published releases
        val pluginIdsWithPublishedRelease: Set<java.util.UUID> = if (allIds.isEmpty()) {
            emptySet()
        } else {
            releaseRepository.findLatestPublishedReleasesForPlugins(allIds)
                .map { it.plugin.id!! }
                .toSet()
        }

        // Determine which plugins are draft-only (for AUTHENTICATED/ADMIN visibility)
        val draftOnlyPluginIds: Set<java.util.UUID> =
            if (visibility != CatalogVisibility.PUBLIC && namespace.id != null) {
                releaseRepository.findPluginIdsWithOnlyDraftReleases(namespace.id!!)
            } else {
                emptySet()
            }

        // Apply visibility rules
        val visibilityFiltered = all.filter { plugin ->
            when (visibility) {
                CatalogVisibility.PUBLIC -> {
                    plugin.status == PluginStatus.ACTIVE && plugin.id in pluginIdsWithPublishedRelease
                }

                CatalogVisibility.AUTHENTICATED -> {
                    plugin.status != PluginStatus.SUSPENDED &&
                        (plugin.id in pluginIdsWithPublishedRelease || plugin.id in draftOnlyPluginIds)
                }

                CatalogVisibility.ADMIN -> true
            }
        }

        // Apply tag and full-text search filters
        val filtered = visibilityFiltered.filter { plugin ->
            (tag == null || plugin.tags.contains(tag)) &&
                (
                    q == null || plugin.name.contains(q, ignoreCase = true) ||
                        plugin.description?.contains(q, ignoreCase = true) == true
                    )
        }

        // Apply version compatibility filter
        // Filters by the plugin release version itself. Plugins whose latest release
        // version satisfies the constraint (e.g. >=2.0.0) are included.
        // Plugins without a published release are excluded when a version filter is active.
        val versionFiltered = if (version.isNullOrBlank()) {
            filtered
        } else {
            val latestReleases = if (allIds.isEmpty()) {
                emptyMap()
            } else {
                releaseRepository.findLatestPublishedReleasesForPlugins(allIds)
                    .associateBy { it.plugin.id!! }
            }
            filtered.filter { plugin ->
                val release = latestReleases[plugin.id] ?: return@filter false
                matchesVersionConstraint(release.version, version)
            }
        }

        // Apply in-memory sort (fixes downloadCount and updatedAt sorts)
        val downloadCounts: Map<java.util.UUID, Long> = if (allIds.isEmpty()) {
            emptyMap()
        } else {
            releaseRepository.sumDownloadCountsByPluginIds(allIds)
                .associate { (it[0] as java.util.UUID) to (it[1] as Long) }
        }

        val sortOrder = pageable.sort.firstOrNull()
        val sorted = if (sortOrder != null) {
            val comparator: Comparator<PluginEntity> = when (sortOrder.property) {
                "downloadCount" -> compareBy { downloadCounts[it.id] ?: 0L }
                "updatedAt" -> compareBy { it.updatedAt }
                "createdAt" -> compareBy { it.createdAt }
                else -> compareBy { it.name.lowercase() }
            }
            val directed = if (sortOrder.isDescending) comparator.reversed() else comparator
            versionFiltered.sortedWith(directed)
        } else {
            versionFiltered
        }

        val offset = pageable.offset.toInt().coerceAtMost(sorted.size)
        val page = sorted.subList(offset, (offset + pageable.pageSize).coerceAtMost(sorted.size))
        return PagedCatalogResult(
            page = PageImpl(page, pageable, sorted.size.toLong()),
            draftOnlyPluginIds = draftOnlyPluginIds,
        )
    }

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
        val all = pluginRepository.findAllByNamespace(namespace)
        val filtered = all.filter { plugin ->
            when (visibility) {
                CatalogVisibility.PUBLIC -> plugin.status == PluginStatus.ACTIVE
                CatalogVisibility.AUTHENTICATED -> plugin.status != PluginStatus.SUSPENDED
                CatalogVisibility.ADMIN -> true
            }
        }
        return filtered
            .flatMap { it.tags.toList() }
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
