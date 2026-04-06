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
import io.plugwerk.server.SharedPostgresContainer
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import kotlin.test.assertFailsWith

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    PluginReleaseService::class,
    PluginService::class,
    NamespaceService::class,
    DownloadEventService::class,
    PluginReleaseServiceIntegrationTest.MockConfig::class,
)
@Tag("integration")
class PluginReleaseServiceIntegrationTest {

    @Configuration
    class MockConfig {
        @Bean
        fun artifactStorageService(): ArtifactStorageService = mock(ArtifactStorageService::class.java).also {
            whenever(it.store(any(), any(), any())).thenAnswer { inv -> inv.arguments[0] as String }
        }

        @Bean
        fun descriptorResolver(): DescriptorResolver = mock(DescriptorResolver::class.java)

        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()
    }

    companion object {
        private val FAKE_JAR = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "fake".toByteArray()

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { SharedPostgresContainer.instance.jdbcUrl }
            registry.add("spring.datasource.username") { SharedPostgresContainer.instance.username }
            registry.add("spring.datasource.password") { SharedPostgresContainer.instance.password }
        }
    }

    @Autowired
    lateinit var releaseService: PluginReleaseService

    @Autowired
    lateinit var namespaceService: NamespaceService

    @Autowired
    lateinit var descriptorResolver: DescriptorResolver

    lateinit var testNamespace: io.plugwerk.server.domain.NamespaceEntity

    @BeforeEach
    fun setUp() {
        testNamespace = namespaceService.create("rel-int-ns", "Integration Org")
    }

    @Test
    fun `upload creates release and auto-creates plugin`() {
        val descriptor = PlugwerkDescriptor(id = "auto-plugin", version = "1.0.0", name = "Auto Plugin")
        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)

        val result = releaseService.upload("rel-int-ns", ByteArrayInputStream(FAKE_JAR), FAKE_JAR.size.toLong())

        assertThat(result.version).isEqualTo("1.0.0")
        assertThat(result.artifactKey).isEqualTo("${testNamespace.id}:auto-plugin:1.0.0:jar")
        assertThat(result.status).isEqualTo(ReleaseStatus.DRAFT)
    }

    @Test
    fun `upload throws ReleaseAlreadyExistsException on duplicate version`() {
        val descriptor = PlugwerkDescriptor(id = "dup-plugin", version = "1.0.0", name = "Dup Plugin")
        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)

        releaseService.upload("rel-int-ns", ByteArrayInputStream(FAKE_JAR), FAKE_JAR.size.toLong())

        assertFailsWith<ReleaseAlreadyExistsException> {
            releaseService.upload("rel-int-ns", ByteArrayInputStream(FAKE_JAR), FAKE_JAR.size.toLong())
        }
    }

    @Test
    fun `updateStatus transitions release to PUBLISHED`() {
        val descriptor = PlugwerkDescriptor(id = "pub-plugin", version = "1.0.0", name = "Pub Plugin")
        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        releaseService.upload("rel-int-ns", ByteArrayInputStream(FAKE_JAR), FAKE_JAR.size.toLong())

        releaseService.updateStatus("rel-int-ns", "pub-plugin", "1.0.0", ReleaseStatus.PUBLISHED)

        val found = releaseService.findByVersion("rel-int-ns", "pub-plugin", "1.0.0")
        assertThat(found.status).isEqualTo(ReleaseStatus.PUBLISHED)
    }

    @Test
    fun `findAllByPlugin returns releases ordered by createdAt desc`() {
        val d1 = PlugwerkDescriptor(id = "order-plugin", version = "1.0.0", name = "Plugin")
        val d2 = PlugwerkDescriptor(id = "order-plugin", version = "2.0.0", name = "Plugin")
        whenever(descriptorResolver.resolve(any())).thenReturn(d1).thenReturn(d2)

        releaseService.upload("rel-int-ns", ByteArrayInputStream(FAKE_JAR), FAKE_JAR.size.toLong())
        releaseService.upload("rel-int-ns", ByteArrayInputStream(FAKE_JAR), FAKE_JAR.size.toLong())

        val releases = releaseService.findAllByPlugin("rel-int-ns", "order-plugin")
        assertThat(releases).hasSize(2)
        assertThat(releases.first().version).isEqualTo("2.0.0")
    }
}
