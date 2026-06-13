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
import io.plugwerk.descriptor.PluginDependency
import io.plugwerk.descriptor.PlugwerkDescriptor
import io.plugwerk.server.domain.FileFormat
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.settings.ApplicationSettingsService
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.server.service.telemetry.ActivationTelemetry
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Branch-coverage-focused tests for [PluginReleaseService]. Targets conditional
 * arms not exercised by [PluginReleaseServiceTest]: the namespace-not-found and
 * plugin-not-found `orElseThrow` arms of [resolvePlugin]/[findOrCreatePlugin],
 * the not-found arms of [findById]/[findByIdWithPlugin], the
 * [findPendingByNamespace] missing-namespace path, the `enforceNamespace = false`
 * skip path of [updateStatusByIdInNamespace], the upload filename-extension
 * `?.takeIf` fallback branches, the [findOrCreatePlugin] `orElseGet` save path
 * (full descriptor mapping), and the dependency-serialization empty-vs-nonempty
 * branch.
 */
@ExtendWith(MockitoExtension::class)
class PluginReleaseServiceBranchCoverageTest {

    @Mock lateinit var releaseRepository: PluginReleaseRepository

    @Mock lateinit var pluginRepository: PluginRepository

    @Mock lateinit var namespaceRepository: NamespaceRepository

    @Mock lateinit var storageService: ArtifactStorageService

    @Mock lateinit var descriptorResolver: DescriptorResolver

    @Mock lateinit var downloadEventService: DownloadEventService

    @Mock lateinit var activationTelemetry: ActivationTelemetry

    lateinit var releaseService: PluginReleaseService

    private val namespaceId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val namespace = NamespaceEntity(id = namespaceId, slug = "acme", name = "ACME Corp")
    private val plugin = PluginEntity(namespace = namespace, pluginId = "my-plugin", name = "My Plugin")

    private val settingsService: ApplicationSettingsService = mock<ApplicationSettingsService>().also {
        whenever(it.maxUploadSizeMb()).thenReturn(1)
    }

    @BeforeEach
    fun setUp() {
        releaseService = PluginReleaseService(
            releaseRepository,
            pluginRepository,
            namespaceRepository,
            storageService,
            descriptorResolver,
            ObjectMapper(),
            settingsService,
            downloadEventService,
            activationTelemetry,
        )
    }

    private fun fakeJarBytes(content: String = "fake-jar-content"): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + content.toByteArray()

    // ---- resolvePlugin: namespace-not-found vs plugin-not-found ---------------------------

    @Test
    fun `findAllByPlugin throws NamespaceNotFoundException when namespace missing`() {
        whenever(namespaceRepository.findBySlug("ghost")).thenReturn(Optional.empty())

        assertFailsWith<NamespaceNotFoundException> {
            releaseService.findAllByPlugin("ghost", "my-plugin")
        }
        verify(pluginRepository, never()).findByNamespaceAndPluginId(any(), any())
    }

    @Test
    fun `findAllByPlugin throws PluginNotFoundException when plugin missing in existing namespace`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "missing")).thenReturn(Optional.empty())

        assertFailsWith<PluginNotFoundException> {
            releaseService.findAllByPlugin("acme", "missing")
        }
    }

    @Test
    fun `findAllByPlugin returns releases when plugin resolves`() {
        val release = PluginReleaseEntity(
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "key",
        )
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin)).thenReturn(listOf(release))

        val result = releaseService.findAllByPlugin("acme", "my-plugin")

        assertThat(result).hasSize(1)
    }

    // ---- findById / findByIdWithPlugin: not-found arms ------------------------------------

    @Test
    fun `findById throws ReleaseNotFoundException when id is unknown`() {
        val id = UUID.randomUUID()
        whenever(releaseRepository.findById(id)).thenReturn(Optional.empty())

        assertFailsWith<ReleaseNotFoundException> { releaseService.findById(id) }
    }

    @Test
    fun `findById returns release when present`() {
        val id = UUID.randomUUID()
        val release = PluginReleaseEntity(
            id = id,
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "key",
        )
        whenever(releaseRepository.findById(id)).thenReturn(Optional.of(release))

        assertThat(releaseService.findById(id).id).isEqualTo(id)
    }

    @Test
    fun `findByIdWithPlugin throws ReleaseNotFoundException when id is unknown`() {
        val id = UUID.randomUUID()
        whenever(releaseRepository.findByIdWithPlugin(id)).thenReturn(Optional.empty())

        assertFailsWith<ReleaseNotFoundException> { releaseService.findByIdWithPlugin(id) }
    }

    // ---- findPendingByNamespace: missing-namespace arm ------------------------------------

    @Test
    fun `findPendingByNamespace throws NamespaceNotFoundException when namespace missing`() {
        whenever(namespaceRepository.findBySlug("ghost")).thenReturn(Optional.empty())

        assertFailsWith<NamespaceNotFoundException> {
            releaseService.findPendingByNamespace("ghost")
        }
    }

    @Test
    fun `findPendingByNamespace returns draft releases when namespace resolves`() {
        val release = PluginReleaseEntity(
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "key",
            status = ReleaseStatus.DRAFT,
        )
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(releaseRepository.findPendingByNamespace(namespace, ReleaseStatus.DRAFT))
            .thenReturn(listOf(release))

        val result = releaseService.findPendingByNamespace("acme")

        assertThat(result).hasSize(1)
    }

    // ---- updateStatusByIdInNamespace: enforceNamespace = false skips the check ------------

    @Test
    fun `updateStatusByIdInNamespace skips namespace check when enforceNamespace is false`() {
        val id = UUID.randomUUID()
        val release = PluginReleaseEntity(
            id = id,
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = "key",
        )
        whenever(releaseRepository.findByIdWithPlugin(id)).thenReturn(Optional.of(release))
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenReturn(release)

        // namespace deliberately mismatched; with enforceNamespace=false the && short-circuits
        // on the first operand and the throw is never reached.
        val result = releaseService.updateStatusByIdInNamespace(
            id,
            "totally-different-namespace",
            ReleaseStatus.YANKED,
            enforceNamespace = false,
        )

        assertThat(result.status).isEqualTo(ReleaseStatus.YANKED)
    }

    // ---- upload: filename-extension takeIf fallback branches ------------------------------

    @Test
    fun `upload falls back to JAR extension when filename extension is unsupported`() {
        val bytes = fakeJarBytes()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "5.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "5.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        // "tar.gz" -> substringAfterLast('.') == "gz", which is neither zip nor jar,
        // so takeIf returns null and the elvis falls back to "jar".
        val result = releaseService.upload(
            "acme",
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
            "plugin.tar.gz",
        )

        assertThat(result.fileFormat).isEqualTo(FileFormat.JAR)
        val keyCaptor = argumentCaptor<String>()
        verify(storageService).store(keyCaptor.capture(), any(), any())
        assertThat(keyCaptor.firstValue).endsWith(":jar")
    }

    @Test
    fun `upload treats uppercase ZIP extension case-insensitively`() {
        val bytes = fakeJarBytes("zip-content")
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "6.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "6.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        // ".ZIP" is lowercased before the takeIf, so the zip arm is taken.
        val result = releaseService.upload(
            "acme",
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
            "plugin.ZIP",
        )

        assertThat(result.fileFormat).isEqualTo(FileFormat.ZIP)
    }

    // ---- upload: findOrCreatePlugin orElseGet save path + full descriptor mapping ---------

    @Test
    fun `upload auto-creates plugin mapping every optional descriptor field`() {
        val bytes = fakeJarBytes()
        val descriptor = PlugwerkDescriptor(
            id = "rich-plugin",
            version = "1.0.0",
            name = "Rich Plugin",
            description = "Rich description",
            provider = "Acme Inc",
            license = "Apache-2.0",
            tags = listOf("a", "b"),
            requiresSystemVersion = ">=2.0.0",
            pluginDependencies = listOf(PluginDependency(id = "dep", version = ">=1.0.0")),
            icon = "https://icon",
            homepage = "https://home",
            repository = "https://repo",
        )
        val created = PluginEntity(namespace = namespace, pluginId = "rich-plugin", name = "Rich Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "rich-plugin"))
            .thenReturn(Optional.empty())
        whenever(pluginRepository.save(any<PluginEntity>())).thenReturn(created)
        whenever(releaseRepository.existsByPluginAndVersion(created, "1.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        val result = releaseService.upload("acme", ByteArrayInputStream(bytes), bytes.size.toLong())

        // orElseGet branch: plugin saved from the descriptor.
        val pluginCaptor = argumentCaptor<PluginEntity>()
        verify(pluginRepository).save(pluginCaptor.capture())
        val savedPlugin = pluginCaptor.firstValue
        assertThat(savedPlugin.pluginId).isEqualTo("rich-plugin")
        assertThat(savedPlugin.description).isEqualTo("Rich description")
        assertThat(savedPlugin.provider).isEqualTo("Acme Inc")
        assertThat(savedPlugin.license).isEqualTo("Apache-2.0")
        assertThat(savedPlugin.homepage).isEqualTo("https://home")
        assertThat(savedPlugin.repository).isEqualTo("https://repo")
        assertThat(savedPlugin.icon).isEqualTo("https://icon")
        assertThat(savedPlugin.tags).containsExactly("a", "b")

        // serializeDependencies non-empty branch: dependencies serialized into the release.
        assertThat(result.pluginDependencies).contains("\"id\":\"dep\"")
        assertThat(result.requiresSystemVersion).isEqualTo(">=2.0.0")
    }

    @Test
    fun `upload leaves pluginDependencies null when descriptor declares none`() {
        val bytes = fakeJarBytes()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "7.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "7.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        val result = releaseService.upload("acme", ByteArrayInputStream(bytes), bytes.size.toLong())

        // serializeDependencies empty branch: null returned, no `orElseGet` plugin save.
        assertThat(result.pluginDependencies).isNull()
        verify(pluginRepository, never()).save(any<PluginEntity>())
    }

    @Test
    fun `upload throws NamespaceNotFoundException when namespace missing during findOrCreatePlugin`() {
        val bytes = fakeJarBytes()
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "1.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("ghost")).thenReturn(Optional.empty())

        assertFailsWith<NamespaceNotFoundException> {
            releaseService.upload("ghost", ByteArrayInputStream(bytes), bytes.size.toLong())
        }
        verify(pluginRepository, never()).save(any<PluginEntity>())
    }

    // ---- upload: contentLength <= 0 skips the early size guard ----------------------------

    @Test
    fun `upload skips early content-length guard when contentLength is zero`() {
        // contentLength == 0 means the `contentLength > 0` operand short-circuits the &&
        // so the early FileTooLargeException is bypassed; a small valid jar still uploads.
        val bytes = fakeJarBytes("tiny")
        val descriptor = PlugwerkDescriptor(id = "my-plugin", version = "8.0.0", name = "My Plugin")

        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.existsByPluginAndVersion(plugin, "8.0.0")).thenReturn(false)
        whenever(storageService.store(any(), any(), any())).thenReturn("key")
        whenever(releaseRepository.save(any<PluginReleaseEntity>())).thenAnswer { it.getArgument(0) }

        val result = releaseService.upload("acme", ByteArrayInputStream(bytes), 0)

        assertThat(result.version).isEqualTo("8.0.0")
    }
}
