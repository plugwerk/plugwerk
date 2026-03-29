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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class PluginReleaseServiceTest {

    @Mock lateinit var releaseRepository: PluginReleaseRepository

    @Mock lateinit var pluginRepository: PluginRepository

    @Mock lateinit var namespaceRepository: NamespaceRepository

    @Mock lateinit var storageService: ArtifactStorageService

    @Mock lateinit var descriptorResolver: DescriptorResolver

    lateinit var releaseService: PluginReleaseService

    private val namespaceId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val namespace = NamespaceEntity(id = namespaceId, slug = "acme", ownerOrg = "ACME Corp")
    private val plugin = PluginEntity(namespace = namespace, pluginId = "my-plugin", name = "My Plugin")

    private val properties = PlugwerkProperties(
        auth = PlugwerkProperties.AuthProperties(
            jwtSecret = "test-only-secret-not-for-production-32ch",
            encryptionKey = "test-encrypt16c!",
        ),
        upload = PlugwerkProperties.UploadProperties(maxFileSizeMb = 1),
    )

    @BeforeEach
    fun setUp() {
        releaseService = PluginReleaseService(
            releaseRepository,
            pluginRepository,
            namespaceRepository,
            storageService,
            descriptorResolver,
            ObjectMapper(),
            properties,
        )
    }

    @Test
    fun `upload creates release from descriptor and stores artifact`() {
        val jarBytes = "fake-jar-content".toByteArray()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "1.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "1.0.0")).thenReturn(false)
        whenever(
            storageService.store(any<String>(), any<java.io.InputStream>(), any<Long>()),
        ).thenReturn("00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar")
        val savedRelease = PluginReleaseEntity(
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar",
        )
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenReturn(savedRelease)

        val result = releaseService.upload("acme", ByteArrayInputStream(jarBytes), jarBytes.size.toLong())

        assertThat(result.version).isEqualTo("1.0.0")
        verify(
            storageService,
        ).store(eq("00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar"), any<java.io.InputStream>(), any<Long>())
        verify(releaseRepository).save(any<PluginReleaseEntity>())
    }

    @Test
    fun `upload stores artifact size in bytes`() {
        val jarBytes = "fake-jar-content".toByteArray()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "1.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "1.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { invocation ->
            invocation.getArgument<PluginReleaseEntity>(0)
        }

        val result = releaseService.upload("acme", ByteArrayInputStream(jarBytes), jarBytes.size.toLong())

        assertThat(result.artifactSize).isEqualTo(jarBytes.size.toLong())
    }

    @Test
    fun `upload throws ReleaseAlreadyExistsException when version exists`() {
        val jarBytes = "fake-jar-content".toByteArray()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "1.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "1.0.0")).thenReturn(true)

        assertFailsWith<ReleaseAlreadyExistsException> {
            releaseService.upload("acme", ByteArrayInputStream(jarBytes), jarBytes.size.toLong())
        }
    }

    @Test
    fun `upload auto-creates plugin when it does not exist`() {
        val jarBytes = "fake-jar-content".toByteArray()
        val descriptor = PlugwerkDescriptor(id = "new-plugin", version = "1.0.0", name = "New Plugin")
        val newPlugin = PluginEntity(namespace = namespace, pluginId = "new-plugin", name = "New Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "new-plugin")).thenReturn(Optional.empty())
        whenever(pluginRepository.save(any<PluginEntity>())).thenReturn(newPlugin)
        whenever(releaseRepository.existsByPluginAndVersion(newPlugin, "1.0.0")).thenReturn(false)
        whenever(
            storageService.store(any<String>(), any<java.io.InputStream>(), any<Long>()),
        ).thenReturn("00000000-0000-0000-0000-000000000001:new-plugin:1.0.0:jar")
        val savedRelease = PluginReleaseEntity(
            plugin = newPlugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "00000000-0000-0000-0000-000000000001:new-plugin:1.0.0:jar",
        )
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenReturn(savedRelease)

        val result = releaseService.upload("acme", ByteArrayInputStream(jarBytes), jarBytes.size.toLong())

        assertThat(result.version).isEqualTo("1.0.0")
        verify(pluginRepository).save(any<PluginEntity>())
    }

    @Test
    fun `findByVersion throws ReleaseNotFoundException when release missing`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findByPluginAndVersion(plugin, "9.9.9")).thenReturn(Optional.empty())

        assertFailsWith<ReleaseNotFoundException> {
            releaseService.findByVersion("acme", "my-plugin", "9.9.9")
        }
    }

    @Test
    fun `updateStatus changes release status`() {
        val release = PluginReleaseEntity(
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar",
        )
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findByPluginAndVersion(plugin, "1.0.0")).thenReturn(Optional.of(release))
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenReturn(release)

        releaseService.updateStatus("acme", "my-plugin", "1.0.0", ReleaseStatus.PUBLISHED)

        assertThat(release.status).isEqualTo(ReleaseStatus.PUBLISHED)
    }

    @Test
    fun `delete removes artifact and release entity`() {
        val release = PluginReleaseEntity(
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar",
        )
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findByPluginAndVersion(plugin, "1.0.0")).thenReturn(Optional.of(release))

        releaseService.delete("acme", "my-plugin", "1.0.0")

        verify(storageService).delete("00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar")
        verify(releaseRepository).delete(release)
    }

    @Test
    fun `downloadArtifact increments download counter`() {
        val releaseId = UUID.randomUUID()
        val release = PluginReleaseEntity(
            id = releaseId,
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar",
        )
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findByPluginAndVersion(plugin, "1.0.0")).thenReturn(Optional.of(release))
        whenever(storageService.retrieve(release.artifactKey)).thenReturn(ByteArrayInputStream(ByteArray(0)))

        releaseService.downloadArtifact("acme", "my-plugin", "1.0.0")

        verify(releaseRepository).incrementDownloadCount(releaseId)
    }

    @Test
    fun `upload throws FileTooLargeException when contentLength exceeds limit`() {
        val oversizedLength = 2L * 1_048_576L // 2 MB, limit is 1 MB

        assertFailsWith<FileTooLargeException> {
            releaseService.upload("acme", ByteArrayInputStream(ByteArray(0)), oversizedLength)
        }
    }

    @Test
    fun `upload throws FileTooLargeException when stream exceeds limit regardless of contentLength`() {
        val oversizedBytes = ByteArray(1_048_576 + 1) // 1 MB + 1 byte

        assertFailsWith<FileTooLargeException> {
            releaseService.upload("acme", ByteArrayInputStream(oversizedBytes), 0)
        }
    }

    @Test
    fun `upload succeeds when file is within size limit`() {
        val jarBytes = "small-content".toByteArray()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "2.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "2.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        val result = releaseService.upload("acme", ByteArrayInputStream(jarBytes), jarBytes.size.toLong())

        assertThat(result.version).isEqualTo("2.0.0")
    }
}
