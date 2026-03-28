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

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.service.EntityNotFoundException
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.OidcProviderService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
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
    OidcProviderController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class OidcProviderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var oidcProviderService: OidcProviderService

    @MockitoBean
    private lateinit var namespaceAuthorizationService: NamespaceAuthorizationService

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("admin", null, emptyList())
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun stubProvider(name: String = "My Keycloak", enabled: Boolean = false): OidcProviderEntity =
        OidcProviderEntity(
            id = UUID.randomUUID(),
            name = name,
            providerType = OidcProviderType.KEYCLOAK,
            enabled = enabled,
            clientId = "my-client",
            clientSecretEncrypted = "encrypted-secret",
            issuerUri = "https://kc.example.com/realms/myrealm",
            scope = "openid email profile",
        )

    @Test
    fun `GET oidc-providers returns list`() {
        val provider = stubProvider()
        whenever(oidcProviderService.findAll()).thenReturn(listOf(provider))

        mockMvc.get("/api/v1/admin/oidc-providers").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("My Keycloak") }
            jsonPath("$[0].providerType") { value("KEYCLOAK") }
            jsonPath("$[0].enabled") { value(false) }
        }
    }

    @Test
    fun `GET oidc-providers returns empty list when none configured`() {
        whenever(oidcProviderService.findAll()).thenReturn(emptyList())

        mockMvc.get("/api/v1/admin/oidc-providers").andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
        }
    }

    @Test
    fun `POST oidc-providers creates provider and returns 201`() {
        val provider = stubProvider("GitHub SSO")
        whenever(oidcProviderService.create(any(), any(), any(), any(), anyOrNull(), any())).thenReturn(provider)

        mockMvc.post("/api/v1/admin/oidc-providers") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"GitHub SSO","providerType":"GITHUB","clientId":"gh-id","clientSecret":"gh-secret"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("GitHub SSO") }
        }
    }

    @Test
    fun `PATCH oidc-providers enables provider`() {
        val providerId = UUID.randomUUID()
        val provider = stubProvider(enabled = true)
        whenever(oidcProviderService.findById(providerId)).thenReturn(provider)
        whenever(oidcProviderService.setEnabled(eq(providerId), eq(true))).thenReturn(provider)

        mockMvc.patch("/api/v1/admin/oidc-providers/$providerId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.enabled") { value(true) }
        }
    }

    @Test
    fun `PATCH oidc-providers returns 404 when provider not found`() {
        val providerId = UUID.randomUUID()
        whenever(
            oidcProviderService.findById(providerId),
        ).thenThrow(EntityNotFoundException("OidcProvider", providerId.toString()))

        mockMvc.patch("/api/v1/admin/oidc-providers/$providerId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":true}"""
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE oidc-providers removes provider and returns 204`() {
        val providerId = UUID.randomUUID()

        mockMvc.delete("/api/v1/admin/oidc-providers/$providerId").andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `GET oidc-providers returns 403 for non-superadmin`() {
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.get("/api/v1/admin/oidc-providers").andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST oidc-providers returns 403 for non-superadmin`() {
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.post("/api/v1/admin/oidc-providers") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """{"name":"Rogue","providerType":"GENERIC_OIDC","clientId":"x","clientSecret":"y","issuerUri":"https://evil.example.com"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `DELETE oidc-providers returns 403 for non-superadmin`() {
        val providerId = UUID.randomUUID()
        doThrow(ForbiddenException("Superadmin privileges required"))
            .whenever(namespaceAuthorizationService).requireSuperadmin(any())

        mockMvc.delete("/api/v1/admin/oidc-providers/$providerId").andExpect {
            status { isForbidden() }
        }
    }
}
