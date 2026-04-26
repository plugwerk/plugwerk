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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.service.OidcEmailMissingException
import io.plugwerk.server.service.OidcIdentityService
import io.plugwerk.server.service.RefreshTokenService
import io.plugwerk.server.service.RefreshTokenService.IssuedToken
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [OidcLoginSuccessHandler] (post-#351 refactor).
 */
@ExtendWith(MockitoExtension::class)
class OidcLoginSuccessHandlerTest {

    @Mock lateinit var refreshTokenService: RefreshTokenService

    @Mock lateinit var oidcIdentityService: OidcIdentityService

    @Mock lateinit var oidcProviderRepository: OidcProviderRepository

    private val cookieFactory = RefreshTokenCookieFactory(
        PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "test-jwt-secret-at-least-32-characters-long",
                encryptionKey = "test-encryption-key-32-characters-",
                cookieSecure = false,
            ),
        ),
    )

    private val providerId = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val provider = OidcProviderEntity(
        id = providerId,
        name = "Test Keycloak",
        providerType = OidcProviderType.OIDC,
        enabled = true,
        clientId = "test-client",
        clientSecretEncrypted = "encrypted",
        issuerUri = "https://kc/realms/test",
    )

    private val resolvedUser = UserEntity(
        id = UUID.randomUUID(),
        username = null,
        displayName = "Alice",
        email = "alice@example.test",
        source = UserSource.OIDC,
        passwordHash = null,
    )

    @Test
    fun `delegates identity resolution to OidcIdentityService and issues refresh token bound to the resolved user`() {
        val tokenValue = "eyJhbGciOiJSUzI1NiJ9.fake-id-token.signature"
        val token = oidcAuthenticationTokenFor(
            registrationId = providerId.toString(),
            sub = "alice-sub",
            idTokenValue = tokenValue,
        )
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcIdentityService.upsertOnLogin(eq(provider), eq("alice-sub"), any())).thenReturn(resolvedUser)
        whenever(refreshTokenService.issue(eq(resolvedUser.id!!), anyOrNull())).thenReturn(stubIssuedToken())

        val handler =
            OidcLoginSuccessHandler(refreshTokenService, cookieFactory, oidcIdentityService, oidcProviderRepository)
        handler.onAuthenticationSuccess(MockHttpServletRequest(), MockHttpServletResponse(), token)

        verify(oidcIdentityService).upsertOnLogin(eq(provider), eq("alice-sub"), any())
        verify(refreshTokenService).issue(eq(resolvedUser.id!!), eq(tokenValue))
    }

    @Test
    fun `sets httpOnly refresh cookie and redirects to SPA root`() {
        val token = oidcAuthenticationTokenFor(
            registrationId = providerId.toString(),
            sub = "alice-sub",
            idTokenValue = "eyJ.x.y",
        )
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcIdentityService.upsertOnLogin(any(), any(), any())).thenReturn(resolvedUser)
        whenever(refreshTokenService.issue(any<UUID>(), anyOrNull())).thenReturn(stubIssuedToken())

        val handler =
            OidcLoginSuccessHandler(refreshTokenService, cookieFactory, oidcIdentityService, oidcProviderRepository)
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(MockHttpServletRequest(), response, token)

        val setCookie = response.getHeader(HttpHeaders.SET_COOKIE)
        assertThat(setCookie).isNotNull()
        assertThat(setCookie!!).contains("HttpOnly")
        // Deliberately never include the access token in the URL or response body —
        // the frontend picks it up via /api/v1/auth/refresh against the cookie.
        assertThat(response.redirectedUrl).isEqualTo("/")
    }

    @Test
    fun `rejects login with 400 when OIDC provider returned no email claim`() {
        val token = oidcAuthenticationTokenFor(
            registrationId = providerId.toString(),
            sub = "alice-sub",
            idTokenValue = "eyJ.x.y",
        )
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcIdentityService.upsertOnLogin(any(), any(), any()))
            .thenThrow(OidcEmailMissingException(provider.name))

        val handler =
            OidcLoginSuccessHandler(refreshTokenService, cookieFactory, oidcIdentityService, oidcProviderRepository)
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(MockHttpServletRequest(), response, token)

        assertThat(response.status).isEqualTo(400)
    }

    @Test
    fun `passes null id_token for non-OIDC OAuth2 principals (graceful degradation, #352)`() {
        val token = oauth2AuthenticationTokenFor(registrationId = providerId.toString(), sub = "alice-sub")
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcIdentityService.upsertOnLogin(any(), any(), any())).thenReturn(resolvedUser)
        whenever(refreshTokenService.issue(any<UUID>(), anyOrNull())).thenReturn(stubIssuedToken())

        val handler =
            OidcLoginSuccessHandler(refreshTokenService, cookieFactory, oidcIdentityService, oidcProviderRepository)
        handler.onAuthenticationSuccess(MockHttpServletRequest(), MockHttpServletResponse(), token)

        verify(refreshTokenService).issue(eq(resolvedUser.id!!), eq(null))
    }

    @Test
    fun `non-OAuth2 authentication is treated as a wiring bug and fails loudly`() {
        val handler =
            OidcLoginSuccessHandler(refreshTokenService, cookieFactory, oidcIdentityService, oidcProviderRepository)
        val plainAuth = TestingAuthenticationToken("alice", "irrelevant")

        assertThatThrownBy {
            handler.onAuthenticationSuccess(MockHttpServletRequest(), MockHttpServletResponse(), plainAuth)
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("non-OAuth2 authentication")
    }

    private fun stubIssuedToken() = IssuedToken(
        plaintext = "refresh-plaintext",
        expiresAt = java.time.OffsetDateTime.now().plusDays(7),
        maxAge = Duration.ofDays(7),
        familyId = UUID.randomUUID(),
        rowId = UUID.randomUUID(),
    )

    private fun oauth2AuthenticationTokenFor(registrationId: String, sub: String): OAuth2AuthenticationToken {
        val user = DefaultOAuth2User(
            emptyList(),
            mapOf("sub" to sub, "email" to "test@plugwerk.test"),
            "sub",
        )
        return OAuth2AuthenticationToken(user, emptyList(), registrationId)
    }

    private fun oidcAuthenticationTokenFor(
        registrationId: String,
        sub: String,
        idTokenValue: String,
    ): OAuth2AuthenticationToken {
        val now = Instant.now()
        val idToken = OidcIdToken(
            idTokenValue,
            now,
            now.plusSeconds(300),
            mapOf("sub" to sub, "email" to "test@plugwerk.test"),
        )
        val user = DefaultOidcUser(emptyList(), idToken)
        return OAuth2AuthenticationToken(user, emptyList(), registrationId)
    }
}
