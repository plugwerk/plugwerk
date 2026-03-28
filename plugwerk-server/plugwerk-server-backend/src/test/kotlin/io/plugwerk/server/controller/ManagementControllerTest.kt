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
package io.plugwerk.server.controller

import io.plugwerk.descriptor.DescriptorNotFoundException
import io.plugwerk.descriptor.DescriptorParseException
import io.plugwerk.server.controller.mapper.PluginMapper
import io.plugwerk.server.controller.mapper.PluginReleaseMapper
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.service.PluginNotFoundException
import io.plugwerk.server.service.PluginReleaseService
import io.plugwerk.server.service.PluginService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

@WithMockUser
@WebMvcTest(
    ManagementController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class ManagementControllerTest {

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @MockitoBean lateinit var namespaceAuthorizationService: NamespaceAuthorizationService

    @MockitoBean lateinit var pluginService: PluginService

    @MockitoBean lateinit var releaseService: PluginReleaseService

    @MockitoBean lateinit var pluginMapper: PluginMapper

    @MockitoBean lateinit var releaseMapper: PluginReleaseMapper

    @Autowired private lateinit var mockMvc: MockMvc

    private val namespace = NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp")
    private val plugin = PluginEntity(
        id = UUID.randomUUID(),
        namespace = namespace,
        pluginId = "my-plugin",
        name = "My Plugin",
    )

    @Test
    fun `PATCH plugin returns 200 with updated plugin`() {
        // update() has 12 params: namespaceSlug, pluginId, name?, description?, author?, license?,
        // homepage?, repository?, icon?, categories?, tags?, status?
        whenever(
            pluginService.update(
                any(), any(),
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
            ),
        ).thenReturn(plugin)
        whenever(pluginMapper.toDto(any(), any(), anyOrNull())).thenReturn(buildPluginDto())

        mockMvc.patch("/api/v1/namespaces/acme/plugins/my-plugin") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Updated Name"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `PATCH plugin returns 404 when not found`() {
        whenever(
            pluginService.update(
                any(), any(),
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
            ),
        ).thenThrow(PluginNotFoundException("acme", "missing"))

        mockMvc.patch("/api/v1/namespaces/acme/plugins/missing") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"X"}"""
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST release upload returns 201`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc123",
            artifactKey = "acme/my-plugin/1.0.0",
        )
        whenever(releaseService.upload(any(), any(), any(), anyOrNull())).thenReturn(release)
        whenever(releaseMapper.toDto(any(), any())).thenReturn(buildReleaseDto())

        val artifact =
            MockMultipartFile("artifact", "my-plugin-1.0.0.jar", "application/octet-stream", "fake".toByteArray())

        mockMvc.multipart("/api/v1/namespaces/acme/plugin-releases") {
            file(artifact)
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `PATCH release status returns 200`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc123",
            artifactKey = "acme/my-plugin/1.0.0",
        )
        whenever(releaseService.updateStatus(any(), any(), any(), any())).thenReturn(release)
        whenever(releaseMapper.toDto(any(), any())).thenReturn(buildReleaseDto())

        mockMvc.patch("/api/v1/namespaces/acme/plugins/my-plugin/releases/1.0.0") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"published"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `POST release upload returns 422 when descriptor not found in JAR`() {
        val artifact =
            MockMultipartFile("artifact", "invalid.jar", "application/octet-stream", "not-a-jar".toByteArray())
        whenever(releaseService.upload(any(), any(), any(), anyOrNull()))
            .thenThrow(
                DescriptorNotFoundException(
                    "No descriptor found in JAR (tried plugwerk.yml, MANIFEST.MF, plugin.properties)",
                ),
            )

        mockMvc.multipart("/api/v1/namespaces/acme/plugin-releases") {
            file(artifact)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.status") { value(422) }
            jsonPath("$.message") {
                value("No descriptor found in JAR (tried plugwerk.yml, MANIFEST.MF, plugin.properties)")
            }
        }
    }

    @Test
    fun `POST release upload returns 422 when descriptor cannot be parsed`() {
        val artifact =
            MockMultipartFile("artifact", "broken.jar", "application/octet-stream", "not-a-jar".toByteArray())
        whenever(releaseService.upload(any(), any(), any(), anyOrNull()))
            .thenThrow(DescriptorParseException("Invalid plugin.id in MANIFEST.MF"))

        mockMvc.multipart("/api/v1/namespaces/acme/plugin-releases") {
            file(artifact)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.status") { value(422) }
            jsonPath("$.message") { value("Invalid plugin.id in MANIFEST.MF") }
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
