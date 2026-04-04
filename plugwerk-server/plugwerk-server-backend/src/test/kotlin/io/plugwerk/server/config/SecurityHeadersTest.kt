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
package io.plugwerk.server.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Verifies that HTTP security headers are present on responses.
 *
 * Uses the public `/actuator/health` endpoint so no authentication is needed.
 *
 * Note: `Strict-Transport-Security` (HSTS) is NOT asserted because MockMvc does not use
 * HTTPS, and Spring Security only sends the HSTS header over secure connections.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:security-headers-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class SecurityHeadersTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `public endpoint returns all security headers`() {
        mockMvc.get("/actuator/health")
            .andExpect {
                header { string("X-Content-Type-Options", "nosniff") }
                header { string("X-Frame-Options", "DENY") }
                header { string("Referrer-Policy", "strict-origin-when-cross-origin") }
                header {
                    string(
                        "Content-Security-Policy",
                        SecurityConfiguration.CSP_POLICY,
                    )
                }
                header {
                    string(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()",
                    )
                }
            }
    }

    @Test
    fun `unauthenticated API request returns security headers with 401`() {
        mockMvc.get("/api/v1/namespaces")
            .andExpect {
                status { isUnauthorized() }
                header { string("X-Content-Type-Options", "nosniff") }
                header { string("X-Frame-Options", "DENY") }
                header { exists("Content-Security-Policy") }
            }
    }

    @Test
    fun `actuator health is publicly accessible`() {
        mockMvc.get("/actuator/health")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `actuator info requires authentication`() {
        mockMvc.get("/actuator/info")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
