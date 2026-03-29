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

import io.plugwerk.descriptor.DescriptorResolver
import io.plugwerk.descriptor.PlugwerkDescriptor
import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.spi.model.ReleaseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PluginReleaseService(
    private val releaseRepository: PluginReleaseRepository,
    private val pluginRepository: PluginRepository,
    private val namespaceRepository: NamespaceRepository,
    private val storageService: ArtifactStorageService,
    private val descriptorResolver: DescriptorResolver,
    private val objectMapper: ObjectMapper,
    private val properties: PlugwerkProperties,
) {

    fun findAllByPlugin(namespaceSlug: String, pluginId: String): List<PluginReleaseEntity> {
        val plugin = resolvePlugin(namespaceSlug, pluginId)
        return releaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin)
    }

    fun findByVersion(namespaceSlug: String, pluginId: String, version: String): PluginReleaseEntity {
        val plugin = resolvePlugin(namespaceSlug, pluginId)
        return releaseRepository.findByPluginAndVersion(plugin, version)
            .orElseThrow { ReleaseNotFoundException(pluginId, version) }
    }

    fun findPagedByPlugin(plugin: PluginEntity, pageable: Pageable): Page<PluginReleaseEntity> =
        releaseRepository.findAllByPlugin(plugin, pageable)

    fun findById(id: UUID): PluginReleaseEntity = releaseRepository.findById(id)
        .orElseThrow { ReleaseNotFoundException("id=$id", "") }

    fun findPendingByNamespace(namespaceSlug: String): List<PluginReleaseEntity> {
        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }
        return releaseRepository.findPendingByNamespace(namespace, ReleaseStatus.DRAFT)
    }

    /**
     * Updates the status of a release, verifying it belongs to the given namespace.
     *
     * @param enforceNamespace when `false` the namespace check is skipped (superadmin use-case).
     */
    @Transactional
    fun updateStatusByIdInNamespace(
        id: UUID,
        namespaceSlug: String,
        status: ReleaseStatus,
        enforceNamespace: Boolean = true,
    ): PluginReleaseEntity {
        val release = findById(id)
        if (enforceNamespace && release.plugin.namespace.slug != namespaceSlug) {
            throw ReleaseNotFoundException("id=$id", "")
        }
        release.status = status
        return releaseRepository.save(release)
    }

    @Transactional
    fun downloadArtifact(namespaceSlug: String, pluginId: String, version: String): InputStream {
        val release = findByVersion(namespaceSlug, pluginId, version)
        val stream = storageService.retrieve(release.artifactKey)
        releaseRepository.incrementDownloadCount(release.id!!)
        return stream
    }

    @Transactional
    fun upload(
        namespaceSlug: String,
        content: InputStream,
        contentLength: Long,
        originalFilename: String? = null,
    ): PluginReleaseEntity {
        val maxBytes = properties.upload.maxFileSizeMb.toLong() * 1_048_576L

        if (contentLength > 0 && contentLength > maxBytes) {
            throw FileTooLargeException(contentLength, properties.upload.maxFileSizeMb)
        }

        val bytes = content.readNBytes(maxBytes.toInt() + 1)
        if (bytes.size > maxBytes) {
            throw FileTooLargeException(bytes.size.toLong(), properties.upload.maxFileSizeMb)
        }
        val descriptor = descriptorResolver.resolve(ByteArrayInputStream(bytes))
        val sha256 = computeSha256(bytes)
        val plugin = findOrCreatePlugin(namespaceSlug, descriptor)

        if (releaseRepository.existsByPluginAndVersion(plugin, descriptor.version)) {
            throw ReleaseAlreadyExistsException(descriptor.id, descriptor.version)
        }

        val extension = originalFilename?.substringAfterLast('.')?.lowercase()
            ?.takeIf { it == "zip" || it == "jar" } ?: "jar"
        val artifactKey = "${plugin.namespace.id}:${descriptor.id}:${descriptor.version}:$extension"
        storageService.store(artifactKey, ByteArrayInputStream(bytes), bytes.size.toLong())

        return releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = descriptor.version,
                artifactSha256 = sha256,
                artifactKey = artifactKey,
                artifactSize = bytes.size.toLong(),
                requiresSystemVersion = descriptor.requiresSystemVersion,
                pluginDependencies = serializeDependencies(descriptor),
            ),
        )
    }

    @Transactional
    fun updateStatus(
        namespaceSlug: String,
        pluginId: String,
        version: String,
        status: ReleaseStatus,
    ): PluginReleaseEntity {
        val release = findByVersion(namespaceSlug, pluginId, version)
        release.status = status
        return releaseRepository.save(release)
    }

    @Transactional
    fun delete(namespaceSlug: String, pluginId: String, version: String) {
        val release = findByVersion(namespaceSlug, pluginId, version)
        storageService.delete(release.artifactKey)
        releaseRepository.delete(release)
    }

    private fun resolvePlugin(namespaceSlug: String, pluginId: String): PluginEntity {
        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }
        return pluginRepository.findByNamespaceAndPluginId(namespace, pluginId)
            .orElseThrow { PluginNotFoundException(namespaceSlug, pluginId) }
    }

    private fun findOrCreatePlugin(namespaceSlug: String, descriptor: PlugwerkDescriptor): PluginEntity {
        val namespace: NamespaceEntity = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }
        return pluginRepository.findByNamespaceAndPluginId(namespace, descriptor.id)
            .orElseGet {
                pluginRepository.save(
                    PluginEntity(
                        namespace = namespace,
                        pluginId = descriptor.id,
                        name = descriptor.name,
                        description = descriptor.description,
                        author = descriptor.author,
                        license = descriptor.license,
                        homepage = descriptor.homepage,
                        repository = descriptor.repository,
                        icon = descriptor.icon,
                        categories = descriptor.categories.toTypedArray(),
                        tags = descriptor.tags.toTypedArray(),
                    ),
                )
            }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun serializeDependencies(descriptor: PlugwerkDescriptor): String? {
        if (descriptor.pluginDependencies.isEmpty()) return null
        val deps = descriptor.pluginDependencies.map { mapOf("id" to it.id, "version" to it.version) }
        return objectMapper.writeValueAsString(deps)
    }
}
