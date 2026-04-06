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

import io.plugwerk.descriptor.DescriptorResolver
import io.plugwerk.descriptor.PlugwerkDescriptor
import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.FileFormat
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
import org.mockito.kotlin.never
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

    @Mock lateinit var downloadEventService: DownloadEventService

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
            downloadEventService,
        )
    }

    private fun fakeJarBytes(content: String = "fake-jar-content"): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + content.toByteArray()

    @Test
    fun `upload creates release from descriptor and stores artifact`() {
        val jarBytes = fakeJarBytes()
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
        val jarBytes = fakeJarBytes()
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
    fun `upload sets fileFormat to JAR for jar uploads`() {
        val jarBytes = fakeJarBytes()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "1.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "1.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        val result = releaseService.upload("acme", ByteArrayInputStream(jarBytes), jarBytes.size.toLong(), "plugin.jar")

        assertThat(result.fileFormat).isEqualTo(FileFormat.JAR)
    }

    @Test
    fun `upload sets fileFormat to ZIP for zip uploads`() {
        val zipBytes = fakeJarBytes("fake-zip-content")
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "1.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "1.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        val result = releaseService.upload("acme", ByteArrayInputStream(zipBytes), zipBytes.size.toLong(), "plugin.zip")

        assertThat(result.fileFormat).isEqualTo(FileFormat.ZIP)
    }

    @Test
    fun `upload defaults fileFormat to JAR when no filename provided`() {
        val jarBytes = fakeJarBytes()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "1.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "1.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        val result = releaseService.upload("acme", ByteArrayInputStream(jarBytes), jarBytes.size.toLong())

        assertThat(result.fileFormat).isEqualTo(FileFormat.JAR)
    }

    @Test
    fun `upload throws ReleaseAlreadyExistsException when version exists`() {
        val jarBytes = fakeJarBytes()
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
        val jarBytes = fakeJarBytes()
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
    fun `delete removes artifact and release entity but keeps plugin when other releases exist`() {
        val release = PluginReleaseEntity(
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar",
        )
        val otherRelease = PluginReleaseEntity(
            plugin = plugin,
            version = "2.0.0",
            artifactSha256 = "sha2",
            artifactKey = "00000000-0000-0000-0000-000000000001:my-plugin:2.0.0:jar",
        )
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findByPluginAndVersion(plugin, "1.0.0")).thenReturn(Optional.of(release))
        whenever(releaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin)).thenReturn(listOf(otherRelease))

        val pluginDeleted = releaseService.delete("acme", "my-plugin", "1.0.0")

        assertThat(pluginDeleted).isFalse()
        verify(storageService).delete("00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar")
        verify(releaseRepository).delete(release)
        verify(pluginRepository, never()).delete(any<PluginEntity>())
    }

    @Test
    fun `delete removes plugin when last release is deleted`() {
        val release = PluginReleaseEntity(
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar",
        )
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findByPluginAndVersion(plugin, "1.0.0")).thenReturn(Optional.of(release))
        whenever(releaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin)).thenReturn(emptyList())

        val pluginDeleted = releaseService.delete("acme", "my-plugin", "1.0.0")

        assertThat(pluginDeleted).isTrue()
        verify(storageService).delete("00000000-0000-0000-0000-000000000001:my-plugin:1.0.0:jar")
        verify(releaseRepository).delete(release)
        verify(pluginRepository).delete(plugin)
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
        verify(downloadEventService).record(release, null, null)
    }

    @Test
    fun `downloadArtifact forwards clientIp and userAgent to event service`() {
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

        releaseService.downloadArtifact("acme", "my-plugin", "1.0.0", "10.0.0.1", "curl/7.88")

        verify(downloadEventService).record(release, "10.0.0.1", "curl/7.88")
    }

    @Test
    fun `updateStatusByIdInNamespace updates status when namespace matches`() {
        val releaseId = UUID.randomUUID()
        val release = PluginReleaseEntity(
            id = releaseId,
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "acme:my-plugin:1.0.0:jar",
        )
        whenever(releaseRepository.findByIdWithPlugin(releaseId)).thenReturn(Optional.of(release))
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenReturn(release)

        val result = releaseService.updateStatusByIdInNamespace(releaseId, "acme", ReleaseStatus.PUBLISHED)

        assertThat(result.status).isEqualTo(ReleaseStatus.PUBLISHED)
    }

    @Test
    fun `updateStatusByIdInNamespace throws ReleaseNotFoundException when namespace does not match`() {
        val releaseId = UUID.randomUUID()
        val release = PluginReleaseEntity(
            id = releaseId,
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "acme:my-plugin:1.0.0:jar",
        )
        whenever(releaseRepository.findByIdWithPlugin(releaseId)).thenReturn(Optional.of(release))

        assertFailsWith<ReleaseNotFoundException> {
            releaseService.updateStatusByIdInNamespace(releaseId, "evil-namespace", ReleaseStatus.PUBLISHED)
        }
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
    fun `upload throws InvalidArtifactException when magic bytes are not ZIP or JAR`() {
        val invalidBytes = "this-is-not-a-jar".toByteArray()

        assertFailsWith<InvalidArtifactException> {
            releaseService.upload("acme", ByteArrayInputStream(invalidBytes), invalidBytes.size.toLong())
        }
    }

    @Test
    fun `upload throws InvalidArtifactException when file is too short for magic bytes`() {
        val tinyBytes = byteArrayOf(0x50)

        assertFailsWith<InvalidArtifactException> {
            releaseService.upload("acme", ByteArrayInputStream(tinyBytes), tinyBytes.size.toLong())
        }
    }

    @Test
    fun `upload succeeds when file is within size limit`() {
        val jarBytes = fakeJarBytes("small-content")
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
