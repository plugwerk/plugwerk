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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import java.time.Instant

/**
 * Covers audit findings SBS-010 (GitHub audience missing) and SBS-011 (Facebook issuer
 * missing) plus the parity audience/issuer hardening across OIDC and GOOGLE.
 *
 * Uses `Jwt.withTokenValue` to construct decoded-JWT instances directly — no JWKS mocking,
 * no signing. The validator layer is what matters for the security gap; signature
 * verification is Spring's concern and is covered elsewhere.
 */
class OidcJwtValidatorsTest {

    private val expectedAudience = "plugwerk-client-id-ABC"

    private fun jwt(iss: String, aud: List<String>?): Jwt {
        val builder = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .issuer(iss)
            .subject("alice")
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(600))
        if (aud != null) {
            builder.claim(JwtClaimNames.AUD, aud)
        }
        return builder.build()
    }

    // -------------------------- GITHUB (SBS-010 + new issuer check) --------------------

    @Test
    fun `GITHUB accepts token with correct issuer and audience`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.GITHUB, null, expectedAudience)
        val result = validator.validate(jwt(OidcJwtValidators.GITHUB_ISSUER, listOf(expectedAudience)))
        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `GITHUB rejects token with wrong audience (SBS-010)`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.GITHUB, null, expectedAudience)
        val result = validator.validate(jwt(OidcJwtValidators.GITHUB_ISSUER, listOf("some-other-app")))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `GITHUB rejects token with missing audience claim (SBS-010)`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.GITHUB, null, expectedAudience)
        val result = validator.validate(jwt(OidcJwtValidators.GITHUB_ISSUER, null))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `GITHUB rejects token with empty audience list`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.GITHUB, null, expectedAudience)
        val result = validator.validate(jwt(OidcJwtValidators.GITHUB_ISSUER, emptyList()))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `GITHUB rejects token with wrong issuer`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.GITHUB, null, expectedAudience)
        val result = validator.validate(jwt("https://evil.example.com", listOf(expectedAudience)))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `GITHUB accepts token whose audience list also contains other values`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.GITHUB, null, expectedAudience)
        val result = validator.validate(
            jwt(OidcJwtValidators.GITHUB_ISSUER, listOf("another-aud", expectedAudience, "yet-another")),
        )
        assertThat(result.hasErrors()).isFalse()
    }

    // -------------------------- FACEBOOK (SBS-011 + new audience check) ----------------

    @Test
    fun `FACEBOOK accepts token with correct issuer and audience`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.FACEBOOK, null, expectedAudience)
        val result = validator.validate(jwt(OidcJwtValidators.FACEBOOK_ISSUER, listOf(expectedAudience)))
        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `FACEBOOK rejects token with wrong issuer (SBS-011)`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.FACEBOOK, null, expectedAudience)
        val result = validator.validate(jwt("https://evil.example.com", listOf(expectedAudience)))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `FACEBOOK rejects token with wrong audience`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.FACEBOOK, null, expectedAudience)
        val result = validator.validate(jwt(OidcJwtValidators.FACEBOOK_ISSUER, listOf("different-app")))
        assertThat(result.hasErrors()).isTrue()
    }

    // -------------------------- GOOGLE parity ------------------------------------------

    @Test
    fun `GOOGLE accepts token with correct issuer and audience`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.GOOGLE, null, expectedAudience)
        val result = validator.validate(jwt(OidcJwtValidators.GOOGLE_ISSUER, listOf(expectedAudience)))
        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `GOOGLE rejects token with wrong audience`() {
        val validator = OidcJwtValidators.forProvider(OidcProviderType.GOOGLE, null, expectedAudience)
        val result = validator.validate(jwt(OidcJwtValidators.GOOGLE_ISSUER, listOf("different-app")))
        assertThat(result.hasErrors()).isTrue()
    }

    // -------------------------- OIDC (any standards-compliant provider) ---------------

    @Test
    fun `OIDC uses configured issuerUri for validation`() {
        val configuredIssuer = "https://keycloak.example.com/realms/plugwerk"
        val validator = OidcJwtValidators.forProvider(
            OidcProviderType.OIDC,
            configuredIssuer,
            expectedAudience,
        )
        assertThat(validator.validate(jwt(configuredIssuer, listOf(expectedAudience))).hasErrors()).isFalse()
        assertThat(validator.validate(jwt("https://other.example.com", listOf(expectedAudience))).hasErrors())
            .isTrue()
    }

    @Test
    fun `OIDC rejects token with wrong audience`() {
        val configuredIssuer = "https://keycloak.example.com/realms/plugwerk"
        val validator = OidcJwtValidators.forProvider(
            OidcProviderType.OIDC,
            configuredIssuer,
            expectedAudience,
        )
        assertThat(validator.validate(jwt(configuredIssuer, listOf("wrong-aud"))).hasErrors()).isTrue()
    }

    // -------------------------- Configuration errors -----------------------------------

    @Test
    fun `blank expectedAudience is rejected as a configuration error`() {
        assertThatThrownBy {
            OidcJwtValidators.forProvider(OidcProviderType.GITHUB, null, "")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("expectedAudience")
    }

    @Test
    fun `missing issuerUri for OIDC is rejected as a configuration error`() {
        assertThatThrownBy {
            OidcJwtValidators.forProvider(OidcProviderType.OIDC, null, expectedAudience)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("issuerUri")
    }

    @Test
    fun `blank issuerUri for OIDC is rejected as a configuration error`() {
        assertThatThrownBy {
            OidcJwtValidators.forProvider(OidcProviderType.OIDC, "   ", expectedAudience)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("issuerUri")
    }
}
