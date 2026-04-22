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
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * Pins the CSRF scope contract (ADR-0027, supersedes ADR-0020): CSRF protection is
 * enforced **only** on `POST /api/v1/auth/refresh`; every other Bearer-authenticated
 * POST stays CSRF-exempt because Bearer tokens are not ambient credentials.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:csrf-scope-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class CsrfScopeIT {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var jwtTokenService: JwtTokenService

    private lateinit var bearer: String

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
        val user = userRepository.save(
            UserEntity(
                username = "csrf-scope-user",
                email = "csrf-scope@it.test",
                passwordHash = passwordEncoder.encode("irrelevant")!!,
                enabled = true,
                passwordChangeRequired = false,
                isSuperadmin = false,
            ),
        )
        bearer = jwtTokenService.generateToken(user.username)
    }

    @Test
    fun `login endpoint does not require CSRF`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"unknown","password":"wrong"}"""
        }.andExpect {
            // 401 (bad creds) — not 403 (CSRF). The whole point: CSRF never fires on login.
            status { isUnauthorized() }
        }
    }

    @Test
    fun `refresh endpoint requires CSRF header even without a valid cookie`() {
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            // No CSRF → 403. (Even without a cookie, CSRF is checked first.)
            status { isForbidden() }
        }
    }

    @Test
    fun `logout POST with Bearer does NOT require CSRF`() {
        mockMvc.post("/api/v1/auth/logout") {
            header("Authorization", "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            // 204 means the Bearer passed authz and logout ran — no CSRF block.
            status { isNoContent() }
        }
    }

    @Test
    fun `arbitrary authenticated POST (change-password) does NOT require CSRF`() {
        // change-password is a Bearer-authenticated mutation — it's exactly the kind of
        // POST that would be hit hardest if CSRF were accidentally re-enabled everywhere.
        // We expect a 4xx from the endpoint's own validation (bad current password),
        // NOT 403 from CSRF.
        val response = mockMvc.post("/api/v1/auth/change-password") {
            header("Authorization", "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"wrong","newPassword":"does-not-matter"}"""
        }.andReturn().response

        // Accept any non-403 4xx status; the important invariant is "not 403 from CSRF".
        check(response.status != 403) {
            "change-password returned 403 — CSRF is active on the wrong endpoint. Response: ${response.contentAsString}"
        }
    }
}
