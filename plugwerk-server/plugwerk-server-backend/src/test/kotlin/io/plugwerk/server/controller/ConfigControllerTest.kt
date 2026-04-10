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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.service.VersionProvider
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(
    ConfigController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class ConfigControllerTest {

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @MockitoBean lateinit var plugwerkProperties: PlugwerkProperties

    @MockitoBean lateinit var versionProvider: VersionProvider

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `GET config returns upload limits and version`() {
        whenever(plugwerkProperties.upload).thenReturn(
            PlugwerkProperties.UploadProperties(maxFileSizeMb = 200),
        )
        whenever(versionProvider.getVersion()).thenReturn("1.2.3")

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.version") { value("1.2.3") }
                jsonPath("$.upload.maxFileSizeMb") { value(200) }
            }
    }

    @Test
    fun `GET config returns unknown when version is not available`() {
        whenever(plugwerkProperties.upload).thenReturn(
            PlugwerkProperties.UploadProperties(maxFileSizeMb = 100),
        )
        whenever(versionProvider.getVersion()).thenReturn("unknown")

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.version") { value("unknown") }
            }
    }
}
