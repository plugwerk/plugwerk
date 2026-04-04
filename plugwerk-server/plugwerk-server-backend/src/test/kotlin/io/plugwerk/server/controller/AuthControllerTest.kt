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

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.UserCredentialValidator
import io.plugwerk.server.service.JwtTokenService
import io.plugwerk.server.service.UnauthorizedException
import io.plugwerk.server.service.UserService
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
import java.util.Optional

@WebMvcTest(
    AuthController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
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

    @MockitoBean lateinit var userService: UserService

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

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
