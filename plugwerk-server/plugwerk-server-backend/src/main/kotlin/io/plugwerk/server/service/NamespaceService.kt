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
import io.plugwerk.server.service.settings.UserSettingsService
import io.plugwerk.server.service.storage.ArtifactStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NamespaceService(
    private val namespaceRepository: NamespaceRepository,
    private val pluginRepository: PluginRepository,
    private val pluginReleaseRepository: PluginReleaseRepository,
    private val storageService: ArtifactStorageService,
    private val userSettingsService: UserSettingsService,
    private val namespaceDeletionTransaction: NamespaceDeletionTransaction,
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

    /**
     * Deletes a namespace and all its dependent rows. Two-phase split (#481):
     * DB cleanup runs in [NamespaceDeletionTransaction.deleteFromDb] inside
     * its own transaction, then storage cleanup runs *outside* any
     * transaction as a best-effort log-and-continue loop.
     *
     * `@Transactional(propagation = NOT_SUPPORTED)` enforces and documents
     * that this method must not run inside a caller-provided transaction —
     * such an outer TX would re-extend its boundary to wrap the storage
     * loop, recreating the original issue. See `PluginService.delete` for
     * the same pattern and rationale.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun delete(slug: String) {
        val artifactKeys = namespaceDeletionTransaction.deleteFromDb(slug)
        deleteArtifactsBestEffort(artifactKeys, slug)
    }

    private fun deleteArtifactsBestEffort(keys: List<String>, slug: String) {
        keys.forEach { key ->
            try {
                storageService.delete(key)
            } catch (ex: Exception) {
                log.warn(
                    "Failed to delete artifact '{}' for namespace '{}': {}",
                    key,
                    slug,
                    ex.message,
                )
            }
        }
    }
}
