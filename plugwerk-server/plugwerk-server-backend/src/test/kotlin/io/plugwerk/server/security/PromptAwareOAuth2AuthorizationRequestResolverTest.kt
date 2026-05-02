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

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcProviderRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptAwareOAuth2AuthorizationRequestResolverTest {

    @Mock lateinit var oidcProviderRepository: OidcProviderRepository

    private val providerId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    /**
     * The Spring `ClientRegistrationRepository` the resolver is constructed
     * over. We register one in-memory `ClientRegistration` keyed by the test
     * provider's UUID-as-string so the underlying
     * `DefaultOAuth2AuthorizationRequestResolver` can resolve `/oauth2/authorization/{id}`
     * to a real authorization request.
     */
    private fun stubClientRegistrationRepository(): ClientRegistrationRepository {
        val registration = ClientRegistration.withRegistrationId(providerId.toString())
            .clientId("test-client")
            .clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/{registrationId}")
            .authorizationUri("https://idp.example/authorize")
            .tokenUri("https://idp.example/token")
            .userInfoUri("https://idp.example/userinfo")
            .userNameAttributeName("sub")
            .scope("openid", "email")
            .clientName("Test")
            .build()
        return ClientRegistrationRepository { id -> if (id == providerId.toString()) registration else null }
    }

    private fun resolver() = PromptAwareOAuth2AuthorizationRequestResolver(
        stubClientRegistrationRepository(),
        oidcProviderRepository,
    )

    /**
     * Class-level `Strictness.LENIENT` because some test cases never reach
     * `findById` — when the inbound `prompt` is invalid the resolver
     * short-circuits before the provider-type lookup. Strict Mockito would
     * flag those stubs as unused, which is technically true but masks the
     * *important* check (does the resolver still strip `prompt` for those
     * cases).
     */
    private fun stubProvider(type: OidcProviderType) {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(
            Optional.of(
                OidcProviderEntity(
                    id = providerId,
                    name = "Test",
                    providerType = type,
                    enabled = true,
                    clientId = "test-client",
                    clientSecretEncrypted = "{cipher}ignored",
                    issuerUri = "https://idp.example/realms/x",
                ),
            ),
        )
    }

    private fun authorizeRequest(promptParam: String? = null, multiplePrompts: List<String>? = null) =
        MockHttpServletRequest("GET", "/oauth2/authorization/$providerId").apply {
            servletPath = "/oauth2/authorization/$providerId"
            when {
                multiplePrompts != null -> setParameter("prompt", *multiplePrompts.toTypedArray())
                promptParam != null -> setParameter("prompt", promptParam)
            }
        }

    @Test
    fun `prompt=select_account is added to additionalParameters for OIDC provider`() {
        stubProvider(OidcProviderType.OIDC)
        val resolved = resolver().resolve(authorizeRequest(promptParam = "select_account"))

        assertThat(resolved).isNotNull
        assertThat(resolved!!.additionalParameters).containsEntry("prompt", "select_account")
    }

    @Test
    fun `prompt=login is added to additionalParameters for OAUTH2 generic provider`() {
        stubProvider(OidcProviderType.OAUTH2)
        val resolved = resolver().resolve(authorizeRequest(promptParam = "login"))

        assertThat(resolved!!.additionalParameters).containsEntry("prompt", "login")
    }

    @Test
    fun `prompt is dropped for GitHub provider regardless of inbound value`() {
        // GitHub silently ignores `prompt` upstream — passing it would
        // mislead operators about what is happening. The resolver must
        // strip it for GitHub no matter what the caller sent.
        stubProvider(OidcProviderType.GITHUB)
        val resolved = resolver().resolve(authorizeRequest(promptParam = "select_account"))

        assertThat(resolved!!.additionalParameters).doesNotContainKey("prompt")
    }

    @Test
    fun `unknown prompt values are dropped silently (no inject vector)`() {
        stubProvider(OidcProviderType.OIDC)
        for (rogueValue in listOf("consent", "none", "arbitrary-junk", "")) {
            val resolved = resolver().resolve(authorizeRequest(promptParam = rogueValue))
            assertThat(resolved!!.additionalParameters)
                .`as`("rogue prompt value '$rogueValue' must be dropped")
                .doesNotContainKey("prompt")
        }
    }

    @Test
    fun `parameter pollution (multiple prompt values) is dropped`() {
        // ?prompt=login&prompt=select_account — naive `getParameter` would
        // pick the first; we explicitly require exactly one value via
        // `getParameterValues(...).size == 1`.
        stubProvider(OidcProviderType.OIDC)
        val resolved = resolver().resolve(
            authorizeRequest(multiplePrompts = listOf("login", "select_account")),
        )

        assertThat(resolved!!.additionalParameters).doesNotContainKey("prompt")
    }

    @Test
    fun `missing prompt query parameter leaves the request unchanged (silent SSO default)`() {
        stubProvider(OidcProviderType.OIDC)
        val resolved = resolver().resolve(authorizeRequest())

        assertThat(resolved!!.additionalParameters).doesNotContainKey("prompt")
    }

    @Test
    fun `state, scope, redirect_uri, and PKCE attributes are preserved when prompt is added`() {
        // The most dangerous failure mode: rebuilding the authorization
        // request via `from(...)` could in principle drop fields. Pin
        // every piece the upstream needs to round-trip the flow.
        stubProvider(OidcProviderType.OIDC)
        val resolved = resolver().resolve(authorizeRequest(promptParam = "select_account"))!!

        assertThat(resolved.state).isNotBlank
        assertThat(resolved.scopes).contains("openid")
        assertThat(resolved.redirectUri).isEqualTo("http://localhost/login/oauth2/code/$providerId")
        // PKCE pieces sit on the `attributes` and `additionalParameters` of
        // the resolved request — Spring sets `code_verifier` on attributes
        // and the derived `code_challenge` + `code_challenge_method` on
        // additionalParameters.
        assertThat(resolved.attributes).containsKey("code_verifier")
        assertThat(resolved.additionalParameters).containsKey("code_challenge")
        assertThat(resolved.additionalParameters).containsEntry("code_challenge_method", "S256")
        // And of course the prompt we just added.
        assertThat(resolved.additionalParameters).containsEntry("prompt", "select_account")
    }

    @Test
    fun `unresolvable registration id returns null without contacting the provider repository`() {
        // The DefaultOAuth2AuthorizationRequestResolver returns null when
        // the path does not match an `/oauth2/authorization/{id}` URL —
        // our wrapper must propagate that without crashing.
        val request = MockHttpServletRequest("GET", "/some/other/path").apply {
            servletPath = "/some/other/path"
        }
        val resolved = resolver().resolve(request)

        assertThat(resolved).isNull()
    }
}
