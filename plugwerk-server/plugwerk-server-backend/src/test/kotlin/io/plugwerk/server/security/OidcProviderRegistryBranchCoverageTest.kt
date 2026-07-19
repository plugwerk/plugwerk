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
import io.plugwerk.server.security.url.OidcSsrfPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Branch-coverage focused unit tests for [OidcProviderRegistry.refresh]. Every
 * provider-type arm is exercised. The "success" arms (OIDC / GOOGLE / GITHUB /
 * FACEBOOK / OAUTH2-with-jwks) all attempt real network IO via
 * `JwtDecoders.fromIssuerLocation` / `NimbusJwtDecoder.build`, which throws in a
 * unit-test sandbox and therefore lands on the `runCatching { … }.onFailure`
 * branch — that still covers the branch and the provider is skipped (decoder
 * list excludes it). The `requireNotNull` arms (OIDC null issuerUri, OAUTH2 null
 * jwkSetUri) throw inside `runCatching` and are likewise skipped.
 *
 * The repository mock is stubbed BEFORE construction because the class calls
 * [OidcProviderRegistry.refresh] from its `init` block.
 */
class OidcProviderRegistryBranchCoverageTest {

    private val ssrfPolicy = OidcSsrfPolicy(allowPrivateDiscoveryUris = false)

    private fun provider(
        type: OidcProviderType,
        issuerUri: String? = null,
        jwkSetUri: String? = null,
        name: String = "p",
    ) = OidcProviderEntity(
        id = UUID.randomUUID(),
        name = name,
        providerType = type,
        enabled = true,
        clientId = "client-$name",
        clientSecretEncrypted = "enc",
        issuerUri = issuerUri,
        scope = "openid",
        jwkSetUri = jwkSetUri,
    )

    private fun registryWith(vararg providers: OidcProviderEntity): OidcProviderRegistry {
        val repo: OidcProviderRepository = mock()
        whenever(repo.findAllByEnabledTrue()).thenReturn(providers.toList())
        // init { refresh() } runs here against the stub above.
        return OidcProviderRegistry(repo, ssrfPolicy)
    }

    @Test
    fun `refresh with no enabled providers yields an empty decoder list`() {
        val registry = registryWith()

        assertThat(registry.decoders()).isEmpty()
    }

    @Test
    fun `OIDC provider with null issuerUri is skipped (requireNotNull throws, caught)`() {
        val registry = registryWith(provider(OidcProviderType.OIDC, issuerUri = null))

        assertThat(registry.decoders()).isEmpty()
    }

    @Test
    fun `OIDC provider with a private issuerUri is skipped (SSRF guard rejects, caught)`() {
        val registry = registryWith(
            provider(OidcProviderType.OIDC, issuerUri = "http://10.0.0.1/realms/internal"),
        )

        assertThat(registry.decoders()).isEmpty()
    }

    @Test
    fun `OIDC provider with an unreachable public issuerUri is skipped (network fails, caught)`() {
        // RFC 2606 reserved domain — passes the SSRF guard, then the discovery
        // network call fails fast and lands on the onFailure branch.
        val registry = registryWith(
            provider(OidcProviderType.OIDC, issuerUri = "https://idp.example.invalid/realms/x"),
        )

        assertThat(registry.decoders()).isEmpty()
    }

    @Test
    fun `GOOGLE provider arm is exercised and skipped when discovery fails`() {
        // Hits the GOOGLE branch (fromIssuerLocation against the hardcoded
        // Google issuer). In a sandbox this fails and is caught.
        val registry = registryWith(provider(OidcProviderType.GOOGLE))

        // Either it resolved (network available) or was skipped; the branch is
        // covered regardless. Assert the call did not throw out of refresh().
        assertThat(registry.decoders()).hasSizeLessThanOrEqualTo(1)
    }

    @Test
    fun `GITHUB provider arm is exercised without throwing out of refresh`() {
        // NimbusJwtDecoder.withJwkSetUri(...).build() is lazy — it does not dial
        // the network until first decode — so this arm typically succeeds and
        // adds a decoder. Either way refresh() must not propagate.
        val registry = registryWith(provider(OidcProviderType.GITHUB))

        assertThat(registry.decoders()).hasSizeLessThanOrEqualTo(1)
    }

    @Test
    fun `FACEBOOK provider arm is exercised without throwing out of refresh`() {
        val registry = registryWith(provider(OidcProviderType.FACEBOOK))

        assertThat(registry.decoders()).hasSizeLessThanOrEqualTo(1)
    }

    @Test
    fun `OAUTH2 provider with null jwkSetUri is skipped (requireNotNull throws, caught)`() {
        val registry = registryWith(provider(OidcProviderType.OAUTH2, jwkSetUri = null))

        assertThat(registry.decoders()).isEmpty()
    }

    @Test
    fun `OAUTH2 provider with a private jwkSetUri is skipped (SSRF guard rejects, caught)`() {
        val registry = registryWith(
            provider(OidcProviderType.OAUTH2, jwkSetUri = "http://127.0.0.1/jwks"),
        )

        assertThat(registry.decoders()).isEmpty()
    }

    @Test
    fun `OAUTH2 provider with a public jwkSetUri builds a lazy decoder`() {
        // Public host passes the guard; NimbusJwtDecoder.build() is lazy so the
        // success arm completes and the decoder is registered. OAUTH2 also
        // requires a non-blank issuerUri (used as the expected `iss`) for the
        // validator chain — without it forProvider's require() would throw.
        val registry = registryWith(
            provider(
                OidcProviderType.OAUTH2,
                issuerUri = "https://idp.example.com",
                jwkSetUri = "https://idp.example.com/jwks",
            ),
        )

        assertThat(registry.decoders()).hasSize(1)
    }

    @Test
    fun `decoders reflects the providers present at the most recent refresh`() {
        val repo: OidcProviderRepository = mock()
        // First refresh (init) sees nothing.
        whenever(repo.findAllByEnabledTrue()).thenReturn(emptyList())
        val registry = OidcProviderRegistry(repo, ssrfPolicy)
        assertThat(registry.decoders()).isEmpty()

        // Now a public OAUTH2 provider appears; an explicit refresh picks it up.
        whenever(repo.findAllByEnabledTrue()).thenReturn(
            listOf(
                provider(
                    OidcProviderType.OAUTH2,
                    issuerUri = "https://idp.example.com",
                    jwkSetUri = "https://idp.example.com/jwks",
                ),
            ),
        )
        registry.refresh()

        assertThat(registry.decoders()).hasSize(1)
    }

    @Test
    fun `refresh mixes a buildable provider with a skipped one`() {
        val registry = registryWith(
            provider(
                OidcProviderType.OAUTH2,
                issuerUri = "https://idp.example.com",
                jwkSetUri = "https://idp.example.com/jwks",
                name = "good",
            ),
            provider(OidcProviderType.OAUTH2, jwkSetUri = null, name = "bad"),
        )

        // The buildable one survives; the null-jwks one is skipped.
        assertThat(registry.decoders()).hasSize(1)
    }
}
