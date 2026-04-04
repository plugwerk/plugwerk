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

import io.plugwerk.api.model.UpdateCheckResponse
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.service.NamespaceNotFoundException
import io.plugwerk.server.service.UpdateCheckService
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(
    UpdateCheckController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class UpdateCheckControllerTest {

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @MockitoBean lateinit var updateCheckService: UpdateCheckService

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `POST updates check returns 200 with empty updates when all up to date`() {
        whenever(updateCheckService.checkUpdates(eq("acme"), any()))
            .thenReturn(UpdateCheckResponse(updates = emptyList()))

        mockMvc.post("/api/v1/namespaces/acme/updates/check") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"plugins":[{"pluginId":"my-plugin","currentVersion":"1.0.0"}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.updates") { isArray() }
            jsonPath("$.updates.length()") { value(0) }
        }
    }

    @Test
    fun `POST updates check returns 404 when namespace not found`() {
        whenever(updateCheckService.checkUpdates(eq("unknown"), any()))
            .thenThrow(NamespaceNotFoundException("unknown"))

        mockMvc.post("/api/v1/namespaces/unknown/updates/check") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"plugins":[]}"""
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST updates check returns 400 when body is missing`() {
        mockMvc.post("/api/v1/namespaces/acme/updates/check") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
