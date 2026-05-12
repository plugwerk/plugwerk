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

import tools.jackson.databind.ObjectMapper
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PasswordResetRateLimitFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.security.RegisterRateLimitFilter
import io.plugwerk.server.service.storage.consistency.BulkArtifactDeletionResult
import io.plugwerk.server.service.storage.consistency.ConsistencyReport
import io.plugwerk.server.service.storage.consistency.OrphanedArtifact
import io.plugwerk.server.service.storage.consistency.StorageConsistencyAdminService
import io.plugwerk.server.service.storage.consistency.StorageConsistencyService
import io.plugwerk.server.service.storage.consistency.StorageScanLimitExceededException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.plugwerk.server.service.ForbiddenException
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID

@WebMvcTest(
    AdminStorageConsistencyController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ChangePasswordRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RegisterRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordResetRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RefreshRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class AdminStorageConsistencyControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean private lateinit var consistencyService: StorageConsistencyService

    @MockitoBean private lateinit var adminService: StorageConsistencyAdminService

    @MockitoBean private lateinit var namespaceAuthorizationService: NamespaceAuthorizationService

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("admin", null, emptyList())
        whenever(namespaceAuthorizationService.isCurrentUserSuperadmin()).thenReturn(true)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `GET consistency returns scan result`() {
        val now = Instant.parse("2026-05-12T12:00:00Z")
        whenever(consistencyService.scan()).thenReturn(
            ConsistencyReport(
                missingArtifacts = emptyList(),
                orphanedArtifacts = listOf(
                    OrphanedArtifact(
                        key = "ns:orphan:1.0.0:jar",
                        lastModified = now.minusSeconds(48 * 3600),
                        ageHours = 48,
                        sizeBytes = 12345L,
                    ),
                ),
                scannedAt = now,
                totalDbRows = 0,
                totalStorageObjects = 1,
            ),
        )

        mockMvc.get("/api/v1/admin/storage/consistency")
            .andExpect {
                status { isOk() }
                jsonPath("$.totalStorageObjects") { value(1) }
                jsonPath("$.orphanedArtifacts[0].key") { value("ns:orphan:1.0.0:jar") }
                jsonPath("$.orphanedArtifacts[0].ageHours") { value(48) }
            }
    }

    @Test
    fun `GET consistency over scan limit returns 409`() {
        whenever(consistencyService.scan())
            .thenThrow(StorageScanLimitExceededException(limit = 100, scannedSoFar = 101))

        mockMvc.get("/api/v1/admin/storage/consistency")
            .andExpect {
                status { isConflict() }
                jsonPath("$.limit") { value(100) }
                jsonPath("$.scannedSoFar") { value(101) }
            }
    }

    @Test
    fun `GET consistency without superadmin returns 403`() {
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.get("/api/v1/admin/storage/consistency")
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `DELETE orphaned release returns 204 and calls the admin service`() {
        val id = UUID.randomUUID()

        mockMvc.delete("/api/v1/admin/storage/consistency/releases/$id")
            .andExpect { status { isNoContent() } }

        verify(adminService).deleteOrphanedRelease(eq(id))
    }

    @Test
    fun `DELETE orphaned artifacts bulk returns deleted vs skipped`() {
        whenever(adminService.deleteOrphanedArtifacts(any())).thenReturn(
            BulkArtifactDeletionResult(
                deleted = listOf("ns:gone:1.0.0:jar"),
                skipped = listOf("ns:reborn:1.0.0:jar"),
            ),
        )

        mockMvc.delete("/api/v1/admin/storage/consistency/artifacts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("keys" to listOf("ns:gone:1.0.0:jar", "ns:reborn:1.0.0:jar")),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.deleted[0]") { value("ns:gone:1.0.0:jar") }
            jsonPath("$.skipped[0]") { value("ns:reborn:1.0.0:jar") }
        }
    }
}
