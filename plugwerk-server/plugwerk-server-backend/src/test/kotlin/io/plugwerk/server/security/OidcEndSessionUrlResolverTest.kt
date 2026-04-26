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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType

/**
 * Unit tests for [OidcEndSessionUrlResolver] (#352, RP-Initiated Logout).
 *
 * Three behaviours are pinned down:
 *
 *   - **Happy path** — provider advertises `end_session_endpoint`, the resolver
 *     returns a URL with `id_token_hint` and `post_logout_redirect_uri` properly
 *     encoded.
 *   - **Missing endpoint** — provider's discovery metadata does not include
 *     `end_session_endpoint` (vanilla OAuth2 surface like the GitHub provider) —
 *     the resolver returns `null` so the caller can fall back to a plain
 *     cookie-clear logout.
 *   - **Missing registration** — wholly unknown registrationId returns `null`;
 *     guards against a stale subject re-using the resolver after the provider
 *     was deleted in admin UI.
 */
@ExtendWith(MockitoExtension::class)
class OidcEndSessionUrlResolverTest {

    @Mock lateinit var clientRegistrationRepository: DbClientRegistrationRepository

    private val props = PlugwerkProperties(
        server = PlugwerkProperties.ServerProperties(baseUrl = "http://localhost:5173"),
        auth = PlugwerkProperties.AuthProperties(
            jwtSecret = "test-jwt-secret-at-least-32-characters-long",
            encryptionKey = "test-encryption-key-32-characters-",
        ),
    )

    @Test
    fun `builds RP-initiated logout URL with id_token_hint and post_logout_redirect_uri`() {
        val registrationId = "11111111-2222-3333-4444-555555555555"
        whenever(clientRegistrationRepository.findByRegistrationId(registrationId))
            .thenReturn(
                clientRegistrationWith(
                    registrationId = registrationId,
                    endSessionEndpoint = "http://localhost:8081/realms/plugwerk/protocol/openid-connect/logout",
                ),
            )

        val resolver = OidcEndSessionUrlResolver(clientRegistrationRepository, props)
        val url = resolver.resolve(registrationId, idTokenHint = "eyJ.fake.id-token")

        assertThat(url).isNotNull()
        assertThat(url!!).startsWith("http://localhost:8081/realms/plugwerk/protocol/openid-connect/logout")
        assertThat(url).contains("id_token_hint=eyJ.fake.id-token")
        // post_logout_redirect_uri is built from props.server.baseUrl + /login.
        // RFC 3986 allows `:` and `/` in query values (`pchar` plus the explicit slash
        // production), so Spring's UriComponentsBuilder leaves them as-is. Either
        // encoded or unencoded form is accepted by Keycloak; we pin the unencoded
        // form because that is what Spring actually produces — pinning the encoded
        // form would silently start failing if Spring ever tightens the encoder.
        assertThat(url).contains("post_logout_redirect_uri=http://localhost:5173/login")
    }

    @Test
    fun `returns null when provider does not advertise end_session_endpoint`() {
        val registrationId = "22222222-2222-3333-4444-555555555555"
        whenever(clientRegistrationRepository.findByRegistrationId(registrationId))
            .thenReturn(
                clientRegistrationWith(
                    registrationId = registrationId,
                    endSessionEndpoint = null,
                ),
            )

        val resolver = OidcEndSessionUrlResolver(clientRegistrationRepository, props)
        assertThat(resolver.resolve(registrationId, idTokenHint = "eyJ.fake.id-token")).isNull()
    }

    @Test
    fun `returns null when registrationId is unknown`() {
        whenever(clientRegistrationRepository.findByRegistrationId("ghost")).thenReturn(null)

        val resolver = OidcEndSessionUrlResolver(clientRegistrationRepository, props)
        assertThat(resolver.resolve("ghost", idTokenHint = null)).isNull()
    }

    @Test
    fun `omits id_token_hint when null but still appends post_logout_redirect_uri`() {
        val registrationId = "33333333-2222-3333-4444-555555555555"
        whenever(clientRegistrationRepository.findByRegistrationId(registrationId))
            .thenReturn(
                clientRegistrationWith(
                    registrationId = registrationId,
                    endSessionEndpoint = "http://kc/logout",
                ),
            )

        val resolver = OidcEndSessionUrlResolver(clientRegistrationRepository, props)
        val url = resolver.resolve(registrationId, idTokenHint = null)

        assertThat(url).isNotNull()
        assertThat(url!!).doesNotContain("id_token_hint")
        assertThat(url).contains("post_logout_redirect_uri=")
    }

    @Test
    fun `trims trailing slash from base-url before appending login path`() {
        val registrationId = "44444444-2222-3333-4444-555555555555"
        whenever(clientRegistrationRepository.findByRegistrationId(registrationId))
            .thenReturn(
                clientRegistrationWith(
                    registrationId = registrationId,
                    endSessionEndpoint = "http://kc/logout",
                ),
            )
        val propsWithSlash = PlugwerkProperties(
            server = PlugwerkProperties.ServerProperties(baseUrl = "http://app.local/"),
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "test-jwt-secret-at-least-32-characters-long",
                encryptionKey = "test-encryption-key-32-characters-",
            ),
        )

        val resolver = OidcEndSessionUrlResolver(clientRegistrationRepository, propsWithSlash)
        val url = resolver.resolve(registrationId, idTokenHint = null)

        assertThat(url!!).contains("post_logout_redirect_uri=http://app.local/login")
        // Sanity: a missed trim would produce `app.local//login`, which is a different
        // path and would silently fail the IdP allow-list match.
        assertThat(url).doesNotContain("app.local//login")
    }

    /**
     * Builds a [ClientRegistration] whose `providerDetails.configurationMetadata`
     * either contains or omits `end_session_endpoint`. The other fields are filled
     * with stubs that satisfy the builder's required-property contract; only the
     * metadata map matters for [OidcEndSessionUrlResolver].
     */
    private fun clientRegistrationWith(registrationId: String, endSessionEndpoint: String?): ClientRegistration {
        val metadata = if (endSessionEndpoint == null) {
            emptyMap()
        } else {
            mapOf("end_session_endpoint" to endSessionEndpoint)
        }
        return ClientRegistration.withRegistrationId(registrationId)
            .clientId("test-client")
            .clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/{registrationId}")
            .scope("openid")
            .authorizationUri("http://kc/authorize")
            .tokenUri("http://kc/token")
            .providerConfigurationMetadata(metadata)
            .build()
    }
}
