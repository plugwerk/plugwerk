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
 * Actuator endpoint authorization — default (scrape account disabled) mode (SBS-004 / #292).
 *
 * When no `plugwerk.auth.actuator.scrape-*` is configured the scrape basic-auth chain is
 * not installed; the only access path to `/actuator/{info,prometheus}` is a superadmin JWT.
 * This is the posture a fresh deployment inherits after upgrading past #292 — a strict
 * fallback that cannot accidentally leak metrics to namespace members.
 *
 * See [ADR-0025](../../../../../../../docs/adrs/0025-actuator-endpoint-hardening.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:actuator-sec-default;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class ActuatorSecurityWithoutScrapeAccountIT {

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
            username = "actuator-default-root",
            displayName = "Actuator Default Root",
            email = "root@actuator-default.local",
            source = io.plugwerk.server.domain.UserSource.INTERNAL,
            passwordHash = passwordEncoder.encode("ignored-for-jwt-test")!!,
            enabled = true,
            passwordChangeRequired = false,
            isSuperadmin = true,
        )
        val nonAdmin = UserEntity(
            username = "actuator-default-alice",
            displayName = "Actuator Default Alice",
            email = "alice@actuator-default.local",
            source = io.plugwerk.server.domain.UserSource.INTERNAL,
            passwordHash = passwordEncoder.encode("ignored-for-jwt-test")!!,
            enabled = true,
            passwordChangeRequired = false,
            isSuperadmin = false,
        )
        val saved = userRepository.saveAll(listOf(superadmin, nonAdmin))
        superadminToken = jwtTokenService.generateToken(saved[0].id!!.toString())
        nonAdminToken = jwtTokenService.generateToken(saved[1].id!!.toString())
    }

    private fun basicAuth(user: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())

    @Test
    fun `anonymous request to prometheus returns 401`() {
        mockMvc.get("/actuator/prometheus")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `non-admin JWT gets 403 on prometheus`() {
        mockMvc.get("/actuator/prometheus") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $nonAdminToken")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `non-admin JWT gets 403 on info`() {
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
    fun `basic auth attempt returns 401 when scrape account disabled`() {
        mockMvc.get("/actuator/prometheus") {
            header(HttpHeaders.AUTHORIZATION, basicAuth("prometheus", "anything"))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `health remains public by default`() {
        mockMvc.get("/actuator/health")
            .andExpect { status { isOk() } }
    }
}
