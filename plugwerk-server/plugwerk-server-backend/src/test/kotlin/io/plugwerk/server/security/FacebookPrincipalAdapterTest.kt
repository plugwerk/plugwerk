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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User

class FacebookPrincipalAdapterTest {

    private val adapter = FacebookPrincipalAdapter()

    private fun tokenWith(attributes: Map<String, Any>): OAuth2AuthenticationToken {
        val user = DefaultOAuth2User(emptyList(), attributes, "id")
        return OAuth2AuthenticationToken(user, emptyList(), "fb-registration")
    }

    @Test
    fun `claims only the FACEBOOK provider type`() {
        assertThat(adapter.providerTypes).containsExactly(OidcProviderType.FACEBOOK)
    }

    @Test
    fun `extracts numeric id as subject string, email and name from the userinfo response`() {
        // Facebook's `/me?fields=id,name,email` returns these fields as strings.
        val token = tokenWith(
            mapOf(
                "id" to "1234567890",
                "name" to "Mona Lisa",
                "email" to "mona@example.com",
            ),
        )

        val resolved = adapter.resolve(token)

        assertThat(resolved.subject).isEqualTo("1234567890")
        assertThat(resolved.email).isEqualTo("mona@example.com")
        assertThat(resolved.displayName).isEqualTo("Mona Lisa")
        assertThat(resolved.upstreamIdToken).isNull()
    }

    @Test
    fun `stringifies a numeric id (defensive — Facebook usually returns String, but be safe)`() {
        val token = tokenWith(
            mapOf(
                "id" to 9_876_543_210L,
                "name" to "Numeric",
                "email" to "n@example.com",
            ),
        )

        assertThat(adapter.resolve(token).subject).isEqualTo("9876543210")
    }

    @Test
    fun `returns null email when the userinfo response did not surface email`() {
        // Apps without Facebook App Review approval for `email` get a userinfo
        // response without the field — adapter must surface that as null so the
        // downstream raises OidcEmailMissingException with the App-Review
        // remediation message (#357 phase 2).
        val token = tokenWith(
            mapOf(
                "id" to "42",
                "name" to "App-Review-Pending",
            ),
        )

        assertThat(adapter.resolve(token).email).isNull()
    }

    @Test
    fun `returns null email when the email attribute is blank`() {
        val token = tokenWith(
            mapOf(
                "id" to "42",
                "name" to "X",
                "email" to "   ",
            ),
        )

        assertThat(adapter.resolve(token).email).isNull()
    }

    @Test
    fun `returns null displayName when name is blank (caller falls back to subject)`() {
        val token = tokenWith(
            mapOf(
                "id" to "42",
                "email" to "x@y.z",
            ),
        )

        assertThat(adapter.resolve(token).displayName).isNull()
    }
}
