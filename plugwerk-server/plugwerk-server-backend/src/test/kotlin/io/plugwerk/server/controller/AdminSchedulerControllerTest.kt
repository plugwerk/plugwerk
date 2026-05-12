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

import io.plugwerk.server.domain.SchedulerJobEntity
import io.plugwerk.server.domain.SchedulerJobOutcome
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PasswordResetRateLimitFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.security.RegisterRateLimitFilter
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.scheduler.SchedulerJobAdminService
import io.plugwerk.server.service.scheduler.SchedulerJobDescriptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

@WebMvcTest(
    AdminSchedulerController::class,
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
class AdminSchedulerControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean private lateinit var adminService: SchedulerJobAdminService

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
    fun `GET jobs returns registry plus runtime state`() {
        whenever(adminService.listJobs()).thenReturn(
            listOf(
                SchedulerJobAdminService.JobView(
                    descriptor = SchedulerJobDescriptor(
                        name = "orphan-storage-reaper",
                        description = "Reap orphans.",
                        cronExpression = "0 15 3 * * *",
                        supportsDryRun = true,
                        runNowExecutor = { /* not called from controller layer */ },
                    ),
                    state = SchedulerJobEntity(
                        name = "orphan-storage-reaper",
                        enabled = true,
                        dryRun = true,
                        runCountTotal = 7L,
                    ),
                ),
            ),
        )

        mockMvc.get("/api/v1/admin/scheduler/jobs")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].name") { value("orphan-storage-reaper") }
                jsonPath("$[0].enabled") { value(true) }
                jsonPath("$[0].dryRun") { value(true) }
                jsonPath("$[0].runCountTotal") { value(7) }
                jsonPath("$[0].supportsDryRun") { value(true) }
            }
    }

    @Test
    fun `PATCH job updates and returns the row`() {
        whenever(adminService.update(any(), anyOrNull(), anyOrNull())).thenReturn(
            SchedulerJobAdminService.JobView(
                descriptor = SchedulerJobDescriptor(
                    name = "refresh-token-cleanup",
                    description = "x",
                    cronExpression = "0 0 * * * *",
                    supportsDryRun = false,
                    runNowExecutor = { },
                ),
                state = SchedulerJobEntity(
                    name = "refresh-token-cleanup",
                    enabled = false,
                ),
            ),
        )

        mockMvc.patch("/api/v1/admin/scheduler/jobs/refresh-token-cleanup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("enabled" to false))
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("refresh-token-cleanup") }
            jsonPath("$.enabled") { value(false) }
        }
        verify(adminService).update("refresh-token-cleanup", false, null)
    }

    @Test
    fun `POST run-now returns recorded outcome`() {
        whenever(adminService.runNow("refresh-token-cleanup")).thenReturn(
            SchedulerJobAdminService.RunOutcome(
                outcome = SchedulerJobOutcome.SUCCESS,
                durationMs = 42L,
                message = null,
            ),
        )

        mockMvc.post("/api/v1/admin/scheduler/jobs/refresh-token-cleanup/run")
            .andExpect {
                status { isOk() }
                jsonPath("$.outcome") { value("SUCCESS") }
                jsonPath("$.durationMs") { value(42) }
            }
    }

    @Test
    fun `GET jobs without superadmin returns 403`() {
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.get("/api/v1/admin/scheduler/jobs")
            .andExpect { status { isForbidden() } }
    }
}
