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

import io.plugwerk.server.SharedPostgresContainer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Verifies that the `.cors { ... }` wiring in [SecurityConfiguration] applies the
 * configuration driven by `plugwerk.server.cors.*` to the full filter chain. Audit
 * row SBS-002 / #263.
 *
 * The important assertion is the **presence or absence** of the
 * `Access-Control-Allow-Origin` response header rather than a hard HTTP status code,
 * because Spring Security's exact rejection status varies. The browser enforces
 * SOP on the client side when the header is missing, which is the semantics we need.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["plugwerk.server.cors.allowed-origins=https://frontend.example.com"])
class CorsFilterChainIntegrationTest {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = SharedPostgresContainer.instance

        @DynamicPropertySource
        @JvmStatic
        fun overrideDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `preflight from configured origin is allowed and echoes CORS headers`() {
        mockMvc.perform(
            options("/api/v1/config")
                .header("Origin", "https://frontend.example.com")
                .header("Access-Control-Request-Method", "GET"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.com"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
            .andExpect(header().exists("Access-Control-Allow-Methods"))
            .andExpect(header().exists("Access-Control-Max-Age"))
    }

    @Test
    fun `preflight from non-allowed origin does not receive Access-Control-Allow-Origin`() {
        mockMvc.perform(
            options("/api/v1/config")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET"),
        )
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
    }
}
