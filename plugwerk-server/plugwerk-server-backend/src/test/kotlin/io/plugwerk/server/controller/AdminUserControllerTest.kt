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
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.service.ConflictException
import io.plugwerk.server.service.EntityNotFoundException
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

@WebMvcTest(
    AdminUserController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class AdminUserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var namespaceAuthorizationService: NamespaceAuthorizationService

    @BeforeEach
    fun setUp() {
        // Simulate an authenticated superadmin — requireSuperadmin mock does nothing by default
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("admin", null, emptyList())
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun stubUser(username: String = "alice", enabled: Boolean = true): UserEntity = UserEntity(
        id = UUID.randomUUID(),
        username = username,
        email = "$username@example.com",
        passwordHash = "\$2a\$12\$hash",
        enabled = enabled,
        passwordChangeRequired = false,
    )

    @Test
    fun `GET admin users returns list`() {
        val user = stubUser()
        whenever(userService.findAll()).thenReturn(listOf(user))

        mockMvc.get("/api/v1/admin/users").andExpect {
            status { isOk() }
            jsonPath("$[0].username") { value("alice") }
            jsonPath("$[0].enabled") { value(true) }
        }
    }

    @Test
    fun `GET admin users returns empty list when no users`() {
        whenever(userService.findAll()).thenReturn(emptyList())

        mockMvc.get("/api/v1/admin/users").andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
        }
    }

    @Test
    fun `POST admin users creates user and returns 201`() {
        val user = stubUser("bob")
        whenever(userService.create(any(), anyOrNull(), any())).thenReturn(user)

        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"bob","password":"secret123"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.username") { value("bob") }
        }
    }

    @Test
    fun `POST admin users returns 409 when username already exists`() {
        doThrow(ConflictException("username taken")).whenever(userService).create(any(), anyOrNull(), any())

        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","password":"password123"}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `PATCH admin users enables user`() {
        val userId = UUID.randomUUID()
        val user = stubUser()
        whenever(userService.findById(userId)).thenReturn(user)
        whenever(userService.setEnabled(userId, true)).thenReturn(user)

        mockMvc.patch("/api/v1/admin/users/$userId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.enabled") { value(true) }
        }
    }

    @Test
    fun `PATCH admin users returns 404 when user not found`() {
        val userId = UUID.randomUUID()
        whenever(userService.findById(userId)).thenThrow(EntityNotFoundException("User", userId.toString()))

        mockMvc.patch("/api/v1/admin/users/$userId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":false}"""
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE admin users returns 204 on success`() {
        val userId = UUID.randomUUID()
        doNothing().whenever(userService).delete(userId)

        mockMvc.delete("/api/v1/admin/users/$userId").andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE admin users returns 404 when user not found`() {
        val userId = UUID.randomUUID()
        doThrow(EntityNotFoundException("User", userId.toString())).whenever(userService).delete(userId)

        mockMvc.delete("/api/v1/admin/users/$userId").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE admin users returns 403 when deleting superadmin`() {
        val userId = UUID.randomUUID()
        doThrow(ForbiddenException("The superadmin account cannot be deleted")).whenever(userService).delete(userId)

        mockMvc.delete("/api/v1/admin/users/$userId").andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GET admin users returns 403 for non-superadmin`() {
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.get("/api/v1/admin/users").andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST admin users returns 403 for non-superadmin`() {
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"eve","password":"password123"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `PATCH admin users returns 403 for non-superadmin`() {
        val userId = UUID.randomUUID()
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.patch("/api/v1/admin/users/$userId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":true}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `DELETE admin users returns 403 for non-superadmin`() {
        val userId = UUID.randomUUID()
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.delete("/api/v1/admin/users/$userId").andExpect {
            status { isForbidden() }
        }
    }
}
