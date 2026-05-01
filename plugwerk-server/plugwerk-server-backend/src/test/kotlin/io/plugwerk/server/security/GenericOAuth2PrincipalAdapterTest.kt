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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GenericOAuth2PrincipalAdapterTest {

    @Mock lateinit var oidcProviderRepository: OidcProviderRepository

    private val providerId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    private fun adapter() = GenericOAuth2PrincipalAdapter(oidcProviderRepository)

    private fun providerEntity(
        subjectAttribute: String? = null,
        emailAttribute: String? = null,
        displayNameAttribute: String? = null,
        name: String = "Custom IdP",
    ) = OidcProviderEntity(
        id = providerId,
        name = name,
        providerType = OidcProviderType.OAUTH2,
        enabled = true,
        clientId = "client",
        clientSecretEncrypted = "{cipher}ignored",
        authorizationUri = "https://idp.example/oauth/authorize",
        tokenUri = "https://idp.example/oauth/token",
        userInfoUri = "https://idp.example/api/me",
        subjectAttribute = subjectAttribute,
        emailAttribute = emailAttribute,
        displayNameAttribute = displayNameAttribute,
    )

    private fun tokenWith(attributes: Map<String, Any>, nameAttributeKey: String = "sub"): OAuth2AuthenticationToken {
        val user = DefaultOAuth2User(emptyList(), attributes, nameAttributeKey)
        return OAuth2AuthenticationToken(user, emptyList(), providerId.toString())
    }

    @Test
    fun `claims only the OAUTH2 provider type`() {
        assertThat(adapter().providerTypes).containsExactly(OidcProviderType.OAUTH2)
    }

    @Test
    fun `uses default sub email name keys when operator left attributes blank`() {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(providerEntity()))
        val token = tokenWith(
            attributes = mapOf(
                "sub" to "subject-123",
                "email" to "user@example.com",
                "name" to "Generic User",
            ),
        )

        val resolved = adapter().resolve(token)

        assertThat(resolved.subject).isEqualTo("subject-123")
        assertThat(resolved.email).isEqualTo("user@example.com")
        assertThat(resolved.displayName).isEqualTo("Generic User")
        assertThat(resolved.upstreamIdToken).isNull()
    }

    @Test
    fun `respects operator-supplied attribute names (GitLab style)`() {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(
            Optional.of(
                providerEntity(
                    subjectAttribute = "id",
                    emailAttribute = "primary_email",
                    displayNameAttribute = "username",
                ),
            ),
        )
        val token = tokenWith(
            attributes = mapOf(
                "id" to 42L,
                "primary_email" to "alice@gitlab.example",
                "username" to "alice",
            ),
            nameAttributeKey = "id",
        )

        val resolved = adapter().resolve(token)

        assertThat(resolved.subject).isEqualTo("42")
        assertThat(resolved.email).isEqualTo("alice@gitlab.example")
        assertThat(resolved.displayName).isEqualTo("alice")
    }

    @Test
    fun `returns null email when the configured attribute is absent (downstream raises OidcEmailMissingException)`() {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(providerEntity()))
        val token = tokenWith(
            attributes = mapOf(
                "sub" to "subject-only",
                // no `email` key — the user-info response simply did not include it
                "name" to "No Email User",
            ),
        )

        val resolved = adapter().resolve(token)

        assertThat(resolved.email).isNull()
        assertThat(resolved.subject).isEqualTo("subject-only")
    }

    @Test
    fun `returns null displayName when the configured attribute is blank`() {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(providerEntity()))
        val token = tokenWith(
            attributes = mapOf(
                "sub" to "s",
                "email" to "e@x.y",
                "name" to "   ",
            ),
        )

        assertThat(adapter().resolve(token).displayName).isNull()
    }

    @Test
    fun `throws when subject attribute is missing — provider name surfaced in message`() {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(
            Optional.of(providerEntity(subjectAttribute = "user_id", name = "ACME OAuth")),
        )
        val token = tokenWith(
            attributes = mapOf(
                "id" to "wrong-key",
                "email" to "e@x.y",
            ),
            // The DefaultOAuth2User constructor requires nameAttributeKey to be
            // present in attributes, so we point it at a present key. The
            // adapter then reads from `user_id` (as configured), which is
            // absent → the explicit error path fires.
            nameAttributeKey = "id",
        )

        assertThatThrownBy { adapter().resolve(token) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ACME OAuth")
            .hasMessageContaining("user_id")
    }

    @Test
    fun `throws when registrationId is not a valid UUID`() {
        val user = DefaultOAuth2User(emptyList(), mapOf("sub" to "x"), "sub")
        val token = OAuth2AuthenticationToken(user, emptyList(), "not-a-uuid")

        assertThatThrownBy { adapter().resolve(token) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not-a-uuid")
    }

    @Test
    fun `throws when the provider row was deleted between filter and adapter`() {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.empty())
        val token = tokenWith(attributes = mapOf("sub" to "x"))

        assertThatThrownBy { adapter().resolve(token) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(providerId.toString())
    }

    @Test
    fun `coerces non-string subject (numeric) to string for storage compatibility`() {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(
            Optional.of(providerEntity(subjectAttribute = "id")),
        )
        val token = tokenWith(
            attributes = mapOf("id" to 9_876_543_210L, "email" to "e@x.y"),
            nameAttributeKey = "id",
        )

        assertThat(adapter().resolve(token).subject).isEqualTo("9876543210")
    }
}
