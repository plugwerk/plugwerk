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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator

/**
 * Builds the validator chain applied to every OIDC [org.springframework.security.oauth2.jwt.JwtDecoder]
 * in [OidcProviderRegistry]. Extracted from the registry so the validator composition can be
 * unit-tested without spinning up a JWKS endpoint.
 *
 * Every decoder installs the same three validators:
 * - [JwtTimestampValidator] — `exp` / `nbf` enforcement (must be re-added because
 *   `setJwtValidator` replaces Spring's default validator set).
 * - [JwtIssuerValidator] — rejects tokens whose `iss` does not match the provider's canonical
 *   issuer. This closes audit finding SBS-011 for [OidcProviderType.FACEBOOK] and hardens
 *   [OidcProviderType.GITHUB] (which had no issuer validator at all).
 * - Audience validator — rejects tokens whose `aud` claim does not contain the configured
 *   client_id. This closes audit finding SBS-010 for [OidcProviderType.GITHUB] and uniformly
 *   hardens the other provider types.
 *
 * Canonical issuers for the three vendor providers are hardcoded here — they are not
 * operator-configurable, by design, to prevent misconfiguration from turning off the issuer
 * check for the exploitable vendor flows.
 */
object OidcJwtValidators {

    /** GitHub Actions OIDC issuer (https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect). */
    const val GITHUB_ISSUER: String = "https://token.actions.githubusercontent.com"

    /**
     * Facebook Limited Login / OIDC issuer.
     * See `https://developers.facebook.com/docs/facebook-login/limited-login`.
     */
    const val FACEBOOK_ISSUER: String = "https://www.facebook.com"

    /**
     * Google OIDC issuer as declared at
     * `https://accounts.google.com/.well-known/openid-configuration`.
     *
     * Note: Google ID tokens may also present the bare-host form `accounts.google.com`.
     * The current implementation pins the canonical HTTPS form only; generalising to a
     * multi-issuer set is a future hardening item not covered by #258.
     */
    const val GOOGLE_ISSUER: String = "https://accounts.google.com"

    /**
     * Builds the full validator chain for a provider. Both [issuerUri] presence and
     * [expectedAudience] blankness are guarded — pass-through a blank or null value
     * means a configuration error and the function throws [IllegalArgumentException].
     *
     * @param providerType which vendor (or custom) OIDC flow this validator serves
     * @param issuerUri issuer URI from [io.plugwerk.server.domain.OidcProviderEntity.issuerUri]
     *   — only consulted for [OidcProviderType.OIDC]; ignored for the three vendor types
     *   which use hardcoded canonical issuers
     * @param expectedAudience value expected in the token's `aud` claim; must match
     *   [io.plugwerk.server.domain.OidcProviderEntity.clientId]
     */
    fun forProvider(
        providerType: OidcProviderType,
        issuerUri: String?,
        expectedAudience: String,
    ): OAuth2TokenValidator<Jwt> {
        require(expectedAudience.isNotBlank()) {
            "expectedAudience (client_id) must not be blank for OIDC provider of type $providerType"
        }
        val issuer = when (providerType) {
            OidcProviderType.OIDC -> {
                require(!issuerUri.isNullOrBlank()) {
                    "issuerUri is required for OIDC provider type $providerType"
                }
                issuerUri
            }

            OidcProviderType.GOOGLE -> GOOGLE_ISSUER

            OidcProviderType.GITHUB -> GITHUB_ISSUER

            OidcProviderType.FACEBOOK -> FACEBOOK_ISSUER

            // OAUTH2 providers normally issue opaque tokens (no JWT,
            // hence no resource-server JWT validation path). When an operator
            // configures a `jwkSetUri`, the issuer for `iss`-claim validation
            // is the user-info-host or operator-configured value; without an
            // explicit issuer we fall back to the issuerUri field which can
            // hold a free-form value here (it is otherwise unused for
            // OAUTH2). Strict validation rejects empty issuer.
            OidcProviderType.OAUTH2 -> {
                require(!issuerUri.isNullOrBlank()) {
                    "issuerUri (used as the expected `iss` value) must be set on an OAUTH2 " +
                        "provider before resource-server JWT validation can be enabled. Leave " +
                        "jwkSetUri null to skip resource-server validation entirely."
                }
                issuerUri
            }
        }
        return DelegatingOAuth2TokenValidator(
            JwtTimestampValidator(),
            JwtIssuerValidator(issuer),
            audienceValidator(expectedAudience),
        )
    }

    /**
     * Validates that the token's `aud` claim contains [expected].
     *
     * Spring Security parses `aud` into `List<String>` regardless of whether the wire
     * format was a single string or an array (per RFC 7519 §4.1.3), so a single
     * `contains` check covers both shapes uniformly.
     */
    private fun audienceValidator(expected: String): OAuth2TokenValidator<Jwt> =
        JwtClaimValidator<List<String>?>(JwtClaimNames.AUD) { aud ->
            !aud.isNullOrEmpty() && aud.contains(expected)
        }
}
