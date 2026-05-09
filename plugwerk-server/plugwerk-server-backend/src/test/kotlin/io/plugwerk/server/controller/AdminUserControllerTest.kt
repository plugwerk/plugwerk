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
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PasswordResetRateLimitFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.security.RegisterRateLimitFilter
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ChangePasswordRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RegisterRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordResetRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RefreshRateLimitFilter::class]),
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

    @MockitoBean
    private lateinit var namespaceMemberRepository: io.plugwerk.server.repository.NamespaceMemberRepository

    @MockitoBean
    private lateinit var oidcIdentityRepository: io.plugwerk.server.repository.OidcIdentityRepository

    @MockitoBean
    private lateinit var adminPasswordResetService: io.plugwerk.server.service.auth.AdminPasswordResetService

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
        displayName = username,
        email = "$username@example.com",
        source = io.plugwerk.server.domain.UserSource.INTERNAL,
        passwordHash = "\$2a\$12\$hash",
        enabled = enabled,
        passwordChangeRequired = false,
    )

    private fun stubExternalUser(displayName: String = "Alice Schmidt"): UserEntity = UserEntity(
        id = UUID.randomUUID(),
        username = null,
        displayName = displayName,
        email = "${displayName.lowercase().replace(" ", ".")}@example.com",
        source = io.plugwerk.server.domain.UserSource.EXTERNAL,
        passwordHash = null,
        enabled = true,
        passwordChangeRequired = false,
    )

    /**
     * Spring Data interface-projection stub. The real
     * `OidcIdentityRepository.findProviderNamesForUsers` returns a list of
     * `UserProviderProjection` — the controller calls `it.userId` /
     * `it.providerName` on each row. We materialise the same shape with an
     * anonymous object so the wiring is exercised end-to-end.
     */
    private fun providerProjection(userId: UUID, providerName: String) =
        object : io.plugwerk.server.repository.UserProviderProjection {
            override val userId: UUID = userId
            override val providerName: String = providerName
        }

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
    fun `GET admin users with enabled=true returns only enabled users`() {
        val enabledUser = stubUser("alice", enabled = true)
        whenever(userService.findAllByEnabled(true)).thenReturn(listOf(enabledUser))

        mockMvc.get("/api/v1/admin/users?enabled=true").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].username") { value("alice") }
            jsonPath("$[0].enabled") { value(true) }
        }
    }

    @Test
    fun `GET admin users with enabled=false returns only disabled users`() {
        val disabledUser = stubUser("bob", enabled = false)
        whenever(userService.findAllByEnabled(false)).thenReturn(listOf(disabledUser))

        mockMvc.get("/api/v1/admin/users?enabled=false").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].username") { value("bob") }
            jsonPath("$[0].enabled") { value(false) }
        }
    }

    @Test
    fun `GET admin users without enabled param returns all users`() {
        val users = listOf(stubUser("alice", enabled = true), stubUser("bob", enabled = false))
        whenever(userService.findAll()).thenReturn(users)

        mockMvc.get("/api/v1/admin/users").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
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
    fun `GET admin users populates providerName for EXTERNAL users (issue #412)`() {
        // EXTERNAL user gets a provider-name suffix in the admin list so the
        // add-member dropdown can disambiguate two same-named accounts coming
        // from different providers. The lookup is one batched call; this test
        // pins the wiring end-to-end (controller → repository → DTO).
        val external = stubExternalUser("Alice Schmidt")
        whenever(userService.findAll()).thenReturn(listOf(external))
        whenever(oidcIdentityRepository.findProviderNamesForUsers(listOf(external.id!!)))
            .thenReturn(listOf(providerProjection(external.id!!, "Company Keycloak")))

        mockMvc.get("/api/v1/admin/users").andExpect {
            status { isOk() }
            jsonPath("$[0].source") { value("EXTERNAL") }
            jsonPath("$[0].providerName") { value("Company Keycloak") }
        }
    }

    @Test
    fun `GET admin users returns null providerName for INTERNAL users (issue #412)`() {
        // INTERNAL users always return providerName = null. The controller
        // must NOT include their ids in the batched lookup.
        val internal = stubUser("alice")
        whenever(userService.findAll()).thenReturn(listOf(internal))

        mockMvc.get("/api/v1/admin/users").andExpect {
            status { isOk() }
            jsonPath("$[0].source") { value("INTERNAL") }
            jsonPath("$[0].providerName") { doesNotExist() }
        }
        verify(oidcIdentityRepository, never()).findProviderNamesForUsers(any())
    }

    @Test
    fun `GET admin users skips provider lookup when no EXTERNAL users exist (issue #412)`() {
        // Performance regression guard — passing an empty `IN (…)` collection
        // is a JDBC dialect minefield, and the round-trip is wasted anyway.
        // The controller short-circuits; this test locks that behaviour.
        whenever(userService.findAll()).thenReturn(
            listOf(stubUser("alice"), stubUser("bob")),
        )

        mockMvc.get("/api/v1/admin/users").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
        verify(oidcIdentityRepository, never()).findProviderNamesForUsers(any())
    }

    @Test
    fun `GET admin users handles mixed INTERNAL plus EXTERNAL users with one batched lookup`() {
        // The mixed-source case is the realistic production shape: a few
        // INTERNAL admins and many EXTERNAL OIDC users on the same page. The
        // EXTERNAL ids are batched into one call; INTERNAL ids stay out of it.
        val internal = stubUser("admin")
        val externalA = stubExternalUser("Alice Schmidt")
        val externalB = stubExternalUser("Bob Jones")
        whenever(userService.findAll()).thenReturn(listOf(internal, externalA, externalB))
        whenever(
            oidcIdentityRepository.findProviderNamesForUsers(
                listOf(externalA.id!!, externalB.id!!),
            ),
        ).thenReturn(
            listOf(
                providerProjection(externalA.id!!, "Google"),
                providerProjection(externalB.id!!, "GitHub"),
            ),
        )

        mockMvc.get("/api/v1/admin/users").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].providerName") { doesNotExist() }
            jsonPath("$[1].providerName") { value("Google") }
            jsonPath("$[2].providerName") { value("GitHub") }
        }
    }

    @Test
    fun `POST admin users creates user and returns 201`() {
        val user = stubUser("bob")
        whenever(userService.create(any(), any(), any(), anyOrNull())).thenReturn(user)

        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"bob","email":"bob@example.com","password":"secret123long"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.username") { value("bob") }
        }
    }

    @Test
    fun `POST admin users returns 409 when username already exists`() {
        doThrow(ConflictException("username taken")).whenever(userService).create(any(), any(), any(), anyOrNull())

        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","email":"alice@example.com","password":"password12345"}"""
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
            content = """{"username":"eve","email":"eve@example.com","password":"password12345"}"""
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

    // ------------------------------------------------------------------------
    // POST /admin/users/{userId}/reset-password — admin-initiated reset (#450)
    // ------------------------------------------------------------------------

    private val callerSuperadminId: UUID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111")

    /** Re-stamp the security context with a UUID principal so the controller's
     *  `UUID.fromString(auth.name)` cast succeeds. Tests calling this method
     *  use [callerSuperadminId] for the actor. */
    private fun authenticateAsCallerSuperadmin() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(callerSuperadminId.toString(), null, emptyList())
    }

    @Test
    fun `POST admin users reset-password returns 200 with emailSent=true on success`() {
        authenticateAsCallerSuperadmin()
        val targetUserId = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222")
        val expiresAt = java.time.OffsetDateTime.parse("2026-05-09T13:00:00Z")
        whenever(adminPasswordResetService.trigger(targetUserId, callerSuperadminId)).thenReturn(
            io.plugwerk.server.service.auth.AdminPasswordResetService.Result(
                tokenIssued = true,
                emailSent = true,
                expiresAt = expiresAt,
                resetUrl = null,
            ),
        )

        mockMvc.post("/api/v1/admin/users/$targetUserId/reset-password").andExpect {
            status { isOk() }
            jsonPath("$.tokenIssued") { value(true) }
            jsonPath("$.emailSent") { value(true) }
            jsonPath("$.expiresAt") { value("2026-05-09T13:00:00Z") }
            jsonPath("$.resetUrl") { doesNotExist() }
        }
    }

    @Test
    fun `POST admin users reset-password returns 200 with resetUrl when SMTP disabled`() {
        authenticateAsCallerSuperadmin()
        val targetUserId = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222")
        val expiresAt = java.time.OffsetDateTime.parse("2026-05-09T13:00:00Z")
        whenever(adminPasswordResetService.trigger(targetUserId, callerSuperadminId)).thenReturn(
            io.plugwerk.server.service.auth.AdminPasswordResetService.Result(
                tokenIssued = true,
                emailSent = false,
                expiresAt = expiresAt,
                resetUrl = "https://plugwerk.example.com/reset-password?token=raw",
            ),
        )

        mockMvc.post("/api/v1/admin/users/$targetUserId/reset-password").andExpect {
            status { isOk() }
            jsonPath("$.tokenIssued") { value(true) }
            jsonPath("$.emailSent") { value(false) }
            jsonPath("$.resetUrl") { value("https://plugwerk.example.com/reset-password?token=raw") }
        }
    }

    @Test
    fun `POST admin users reset-password returns 400 on EXTERNAL user`() {
        authenticateAsCallerSuperadmin()
        val targetUserId = UUID.randomUUID()
        whenever(adminPasswordResetService.trigger(targetUserId, callerSuperadminId)).thenThrow(
            io.plugwerk.server.service.auth.ExternalUserResetNotAllowedException(
                "Cannot reset password on OIDC users — credentials live with the upstream provider.",
            ),
        )

        mockMvc.post("/api/v1/admin/users/$targetUserId/reset-password").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("OIDC")) }
        }
    }

    @Test
    fun `POST admin users reset-password returns 400 on self-reset`() {
        authenticateAsCallerSuperadmin()
        // Same UUID for caller and target — controller's UUID parse + service self-reset check
        // produce the same 400.
        whenever(adminPasswordResetService.trigger(callerSuperadminId, callerSuperadminId)).thenThrow(
            io.plugwerk.server.service.auth.SelfResetNotAllowedException(
                "Use Profile → Change password to update your own password.",
            ),
        )

        mockMvc.post("/api/v1/admin/users/$callerSuperadminId/reset-password").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("Profile")) }
        }
    }

    @Test
    fun `POST admin users reset-password returns 404 when user not found`() {
        authenticateAsCallerSuperadmin()
        val targetUserId = UUID.randomUUID()
        whenever(adminPasswordResetService.trigger(targetUserId, callerSuperadminId)).thenThrow(
            io.plugwerk.server.service.EntityNotFoundException("User", targetUserId.toString()),
        )

        mockMvc.post("/api/v1/admin/users/$targetUserId/reset-password").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST admin users reset-password returns 403 for non-superadmin`() {
        authenticateAsCallerSuperadmin()
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.post("/api/v1/admin/users/${UUID.randomUUID()}/reset-password").andExpect {
            status { isForbidden() }
        }
    }
}
