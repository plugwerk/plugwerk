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

import io.plugwerk.server.controller.mapper.PluginReleaseMapper
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.service.PluginReleaseService
import io.plugwerk.server.service.ReleaseNotFoundException
import io.plugwerk.spi.model.ReleaseStatus
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@WithMockUser
@WebMvcTest(
    ReviewsController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class ReviewsControllerTest {

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @MockitoBean lateinit var namespaceAuthorizationService: NamespaceAuthorizationService

    @MockitoBean lateinit var releaseService: PluginReleaseService

    @MockitoBean lateinit var releaseMapper: PluginReleaseMapper

    @Autowired private lateinit var mockMvc: MockMvc

    private val namespace = NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp")
    private val plugin = PluginEntity(
        id = UUID.randomUUID(),
        namespace = namespace,
        pluginId = "my-plugin",
        name = "My Plugin",
    )
    private val releaseId = UUID.randomUUID()
    private val draftRelease = PluginReleaseEntity(
        id = releaseId,
        plugin = plugin,
        version = "1.0.0",
        artifactSha256 = "abc123",
        artifactKey = "acme/my-plugin/1.0.0",
        status = ReleaseStatus.DRAFT,
    )

    @Test
    fun `GET pending reviews returns 200 with list`() {
        whenever(releaseService.findPendingByNamespace("acme")).thenReturn(listOf(draftRelease))

        mockMvc.get("/api/v1/namespaces/acme/reviews/pending")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].pluginId") { value("my-plugin") }
                jsonPath("$[0].version") { value("1.0.0") }
            }
    }

    @Test
    fun `GET pending reviews returns empty list when none`() {
        whenever(releaseService.findPendingByNamespace("acme")).thenReturn(emptyList())

        mockMvc.get("/api/v1/namespaces/acme/reviews/pending")
            .andExpect {
                status { isOk() }
                jsonPath("$") { isArray() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `POST approve returns 200 with published release`() {
        val published = draftRelease.apply { status = ReleaseStatus.PUBLISHED }
        whenever(
            releaseService.updateStatusByIdInNamespace(
                eq(releaseId),
                eq("acme"),
                eq(ReleaseStatus.PUBLISHED),
                eq(true),
            ),
        ).thenReturn(published)
        whenever(releaseMapper.toDto(any(), eq("my-plugin")))
            .thenReturn(buildReleaseDto(io.plugwerk.api.model.PluginReleaseDto.Status.PUBLISHED))

        mockMvc.post("/api/v1/namespaces/acme/reviews/$releaseId/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("published") }
        }
    }

    @Test
    fun `POST approve returns 404 when release not found`() {
        val unknownId = UUID.randomUUID()
        whenever(releaseService.updateStatusByIdInNamespace(eq(unknownId), eq("acme"), any(), eq(true)))
            .thenThrow(ReleaseNotFoundException("id=$unknownId", ""))

        mockMvc.post("/api/v1/namespaces/acme/reviews/$unknownId/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST approve returns 404 when release belongs to different namespace`() {
        whenever(
            releaseService.updateStatusByIdInNamespace(
                eq(releaseId),
                eq("acme"),
                eq(ReleaseStatus.PUBLISHED),
                eq(true),
            ),
        ).thenThrow(ReleaseNotFoundException("id=$releaseId", ""))

        mockMvc.post("/api/v1/namespaces/acme/reviews/$releaseId/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST approve bypasses namespace check for superadmin`() {
        val published = draftRelease.apply { status = ReleaseStatus.PUBLISHED }
        whenever(namespaceAuthorizationService.isSuperadmin(any())).thenReturn(true)
        whenever(
            releaseService.updateStatusByIdInNamespace(
                eq(releaseId),
                eq("acme"),
                eq(ReleaseStatus.PUBLISHED),
                eq(false),
            ),
        ).thenReturn(published)
        whenever(releaseMapper.toDto(any(), eq("my-plugin")))
            .thenReturn(buildReleaseDto(io.plugwerk.api.model.PluginReleaseDto.Status.PUBLISHED))

        mockMvc.post("/api/v1/namespaces/acme/reviews/$releaseId/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `POST reject returns 200 with yanked release`() {
        val yanked = draftRelease.apply { status = ReleaseStatus.YANKED }
        whenever(
            releaseService.updateStatusByIdInNamespace(
                eq(releaseId),
                eq("acme"),
                eq(ReleaseStatus.YANKED),
                eq(true),
            ),
        ).thenReturn(yanked)
        whenever(releaseMapper.toDto(any(), eq("my-plugin")))
            .thenReturn(buildReleaseDto(io.plugwerk.api.model.PluginReleaseDto.Status.YANKED))

        mockMvc.post("/api/v1/namespaces/acme/reviews/$releaseId/reject") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("yanked") }
        }
    }

    @Test
    fun `POST reject returns 404 when release belongs to different namespace`() {
        whenever(
            releaseService.updateStatusByIdInNamespace(
                eq(releaseId),
                eq("acme"),
                eq(ReleaseStatus.YANKED),
                eq(true),
            ),
        ).thenThrow(ReleaseNotFoundException("id=$releaseId", ""))

        mockMvc.post("/api/v1/namespaces/acme/reviews/$releaseId/reject") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isNotFound() }
        }
    }

    private fun buildReleaseDto(status: io.plugwerk.api.model.PluginReleaseDto.Status) =
        io.plugwerk.api.model.PluginReleaseDto(
            id = releaseId,
            pluginId = "my-plugin",
            version = "1.0.0",
            status = status,
        )
}
