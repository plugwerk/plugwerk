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

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.JwtTokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Base64

/**
 * Actuator endpoint authorization — scrape-account configuration mode (SBS-004 / #292).
 *
 * Pins the contract for `/actuator/{info,prometheus}` when `plugwerk.auth.actuator.scrape-*`
 * is set: a dedicated HTTP Basic scrape user is the canonical access path, superadmin JWT
 * is the fallback for human admins, everything else is denied.
 *
 * See [ADR-0025](../../../../../../../docs/adrs/0025-actuator-endpoint-hardening.md) for the
 * decision record and threat model.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:actuator-sec-scrape;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "plugwerk.auth.actuator.scrape-username=prometheus",
        "plugwerk.auth.actuator.scrape-password=scrape-test-password-32char-sample",
    ],
)
class ActuatorSecurityWithScrapeAccountIT {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var jwtTokenService: JwtTokenService

    private lateinit var superadminToken: String
    private lateinit var nonAdminToken: String

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
        val superadmin = UserEntity(
            username = "actuator-test-root",
            email = "root@actuator-test.local",
            passwordHash = passwordEncoder.encode("ignored-for-jwt-test")!!,
            enabled = true,
            passwordChangeRequired = false,
            isSuperadmin = true,
        )
        val nonAdmin = UserEntity(
            username = "actuator-test-alice",
            email = "alice@actuator-test.local",
            passwordHash = passwordEncoder.encode("ignored-for-jwt-test")!!,
            enabled = true,
            passwordChangeRequired = false,
            isSuperadmin = false,
        )
        userRepository.saveAll(listOf(superadmin, nonAdmin))
        superadminToken = jwtTokenService.generateToken(superadmin.username)
        nonAdminToken = jwtTokenService.generateToken(nonAdmin.username)
    }

    private fun basicAuth(user: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())

    @Test
    fun `anonymous request to prometheus returns 401`() {
        mockMvc.get("/actuator/prometheus")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `anonymous request to info returns 401`() {
        mockMvc.get("/actuator/info")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `authenticated non-admin gets 403 on prometheus`() {
        mockMvc.get("/actuator/prometheus") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $nonAdminToken")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `authenticated non-admin gets 403 on info`() {
        mockMvc.get("/actuator/info") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $nonAdminToken")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `superadmin JWT can read prometheus`() {
        mockMvc.get("/actuator/prometheus") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $superadminToken")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `superadmin JWT can read info`() {
        mockMvc.get("/actuator/info") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $superadminToken")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `scrape account with correct basic auth can read prometheus`() {
        mockMvc.get("/actuator/prometheus") {
            header(HttpHeaders.AUTHORIZATION, basicAuth("prometheus", "scrape-test-password-32char-sample"))
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `scrape account with correct basic auth can read info`() {
        mockMvc.get("/actuator/info") {
            header(HttpHeaders.AUTHORIZATION, basicAuth("prometheus", "scrape-test-password-32char-sample"))
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `scrape account with wrong password returns 401`() {
        mockMvc.get("/actuator/prometheus") {
            header(HttpHeaders.AUTHORIZATION, basicAuth("prometheus", "wrong-password"))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `scrape account with unknown user returns 401`() {
        mockMvc.get("/actuator/prometheus") {
            header(HttpHeaders.AUTHORIZATION, basicAuth("eve", "scrape-test-password-32char-sample"))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `health remains public with scrape account configured`() {
        mockMvc.get("/actuator/health")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `scrape account authority does not leak to api endpoints`() {
        mockMvc.get("/api/v1/namespaces") {
            header(HttpHeaders.AUTHORIZATION, basicAuth("prometheus", "scrape-test-password-32char-sample"))
        }.andExpect { status { isUnauthorized() } }
    }
}
