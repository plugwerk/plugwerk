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
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.UserRepository
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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
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

/**
 * Unit tests for [OidcLoginSuccessHandler]. Asserts the post-OIDC-callback
 * contract:
 *
 *   - The OIDC `sub` claim is mapped to a Plugwerk `user_subject` of the form
 *     `<registrationId>:<sub>`. Stable identifier; survives admin-UI provider
 *     renames thanks to the UUID-based registrationId from
 *     [OidcRegistrationIds] (#79).
 *   - The same httpOnly refresh cookie a local login would set is sent on the
 *     response — never an access token in the URL or response body.
 *   - The browser is redirected to `/` (the SPA root) so the frontend's
 *     `hydrate()` flow can immediately exchange the cookie for a Plugwerk
 *     access token via `/api/v1/auth/refresh`.
 *   - A non-OAuth2 [Authentication] is treated as a wiring bug and surfaces
 *     loudly rather than silently no-opping.
 */
@ExtendWith(MockitoExtension::class)
class OidcLoginSuccessHandlerTest {

    @Mock lateinit var refreshTokenService: RefreshTokenService

    @Mock lateinit var userRepository: UserRepository

    private val cookieFactory = RefreshTokenCookieFactory(
        PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "test-jwt-secret-at-least-32-characters-long",
                encryptionKey = "test-encryption-key-32-characters-",
                cookieSecure = false,
            ),
        ),
    )

    @Test
    fun `maps OIDC sub to Plugwerk user_subject of the form registrationId-colon-sub (#79)`() {
        val registrationId = "11111111-2222-3333-4444-555555555555"
        val sub = "0123abcd-4567-89ef-0123-456789abcdef"
        val token = oauth2AuthenticationTokenFor(registrationId = registrationId, sub = sub)
        whenever(refreshTokenService.issue(eq("$registrationId:$sub"), anyOrNull())).thenReturn(stubIssuedToken())

        val handler = OidcLoginSuccessHandler(refreshTokenService, cookieFactory, userRepository)
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(MockHttpServletRequest(), response, token)

        verify(refreshTokenService).issue(eq("$registrationId:$sub"), anyOrNull())
    }

    @Test
    fun `sets httpOnly refresh cookie on the response and redirects to SPA root`() {
        val token = oauth2AuthenticationTokenFor(registrationId = "kc", sub = "alice-sub")
        whenever(refreshTokenService.issue(any(), anyOrNull())).thenReturn(stubIssuedToken())

        val handler = OidcLoginSuccessHandler(refreshTokenService, cookieFactory, userRepository)
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(MockHttpServletRequest(), response, token)

        val setCookie = response.getHeader(HttpHeaders.SET_COOKIE)
        assertThat(setCookie).isNotNull()
        assertThat(setCookie!!).contains("HttpOnly")
        // We deliberately never put the access token in the URL or response body —
        // the frontend picks it up via /api/v1/auth/refresh against the cookie.
        assertThat(response.redirectedUrl).isEqualTo("/")
    }

    @Test
    fun `auto-provisions a local user row on first OIDC login (#79)`() {
        val token = oauth2AuthenticationTokenFor(registrationId = "kc", sub = "alice-sub")
        whenever(userRepository.existsByUsername("kc:alice-sub")).thenReturn(false)
        whenever(refreshTokenService.issue(any(), anyOrNull())).thenReturn(stubIssuedToken())

        val handler = OidcLoginSuccessHandler(refreshTokenService, cookieFactory, userRepository)
        handler.onAuthenticationSuccess(MockHttpServletRequest(), MockHttpServletResponse(), token)

        verify(userRepository).save(
            argThat<UserEntity> {
                username == "kc:alice-sub" &&
                    email == null && // see Kotlindoc on ensureLocalUserRow
                    !passwordChangeRequired && // user has no password to change
                    enabled &&
                    !isSuperadmin &&
                    passwordHash == "OIDC:no-password" // not a valid BCrypt — local /auth/login can never succeed
            },
        )
    }

    @Test
    fun `does not re-provision when local user row already exists (idempotent)`() {
        val token = oauth2AuthenticationTokenFor(registrationId = "kc", sub = "alice-sub")
        whenever(userRepository.existsByUsername("kc:alice-sub")).thenReturn(true)
        whenever(refreshTokenService.issue(any(), anyOrNull())).thenReturn(stubIssuedToken())

        val handler = OidcLoginSuccessHandler(refreshTokenService, cookieFactory, userRepository)
        handler.onAuthenticationSuccess(MockHttpServletRequest(), MockHttpServletResponse(), token)

        verify(userRepository, never()).save(any<UserEntity>())
    }

    @Test
    fun `captures upstream id_token and hands it to RefreshTokenService for OIDC users (#352)`() {
        val tokenValue = "eyJhbGciOiJSUzI1NiJ9.fake-id-token.signature"
        val token = oidcAuthenticationTokenFor(
            registrationId = "kc",
            sub = "alice-sub",
            idTokenValue = tokenValue,
        )
        whenever(userRepository.existsByUsername("kc:alice-sub")).thenReturn(true)
        whenever(refreshTokenService.issue(any(), anyOrNull())).thenReturn(stubIssuedToken())

        val handler = OidcLoginSuccessHandler(refreshTokenService, cookieFactory, userRepository)
        handler.onAuthenticationSuccess(MockHttpServletRequest(), MockHttpServletResponse(), token)

        verify(refreshTokenService).issue(eq("kc:alice-sub"), eq(tokenValue))
    }

    @Test
    fun `passes null id_token for non-OIDC OAuth2 principals (graceful degradation, #352)`() {
        // OAuth2 (non-OIDC) flows have no ID token to capture; the handler must still
        // succeed and just record null on the refresh-token row.
        val token = oauth2AuthenticationTokenFor(registrationId = "kc", sub = "alice-sub")
        whenever(userRepository.existsByUsername("kc:alice-sub")).thenReturn(true)
        whenever(refreshTokenService.issue(any(), anyOrNull())).thenReturn(stubIssuedToken())

        val handler = OidcLoginSuccessHandler(refreshTokenService, cookieFactory, userRepository)
        handler.onAuthenticationSuccess(MockHttpServletRequest(), MockHttpServletResponse(), token)

        verify(refreshTokenService).issue(eq("kc:alice-sub"), eq(null))
    }

    @Test
    fun `non-OAuth2 authentication is treated as a wiring bug and fails loudly`() {
        val handler = OidcLoginSuccessHandler(refreshTokenService, cookieFactory, userRepository)
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
        familyId = java.util.UUID.randomUUID(),
        rowId = java.util.UUID.randomUUID(),
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
        // Mirror what Spring Security's OIDC client filter constructs after the code
        // exchange: an OidcUser whose backing OidcIdToken carries the raw token value
        // we want to pin on the refresh-token row for later RP-Initiated Logout.
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
