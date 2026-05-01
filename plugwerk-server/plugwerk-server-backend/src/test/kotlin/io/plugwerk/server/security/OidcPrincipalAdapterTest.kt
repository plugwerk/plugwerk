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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.time.Instant

class OidcPrincipalAdapterTest {

    private val adapter = OidcPrincipalAdapter()

    @Test
    fun `claims OIDC and GOOGLE provider types`() {
        assertThat(adapter.providerTypes)
            .containsExactlyInAnyOrder(OidcProviderType.OIDC, OidcProviderType.GOOGLE)
    }

    @Test
    fun `extracts subject email displayName and upstreamIdToken from OidcUser principal`() {
        val now = Instant.now()
        val idToken = OidcIdToken(
            "eyJraWQ.fake.signature",
            now,
            now.plusSeconds(300),
            mapOf(
                "sub" to "alice-sub",
                "email" to "alice@example.test",
                "name" to "Alice Anderson",
            ),
        )
        val user = DefaultOidcUser(emptyList(), idToken)
        val token = OAuth2AuthenticationToken(user, emptyList(), "registration-id")

        val resolved = adapter.resolve(token)

        assertThat(resolved.subject).isEqualTo("alice-sub")
        assertThat(resolved.email).isEqualTo("alice@example.test")
        assertThat(resolved.displayName).isEqualTo("Alice Anderson")
        assertThat(resolved.upstreamIdToken).isEqualTo("eyJraWQ.fake.signature")
    }

    @Test
    fun `falls back to preferred_username when name claim is absent`() {
        val now = Instant.now()
        val idToken = OidcIdToken(
            "tok",
            now,
            now.plusSeconds(300),
            mapOf(
                "sub" to "bob-sub",
                "email" to "bob@example.test",
                "preferred_username" to "bob",
            ),
        )
        val user = DefaultOidcUser(emptyList(), idToken)
        val token = OAuth2AuthenticationToken(user, emptyList(), "registration-id")

        val resolved = adapter.resolve(token)

        assertThat(resolved.displayName).isEqualTo("bob")
    }

    @Test
    fun `returns null displayName when neither name nor preferred_username present (caller falls back to subject)`() {
        val now = Instant.now()
        val idToken = OidcIdToken(
            "tok",
            now,
            now.plusSeconds(300),
            mapOf("sub" to "carol-sub", "email" to "carol@example.test"),
        )
        val user = DefaultOidcUser(emptyList(), idToken)
        val token = OAuth2AuthenticationToken(user, emptyList(), "registration-id")

        assertThat(adapter.resolve(token).displayName).isNull()
    }

    @Test
    fun `returns null email when claim is missing or blank (caller raises OidcEmailMissingException)`() {
        val now = Instant.now()
        val idToken = OidcIdToken(
            "tok",
            now,
            now.plusSeconds(300),
            mapOf("sub" to "dave-sub"),
        )
        val user = DefaultOidcUser(emptyList(), idToken)
        val token = OAuth2AuthenticationToken(user, emptyList(), "registration-id")

        assertThat(adapter.resolve(token).email).isNull()
    }

    @Test
    fun `upstreamIdToken is null for non-OIDC OAuth2 principals (caller passes null to refresh-token issue)`() {
        // DefaultOAuth2User has no idToken — emulates a future Google flow stub
        // or any provider where the principal is OAuth2 not OIDC. The adapter
        // still returns a usable ResolvedPrincipal; only upstreamIdToken is null.
        val user = DefaultOAuth2User(
            emptyList(),
            mapOf("sub" to "eve-sub", "email" to "eve@example.test"),
            "sub",
        )
        val token = OAuth2AuthenticationToken(user, emptyList(), "registration-id")

        val resolved = adapter.resolve(token)

        assertThat(resolved.subject).isEqualTo("eve-sub")
        assertThat(resolved.upstreamIdToken).isNull()
    }

    @Test
    fun `throws when sub claim is missing (wiring failure, not user-facing)`() {
        val now = Instant.now()
        val idToken = OidcIdToken(
            "tok",
            now,
            now.plusSeconds(300),
            mapOf("email" to "x@example.test", "iss" to "https://idp.example/"),
        )
        val user = DefaultOidcUser(emptyList(), idToken, "iss")
        val token = OAuth2AuthenticationToken(user, emptyList(), "registration-id")

        assertThatThrownBy { adapter.resolve(token) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("sub")
    }
}
