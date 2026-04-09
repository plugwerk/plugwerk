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

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NamespaceService(
    private val namespaceRepository: NamespaceRepository,
    private val pluginRepository: PluginRepository,
    private val pluginReleaseRepository: PluginReleaseRepository,
    private val storageService: ArtifactStorageService,
) {

    private val log = LoggerFactory.getLogger(NamespaceService::class.java)

    fun findBySlug(slug: String): NamespaceEntity = namespaceRepository.findBySlug(slug)
        .orElseThrow { NamespaceNotFoundException(slug) }

    fun findAll(): List<NamespaceEntity> = namespaceRepository.findAll()

    @Transactional
    fun create(
        slug: String,
        name: String,
        description: String? = null,
        publicCatalog: Boolean = false,
        autoApproveReleases: Boolean = false,
    ): NamespaceEntity {
        if (namespaceRepository.existsBySlug(slug)) throw NamespaceAlreadyExistsException(slug)
        return namespaceRepository.save(
            NamespaceEntity(
                slug = slug,
                name = name,
                description = description,
                publicCatalog = publicCatalog,
                autoApproveReleases = autoApproveReleases,
            ),
        )
    }

    @Transactional
    fun update(
        slug: String,
        name: String,
        description: String? = null,
        publicCatalog: Boolean? = null,
        autoApproveReleases: Boolean? = null,
    ): NamespaceEntity {
        val entity = findBySlug(slug)
        entity.name = name
        description?.let { entity.description = it }
        publicCatalog?.let { entity.publicCatalog = it }
        autoApproveReleases?.let { entity.autoApproveReleases = it }
        return namespaceRepository.save(entity)
    }

    @Transactional
    fun delete(slug: String) {
        val entity = findBySlug(slug)
        deleteStorageArtifacts(entity)
        namespaceRepository.delete(entity)
    }

    private fun deleteStorageArtifacts(namespace: NamespaceEntity) {
        val plugins = pluginRepository.findAllByNamespace(namespace)
        for (plugin in plugins) {
            val releases = pluginReleaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin)
            for (release in releases) {
                try {
                    storageService.delete(release.artifactKey)
                } catch (ex: Exception) {
                    log.warn(
                        "Failed to delete artifact '{}' for plugin '{}' in namespace '{}': {}",
                        release.artifactKey,
                        plugin.pluginId,
                        namespace.slug,
                        ex.message,
                    )
                }
            }
        }
    }
}
