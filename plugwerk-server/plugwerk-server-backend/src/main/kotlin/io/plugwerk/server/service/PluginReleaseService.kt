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

import io.plugwerk.common.model.ReleaseStatus
import io.plugwerk.descriptor.DescriptorResolver
import io.plugwerk.descriptor.PlugwerkDescriptor
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

@Service
@Transactional(readOnly = true)
class PluginReleaseService(
    private val releaseRepository: PluginReleaseRepository,
    private val pluginRepository: PluginRepository,
    private val namespaceRepository: NamespaceRepository,
    private val storageService: ArtifactStorageService,
    private val descriptorResolver: DescriptorResolver,
    private val objectMapper: ObjectMapper,
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

    fun downloadArtifact(namespaceSlug: String, pluginId: String, version: String): InputStream {
        val release = findByVersion(namespaceSlug, pluginId, version)
        return storageService.retrieve(release.artifactKey)
    }

    @Transactional
    fun upload(namespaceSlug: String, content: InputStream, contentLength: Long): PluginReleaseEntity {
        val bytes = content.readAllBytes()
        val descriptor = descriptorResolver.resolve(ByteArrayInputStream(bytes))
        val sha256 = computeSha256(bytes)
        val plugin = findOrCreatePlugin(namespaceSlug, descriptor)

        if (releaseRepository.existsByPluginAndVersion(plugin, descriptor.version)) {
            throw ReleaseAlreadyExistsException(descriptor.id, descriptor.version)
        }

        val artifactKey = "$namespaceSlug/${descriptor.id}/${descriptor.version}"
        storageService.store(artifactKey, ByteArrayInputStream(bytes), bytes.size.toLong())

        return releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = descriptor.version,
                artifactSha256 = sha256,
                artifactKey = artifactKey,
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
