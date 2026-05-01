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

import io.plugwerk.server.domain.OidcProviderType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class GitHubPrincipalAdapterTest {

    @Mock lateinit var authorizedClientService: OAuth2AuthorizedClientService

    @Mock lateinit var emailFetcher: GitHubEmailFetcher

    private fun adapter() = GitHubPrincipalAdapter(authorizedClientService, emailFetcher)

    private fun tokenWith(
        attributes: Map<String, Any>,
        registrationId: String = "gh-registration",
    ): OAuth2AuthenticationToken {
        // GitHub's user-id attribute is named "id" — DefaultOAuth2User needs the
        // attribute KEY (not the value) as the third arg. Use it as the
        // nameAttributeKey throughout these tests, which matches what
        // CommonOAuth2Provider.GITHUB sets in production.
        val user = DefaultOAuth2User(emptyList(), attributes, "id")
        return OAuth2AuthenticationToken(user, emptyList(), registrationId)
    }

    @Test
    fun `claims only the GITHUB provider type`() {
        assertThat(adapter().providerTypes).containsExactly(OidcProviderType.GITHUB)
    }

    @Test
    fun `extracts numeric id as subject (string), uses public email when present, no out-of-band fetch`() {
        val token = tokenWith(
            mapOf(
                "id" to 123_456,
                "login" to "octocat",
                "name" to "Mona Lisa Octocat",
                "email" to "mona@example.com",
            ),
        )

        val resolved = adapter().resolve(token)

        assertThat(resolved.subject).isEqualTo("123456")
        assertThat(resolved.email).isEqualTo("mona@example.com")
        assertThat(resolved.displayName).isEqualTo("Mona Lisa Octocat")
        assertThat(resolved.upstreamIdToken).isNull()
        // Public email present → /user/emails not called.
        verify(emailFetcher, never()).fetchPrimaryVerified(any())
    }

    @Test
    fun `falls back to login when name is blank`() {
        val token = tokenWith(
            mapOf(
                "id" to 1L,
                "login" to "octocat",
                "name" to "",
                "email" to "x@y.z",
            ),
        )

        assertThat(adapter().resolve(token).displayName).isEqualTo("octocat")
    }

    @Test
    fun `fetches primary verified email out of band when public email is missing`() {
        val token = tokenWith(
            mapOf<String, Any>(
                "id" to 999L,
                "login" to "private-user",
                "name" to "Private User",
                // GitHub returns "email": null for users with no public email — we
                // simulate that by leaving the key out (DefaultOAuth2User attributes
                // is Map<String, Any>, no nulls allowed).
            ),
        )
        val authorizedClient = stubAuthorizedClient(accessToken = "gho_FAKE")
        whenever(
            authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(
                eq("gh-registration"),
                eq(token.name),
            ),
        ).thenReturn(authorizedClient)
        whenever(emailFetcher.fetchPrimaryVerified("gho_FAKE")).thenReturn("private@example.com")

        val resolved = adapter().resolve(token)

        assertThat(resolved.email).isEqualTo("private@example.com")
        verify(emailFetcher).fetchPrimaryVerified("gho_FAKE")
    }

    @Test
    fun `returns null email when out-of-band fetch yields nothing (downstream raises OidcEmailMissingException)`() {
        val token = tokenWith(
            mapOf("id" to 1L, "login" to "u", "name" to "U"),
        )
        val authorizedClient = stubAuthorizedClient(accessToken = "gho_FAKE")
        whenever(
            authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(
                eq("gh-registration"),
                eq(token.name),
            ),
        ).thenReturn(authorizedClient)
        whenever(emailFetcher.fetchPrimaryVerified("gho_FAKE")).thenReturn(null)

        assertThat(adapter().resolve(token).email).isNull()
    }

    @Test
    fun `returns null email when no authorized client is loaded (no access token to use)`() {
        val token = tokenWith(
            mapOf("id" to 1L, "login" to "u"),
        )
        whenever(
            authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(any(), any()),
        ).thenReturn(null)

        assertThat(adapter().resolve(token).email).isNull()
        verify(emailFetcher, never()).fetchPrimaryVerified(any())
    }

    // Note: a "no id attribute" branch lives in the adapter as a defensive
    // wiring check, but Spring's DefaultOAuth2User constructor already throws
    // IllegalArgumentException when the supplied nameAttributeKey is absent
    // from the attributes map. The check therefore cannot be reached via the
    // public OAuth2AuthenticationToken path in practice. Kept in code for
    // explicit-failure-mode documentation, dropped from the test suite as
    // unreachable.

    private fun stubAuthorizedClient(accessToken: String): OAuth2AuthorizedClient {
        val now = Instant.now()
        val token = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            accessToken,
            now,
            now.plusSeconds(3600),
        )
        val registration = ClientRegistration.withRegistrationId("gh-registration")
            .clientId("client-id")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example/callback")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .build()
        return OAuth2AuthorizedClient(registration, "principal", token)
    }
}
