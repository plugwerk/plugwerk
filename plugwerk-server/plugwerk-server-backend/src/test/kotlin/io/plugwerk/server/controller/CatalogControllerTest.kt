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
package io.plugwerk.server.controller

import io.plugwerk.server.controller.mapper.PluginMapper
import io.plugwerk.server.controller.mapper.PluginReleaseMapper
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.service.NamespaceNotFoundException
import io.plugwerk.server.service.Pf4jCompatibilityService
import io.plugwerk.server.service.PluginNotFoundException
import io.plugwerk.server.service.PluginReleaseService
import io.plugwerk.server.service.PluginService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.io.ByteArrayInputStream
import java.util.UUID

@WebMvcTest(
    CatalogController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class CatalogControllerTest {

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @MockitoBean lateinit var namespaceRepository: NamespaceRepository

    @MockitoBean lateinit var releaseRepository: PluginReleaseRepository

    @MockitoBean lateinit var pluginService: PluginService

    @MockitoBean lateinit var releaseService: PluginReleaseService

    @MockitoBean lateinit var pf4jService: Pf4jCompatibilityService

    @MockitoBean lateinit var pluginMapper: PluginMapper

    @MockitoBean lateinit var releaseMapper: PluginReleaseMapper

    @MockitoBean lateinit var namespaceAuthService: NamespaceAuthorizationService

    @Autowired private lateinit var mockMvc: MockMvc

    private val namespace = NamespaceEntity(slug = "acme", name = "ACME Corp")
    private val plugin = PluginEntity(
        id = UUID.randomUUID(),
        namespace = namespace,
        pluginId = "my-plugin",
        name = "My Plugin",
    )

    @Test
    fun `GET plugins returns 200 with paged response`() {
        whenever(
            pluginService.findPagedByNamespace(
                eq("acme"),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any<Pageable>(),
                any(),
                anyOrNull(),
            ),
        )
            .thenReturn(PluginService.PagedCatalogResult(PageImpl(listOf(plugin)), emptySet()))
        whenever(
            pluginMapper.toDto(any(), eq("acme"), anyOrNull(), any(), any()),
        ).thenReturn(buildPluginDto())

        mockMvc.get("/api/v1/namespaces/acme/plugins")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.content") { isArray() }
                jsonPath("$.totalElements") { value(1) }
            }
    }

    @Test
    fun `GET plugins returns 404 when namespace not found`() {
        whenever(
            pluginService.findPagedByNamespace(
                eq("unknown"),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any<Pageable>(),
                any(),
                anyOrNull(),
            ),
        )
            .thenThrow(NamespaceNotFoundException("unknown"))

        mockMvc.get("/api/v1/namespaces/unknown/plugins")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
    }

    @Test
    fun `GET plugin by id returns 200`() {
        whenever(pluginService.findByNamespaceAndPluginId("acme", "my-plugin")).thenReturn(plugin)
        whenever(releaseService.findAllByPlugin("acme", "my-plugin")).thenReturn(emptyList())
        whenever(
            pluginMapper.toDto(any(), eq("acme"), anyOrNull(), any(), any()),
        ).thenReturn(buildPluginDto())

        mockMvc.get("/api/v1/namespaces/acme/plugins/my-plugin")
            .andExpect {
                status { isOk() }
                jsonPath("$.pluginId") { value("my-plugin") }
            }
    }

    @Test
    fun `GET plugin by id returns 404 when not found`() {
        whenever(pluginService.findByNamespaceAndPluginId("acme", "missing"))
            .thenThrow(PluginNotFoundException("acme", "missing"))

        mockMvc.get("/api/v1/namespaces/acme/plugins/missing")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
    }

    @Test
    fun `GET releases returns 200 with paged response`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc123",
            artifactKey = "acme/my-plugin/1.0.0",
        )
        whenever(pluginService.findByNamespaceAndPluginId("acme", "my-plugin")).thenReturn(plugin)
        whenever(releaseService.findPagedByPlugin(eq(plugin), any<Pageable>()))
            .thenReturn(PageImpl(listOf(release)))
        whenever(releaseMapper.toDto(any(), eq("my-plugin"))).thenReturn(buildReleaseDto())

        mockMvc.get("/api/v1/namespaces/acme/plugins/my-plugin/releases")
            .andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.totalElements") { value(1) }
            }
    }

    @Test
    fun `GET release by version returns 200`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc123",
            artifactKey = "acme/my-plugin/1.0.0",
        )
        whenever(releaseService.findByVersion("acme", "my-plugin", "1.0.0")).thenReturn(release)
        whenever(releaseMapper.toDto(any(), eq("my-plugin"))).thenReturn(buildReleaseDto())

        mockMvc.get("/api/v1/namespaces/acme/plugins/my-plugin/releases/1.0.0")
            .andExpect {
                status { isOk() }
                jsonPath("$.version") { value("1.0.0") }
            }
    }

    @Test
    fun `GET download returns 200 with octet-stream`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc",
            artifactKey = "acme/my-plugin/1.0.0.jar",
        )
        whenever(releaseService.findByVersion("acme", "my-plugin", "1.0.0")).thenReturn(release)
        whenever(releaseService.downloadArtifact(eq("acme"), eq("my-plugin"), eq("1.0.0"), anyOrNull(), anyOrNull()))
            .thenReturn(ByteArrayInputStream("fake-jar-content".toByteArray()))

        mockMvc.get("/api/v1/namespaces/acme/plugins/my-plugin/releases/1.0.0/download")
            .andExpect {
                status { isOk() }
                header { string("Content-Disposition", "attachment; filename=\"my-plugin-1.0.0.jar\"") }
            }
    }

    private fun buildPluginDto() = io.plugwerk.api.model.PluginDto(
        id = plugin.id!!,
        pluginId = "my-plugin",
        name = "My Plugin",
        status = io.plugwerk.api.model.PluginDto.Status.ACTIVE,
    )

    private fun buildReleaseDto() = io.plugwerk.api.model.PluginReleaseDto(
        id = UUID.randomUUID(),
        pluginId = "my-plugin",
        version = "1.0.0",
        status = io.plugwerk.api.model.PluginReleaseDto.Status.PUBLISHED,
    )
}
