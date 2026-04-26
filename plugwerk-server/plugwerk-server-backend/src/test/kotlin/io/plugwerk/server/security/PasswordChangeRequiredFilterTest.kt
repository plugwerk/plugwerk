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
package io.plugwerk.server.security

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PasswordChangeRequiredFilterTest {

    @Mock
    lateinit var userRepository: UserRepository

    private lateinit var filter: PasswordChangeRequiredFilter

    @BeforeEach
    fun setUp() {
        filter = PasswordChangeRequiredFilter(userRepository)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun jwtAuthFor(subject: String): JwtAuthenticationToken {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        return JwtAuthenticationToken(jwt)
    }

    private fun userEntity(id: UUID, passwordChangeRequired: Boolean) = UserEntity(
        id = id,
        username = "u-$id",
        displayName = "User $id",
        email = "$id@example.test",
        source = UserSource.LOCAL,
        passwordHash = "hash",
        passwordChangeRequired = passwordChangeRequired,
    )

    @Test
    fun `blocks request when JWT user has passwordChangeRequired true`() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication = jwtAuthFor(userId.toString())
        whenever(userRepository.findById(userId))
            .thenReturn(Optional.of(userEntity(userId, passwordChangeRequired = true)))

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/default/plugins")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(403)
        assertThat(response.contentAsString).contains("PASSWORD_CHANGE_REQUIRED")
        assertThat(chain.request).isNull()
    }

    @Test
    fun `allows change-password endpoint when passwordChangeRequired true`() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication = jwtAuthFor(userId.toString())
        whenever(userRepository.findById(userId))
            .thenReturn(Optional.of(userEntity(userId, passwordChangeRequired = true)))

        val request = MockHttpServletRequest("POST", "/api/v1/auth/change-password")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `allows login endpoint when passwordChangeRequired true`() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication = jwtAuthFor(userId.toString())
        whenever(userRepository.findById(userId))
            .thenReturn(Optional.of(userEntity(userId, passwordChangeRequired = true)))

        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `passes through when JWT user has passwordChangeRequired false`() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication = jwtAuthFor(userId.toString())
        whenever(userRepository.findById(userId))
            .thenReturn(Optional.of(userEntity(userId, passwordChangeRequired = false)))

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/default/plugins")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `skips check when authentication is not JwtAuthenticationToken (API key)`() {
        val apiKeyAuth = UsernamePasswordAuthenticationToken(
            "default",
            null,
            listOf(SimpleGrantedAuthority("ROLE_API_CLIENT")),
        )
        SecurityContextHolder.getContext().authentication = apiKeyAuth

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/default/plugins")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `skips check when no authentication present`() {
        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/default/plugins")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `passes through for unknown user (defensive default = false)`() {
        val ghostId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication = jwtAuthFor(ghostId.toString())
        whenever(userRepository.findById(ghostId)).thenReturn(Optional.empty())

        val request = MockHttpServletRequest("GET", "/api/v1/admin/users")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `passes through when JWT subject is not a parseable UUID (legacy or forged token)`() {
        // Pre-#351 tokens carried a username as `sub` — they should not bleed
        // through to a 403 here; downstream filters will reject them on the
        // signature/exp/revocation paths.
        SecurityContextHolder.getContext().authentication = jwtAuthFor("alice-legacy-username")

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/default/plugins")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }
}
