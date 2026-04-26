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

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun stubRefreshPath() {
        // Login emits a refresh cookie after credential validation; stub once here so
        // every valid-credential test does not have to repeat the setup. The two-arg
        // signature (#352) accepts a nullable upstreamIdToken; local-login callers
        // pass null.
        whenever(refreshTokenService.issue(any(), org.mockito.kotlin.anyOrNull())).thenAnswer {
            RefreshTokenService.IssuedToken(
                plaintext = "stub-refresh-plaintext",
                expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(168),
                maxAge = Duration.ofHours(168),
                familyId = UUID.randomUUID(),
                rowId = UUID.randomUUID(),
            )
        }
        whenever(refreshTokenCookieFactory.build(any(), any())).thenAnswer {
            ResponseCookie.from("plugwerk_refresh", "stub-refresh-plaintext")
                .httpOnly(true).secure(false).sameSite("Strict").path("/api/v1/auth")
                .maxAge(Duration.ofHours(168)).build()
        }
        whenever(refreshTokenCookieFactory.clear()).thenAnswer {
            ResponseCookie.from("plugwerk_refresh", "")
                .httpOnly(true).secure(false).sameSite("Strict").path("/api/v1/auth")
                .maxAge(0).build()
        }
    }

    @Test
    fun `POST login returns 200 and token for valid credentials`() {
        whenever(credentialValidator.validate("alice", "secret")).thenReturn(true)
        whenever(jwtTokenService.generateToken("alice")).thenReturn("tok.abc.xyz")
        whenever(jwtTokenService.tokenValiditySeconds()).thenReturn(28800L)
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.empty())

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","password":"secret"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("tok.abc.xyz") }
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.expiresIn") { value(28800) }
        }
    }

    @Test
    fun `POST login sets passwordChangeRequired true when user requires it`() {
        val user = UserEntity(username = "alice", passwordHash = "\$2a\$12\$hash", passwordChangeRequired = true)
        whenever(credentialValidator.validate("alice", "secret")).thenReturn(true)
        whenever(jwtTokenService.generateToken("alice")).thenReturn("tok.abc.xyz")
        whenever(jwtTokenService.tokenValiditySeconds()).thenReturn(28800L)
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.of(user))

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
