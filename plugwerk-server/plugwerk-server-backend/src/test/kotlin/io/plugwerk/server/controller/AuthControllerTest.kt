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

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.OidcEndSessionUrlResolver
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.security.RefreshTokenCookieFactory
import io.plugwerk.server.security.UserCredentialValidator
import io.plugwerk.server.service.JwtTokenService
import io.plugwerk.server.service.RefreshTokenService
import io.plugwerk.server.service.TokenRevocationService
import io.plugwerk.server.service.UnauthorizedException
import io.plugwerk.server.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
import org.springframework.http.ResponseCookie
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@WebMvcTest(
    AuthController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ChangePasswordRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RefreshRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class AuthControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var credentialValidator: UserCredentialValidator

    @MockitoBean lateinit var jwtTokenService: JwtTokenService

    @MockitoBean lateinit var userRepository: UserRepository

    @MockitoBean lateinit var tokenRevocationService: TokenRevocationService

    @MockitoBean lateinit var refreshTokenService: RefreshTokenService

    @MockitoBean lateinit var refreshTokenCookieFactory: RefreshTokenCookieFactory

    @MockitoBean lateinit var userService: UserService

    @MockitoBean lateinit var oidcEndSessionUrlResolver: OidcEndSessionUrlResolver

    @MockitoBean lateinit var oidcIdentityRepository: io.plugwerk.server.repository.OidcIdentityRepository

    @MockitoBean lateinit var currentUserResolver: io.plugwerk.server.security.CurrentUserResolver

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun stubRefreshPath() {
        // Login emits a refresh cookie after credential validation; stub once here so
        // every valid-credential test does not have to repeat the setup. The two-arg
        // signature (#352) accepts a nullable upstreamIdToken; local-login callers
        // pass null.
        whenever(refreshTokenService.issue(any<UUID>(), org.mockito.kotlin.anyOrNull())).thenAnswer {
            RefreshTokenService.IssuedToken(
                plaintext = "stub-refresh-plaintext",
                expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(168),
                maxAge = Duration.ofHours(168),
                familyId = UUID.randomUUID(),
                rowId = UUID.randomUUID(),
            )
        }
        // Stub the 3-arg signature introduced in #317. Note the third arg
        // (persistent) is a Boolean; the answer below faithfully toggles
        // Max-Age so the persistent=false branch tests can assert on the
        // returned header without re-stubbing.
        whenever(refreshTokenCookieFactory.build(any(), any(), any())).thenAnswer { invocation ->
            val persistent = invocation.arguments[2] as Boolean
            val builder = ResponseCookie.from("plugwerk_refresh", "stub-refresh-plaintext")
                .httpOnly(true).secure(false).sameSite("Strict").path("/api/v1/auth")
            if (persistent) builder.maxAge(Duration.ofHours(168))
            builder.build()
        }
        whenever(refreshTokenCookieFactory.clear()).thenAnswer {
            ResponseCookie.from("plugwerk_refresh", "")
                .httpOnly(true).secure(false).sameSite("Strict").path("/api/v1/auth")
                .maxAge(0).build()
        }
    }

    @Test
    fun `POST login returns 200 and token for valid credentials`() {
        val userId = UUID.randomUUID()
        val user = UserEntity(
            id = userId,
            username = "alice",
            displayName = "Alice",
            email = "alice@example.test",
            source = io.plugwerk.server.domain.UserSource.LOCAL,
            passwordHash = "\$2a\$12\$hash",
        )
        whenever(credentialValidator.validate("alice", "secret")).thenReturn(true)
        whenever(jwtTokenService.generateToken(userId.toString())).thenReturn("tok.abc.xyz")
        whenever(jwtTokenService.tokenValiditySeconds()).thenReturn(28800L)
        whenever(userRepository.findByUsernameAndSource("alice", io.plugwerk.server.domain.UserSource.LOCAL))
            .thenReturn(Optional.of(user))

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","password":"secret"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("tok.abc.xyz") }
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.expiresIn") { value(28800) }
            jsonPath("$.userId") { value(userId.toString()) }
            jsonPath("$.displayName") { value("Alice") }
        }
    }

    @Test
    fun `POST login with rememberMe=true forwards persistent=true to the cookie factory`() {
        stubValidLogin("alice", "secret")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","password":"secret","rememberMe":true}"""
        }.andExpect {
            status { isOk() }
            // Stub builds Max-Age=604800 (= 168h) when persistent=true.
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=604800")) }
        }

        verify(refreshTokenCookieFactory).build(any(), any(), eq(true))
    }

    @Test
    fun `POST login with rememberMe=false forwards persistent=false and the cookie has no Max-Age`() {
        stubValidLogin("alice", "secret")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","password":"secret","rememberMe":false}"""
        }.andExpect {
            status { isOk() }
            header {
                string(
                    "Set-Cookie",
                    org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Max-Age")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Expires")),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict"),
                        org.hamcrest.Matchers.containsString("Path=/api/v1/auth"),
                    ),
                )
            }
        }

        verify(refreshTokenCookieFactory).build(any(), any(), eq(false))
    }

    @Test
    fun `POST login without rememberMe field defaults to persistent=true (back-compat)`() {
        stubValidLogin("alice", "secret")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            // No rememberMe key in the body — pre-#317 client shape.
            content = """{"username":"alice","password":"secret"}"""
        }.andExpect {
            status { isOk() }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=604800")) }
        }

        verify(refreshTokenCookieFactory).build(any(), any(), eq(true))
    }

    /**
     * Shared stub for the three `rememberMe`-variant login tests so each test
     * stays focused on the cookie behaviour rather than re-stating the user
     * lookup / token-generation plumbing.
     */
    private fun stubValidLogin(username: String, password: String) {
        val userId = UUID.randomUUID()
        val user = UserEntity(
            id = userId,
            username = username,
            displayName = "Alice",
            email = "$username@example.test",
            source = io.plugwerk.server.domain.UserSource.LOCAL,
            passwordHash = "\$2a\$12\$hash",
        )
        whenever(credentialValidator.validate(username, password)).thenReturn(true)
        whenever(jwtTokenService.generateToken(userId.toString())).thenReturn("tok.abc.xyz")
        whenever(jwtTokenService.tokenValiditySeconds()).thenReturn(28800L)
        whenever(userRepository.findByUsernameAndSource(username, io.plugwerk.server.domain.UserSource.LOCAL))
            .thenReturn(Optional.of(user))
    }

    @Test
    fun `POST login sets passwordChangeRequired true when user requires it`() {
        val userId = UUID.randomUUID()
        val user = UserEntity(
            id = userId,
            username = "alice",
            displayName = "Alice",
            email = "alice@example.test",
            source = io.plugwerk.server.domain.UserSource.LOCAL,
            passwordHash = "\$2a\$12\$hash",
            passwordChangeRequired = true,
        )
        whenever(credentialValidator.validate("alice", "secret")).thenReturn(true)
        whenever(jwtTokenService.generateToken(userId.toString())).thenReturn("tok.abc.xyz")
        whenever(jwtTokenService.tokenValiditySeconds()).thenReturn(28800L)
        whenever(userRepository.findByUsernameAndSource("alice", io.plugwerk.server.domain.UserSource.LOCAL))
            .thenReturn(Optional.of(user))

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","password":"secret"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.passwordChangeRequired") { value(true) }
        }
    }

    @Test
    fun `POST login returns 401 for invalid credentials`() {
        whenever(credentialValidator.validate(any(), any())).thenReturn(false)

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"wrong","password":"wrong"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `POST login returns 400 when username is blank`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"","password":"secret"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST login returns 400 when password is blank`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","password":""}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST login returns 400 when body is missing`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `token is not generated when credentials are invalid`() {
        whenever(credentialValidator.validate(any(), any())).thenReturn(false)

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"hacker","password":"wrong"}"""
        }.andExpect {
            status { isUnauthorized() }
        }

        org.mockito.kotlin.verifyNoInteractions(jwtTokenService)
    }
}
