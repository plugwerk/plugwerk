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
package io.plugwerk.server.service

import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
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
     * Returns a paginated, filtered list of plugins for the given namespace.
     *
     * Category, tag, full-text (q), and published-only filters are applied in-memory after
     * the DB query, which is acceptable for MVP catalog sizes. A DB-level filter can
     * replace this when catalog size demands it.
     *
     * @param publishedOnly when `true`, only plugins that have at least one PUBLISHED release
     *   are returned. SUSPENDED plugins are always excluded regardless of this flag.
     */
    fun findPagedByNamespace(
        namespaceSlug: String,
        status: PluginStatus?,
        category: String?,
        tag: String?,
        q: String?,
        pageable: Pageable,
        publishedOnly: Boolean = false,
    ): Page<PluginEntity> {
        val namespace = namespaceService.findBySlug(namespaceSlug)
        val all = if (status != null) {
            pluginRepository.findAllByNamespaceAndStatus(namespace, status)
        } else {
            pluginRepository.findAllByNamespace(namespace)
        }

        val pluginIdsWithPublishedRelease: Set<java.util.UUID> = if (publishedOnly) {
            val ids = all.mapNotNull { it.id }
            if (ids.isEmpty()) {
                emptySet()
            } else {
                releaseRepository.findLatestPublishedReleasesForPlugins(ids)
                    .map { it.plugin.id!! }
                    .toSet()
            }
        } else {
            emptySet()
        }

        val filtered = all
            .filter { it.status != PluginStatus.SUSPENDED }
            .filter { plugin ->
                (!publishedOnly || plugin.id in pluginIdsWithPublishedRelease) &&
                    (category == null || plugin.categories.contains(category)) &&
                    (tag == null || plugin.tags.contains(tag)) &&
                    (
                        q == null || plugin.name.contains(q, ignoreCase = true) ||
                            plugin.description?.contains(q, ignoreCase = true) == true
                        )
            }
        val offset = pageable.offset.toInt().coerceAtMost(filtered.size)
        val page = filtered.subList(offset, (offset + pageable.pageSize).coerceAtMost(filtered.size))
        return PageImpl(page, pageable, filtered.size.toLong())
    }

    @Transactional
    fun create(
        namespaceSlug: String,
        pluginId: String,
        name: String,
        description: String? = null,
        author: String? = null,
        license: String? = null,
        homepage: String? = null,
        repository: String? = null,
        icon: String? = null,
        categories: Array<String> = emptyArray(),
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
                author = author,
                license = license,
                homepage = homepage,
                repository = repository,
                icon = icon,
                categories = categories,
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
        author: String? = null,
        license: String? = null,
        homepage: String? = null,
        repository: String? = null,
        icon: String? = null,
        categories: Array<String>? = null,
        tags: Array<String>? = null,
        status: PluginStatus? = null,
    ): PluginEntity {
        val entity = findByNamespaceAndPluginId(namespaceSlug, pluginId)
        name?.let { entity.name = it }
        description?.let { entity.description = it }
        author?.let { entity.author = it }
        license?.let { entity.license = it }
        homepage?.let { entity.homepage = it }
        repository?.let { entity.repository = it }
        icon?.let { entity.icon = it }
        categories?.let { entity.categories = it }
        tags?.let { entity.tags = it }
        status?.let { entity.status = it }
        return pluginRepository.save(entity)
    }

    @Transactional
    fun delete(namespaceSlug: String, pluginId: String) {
        val entity = findByNamespaceAndPluginId(namespaceSlug, pluginId)
        pluginRepository.delete(entity)
    }
}
