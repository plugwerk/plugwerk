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
import io.plugwerk.server.repository.DownloadEventRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
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

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    DownloadEventService::class,
    PluginReleaseService::class,
    PluginService::class,
    NamespaceService::class,
    DownloadEventServiceIntegrationTest.MockConfig::class,
)
@Tag("integration")
class DownloadEventServiceIntegrationTest {

    @Configuration
    class MockConfig {
        @Bean
        fun artifactStorageService(): ArtifactStorageService = mock(ArtifactStorageService::class.java).also {
            whenever(it.store(any(), any(), any())).thenAnswer { inv -> inv.arguments[0] as String }
            whenever(it.retrieve(any())).thenReturn(ByteArrayInputStream(ByteArray(0)))
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

    @Autowired lateinit var downloadEventService: DownloadEventService

    @Autowired lateinit var downloadEventRepository: DownloadEventRepository

    @Autowired lateinit var releaseService: PluginReleaseService

    @Autowired lateinit var namespaceService: NamespaceService

    @Autowired lateinit var descriptorResolver: DescriptorResolver

    lateinit var testNamespace: io.plugwerk.server.domain.NamespaceEntity

    @BeforeEach
    fun setUp() {
        testNamespace = namespaceService.create("dl-event-ns", "DL Event Org")
    }

    @Test
    fun `record persists download event with correct release FK`() {
        val descriptor = PlugwerkDescriptor(id = "evt-plugin", version = "1.0.0", name = "Event Plugin")
        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        val release = releaseService.upload("dl-event-ns", ByteArrayInputStream(FAKE_JAR), FAKE_JAR.size.toLong())

        downloadEventService.record(release, "10.20.30.40", "curl/7.88")

        val events = downloadEventRepository.findAll()
        assertThat(events).hasSize(1)
        assertThat(events[0].release.id).isEqualTo(release.id)
        assertThat(events[0].clientIp).isEqualTo("10.20.30.0")
        assertThat(events[0].userAgent).isEqualTo("curl/7.88")
        assertThat(events[0].downloadedAt).isNotNull()
    }

    @Test
    fun `cascade delete removes events when release is deleted`() {
        val descriptor = PlugwerkDescriptor(id = "cascade-plugin", version = "1.0.0", name = "Cascade Plugin")
        whenever(descriptorResolver.resolve(any())).thenReturn(descriptor)
        val release = releaseService.upload("dl-event-ns", ByteArrayInputStream(FAKE_JAR), FAKE_JAR.size.toLong())

        downloadEventService.record(release, "1.2.3.4", null)
        downloadEventService.record(release, "5.6.7.8", null)
        assertThat(downloadEventRepository.findAll()).hasSize(2)

        releaseService.delete("dl-event-ns", "cascade-plugin", "1.0.0")

        assertThat(downloadEventRepository.findAll()).isEmpty()
    }
}
