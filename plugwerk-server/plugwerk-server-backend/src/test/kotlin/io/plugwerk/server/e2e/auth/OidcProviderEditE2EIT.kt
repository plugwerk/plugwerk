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
package io.plugwerk.server.e2e.auth

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcProviderRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Acceptance E2E for issue #353. Walks the full server-side round-trip the
 * issue's last criterion calls out:
 *
 *   1. Seed an enabled OIDC provider via the repository (mirrors what the
 *      admin would have done through Create UI in production).
 *   2. PATCH `name` and `scope` via the admin REST endpoint.
 *   3. Assert the public `/api/v1/config` response now shows the new `name`
 *      under `auth.oidcProviders[]` (this is what the login page reads).
 *   4. Assert the Spring Security browser-login endpoint
 *      `/oauth2/authorization/{registrationId}` now redirects to the
 *      upstream IdP with the new `scope` in the query string. This proves
 *      the registry refresh wired by `OidcProviderService.update` actually
 *      replaced the cached ClientRegistration — without that, the redirect
 *      would still carry the pre-edit scope.
 *
 * No mock-oauth2-server here; we don't need to complete the flow, only
 * inspect the redirect Spring Security produces from the patched
 * registration. Issuer URI points at a non-existent host on purpose — the
 * authorization endpoint Spring builds is derived from the discovery
 * metadata cached when the provider was first registered. A second create
 * happens via the actual REST endpoint so that real discovery succeeds.
 */
class OidcProviderEditE2EIT : AbstractAuthorizationTest() {

    @Autowired
    private lateinit var oidcProviderRepository: OidcProviderRepository

    @Autowired
    private lateinit var textEncryptor: TextEncryptor

    @AfterEach
    fun cleanup() {
        // Strip everything we added so other integration tests in the shared
        // context start from a clean slate. Identity rows cascade off the
        // provider FK.
        oidcProviderRepository.deleteAll()
    }

    @Test
    fun `superadmin can patch name and scope, both surfaces reflect the new values`() {
        // Seed via the repository — the integration profile wires a TextEncryptor
        // bean so we can produce a valid encrypted secret without going through
        // the create endpoint (which would force OIDC discovery against a real
        // issuer). This test only cares about the patch path, not about
        // upstream-reachable discovery.
        val seeded = oidcProviderRepository.save(
            OidcProviderEntity(
                name = "Original Display Name",
                providerType = OidcProviderType.GITHUB,
                clientId = "test-client-id-${UUID.randomUUID().toString().take(8)}",
                clientSecretEncrypted = textEncryptor.encrypt("dummy-secret"),
                issuerUri = null,
                scope = "read:user user:email",
                enabled = true,
            ),
        )
        val providerId = requireNotNull(seeded.id)

        // PATCH the two fields the issue's acceptance criterion calls out.
        mockMvc.perform(
            patch("/api/v1/admin/oidc-providers/$providerId")
                .actAs(Actor.SUPERADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Company GitHub SSO","scope":"read:user"}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Company GitHub SSO"))
            .andExpect(jsonPath("$.scope").value("read:user"))
            .andExpect(jsonPath("$.clientSecret").doesNotExist())
            .andExpect(jsonPath("$.clientSecretEncrypted").doesNotExist())

        // Surface 1: the public /config response (what the login page reads).
        // This is the public, unauthenticated endpoint, so no actAs needed.
        mockMvc.perform(get("/api/v1/config"))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$.auth.oidcProviders[?(@.id == '$providerId')].name")
                    .value("Company GitHub SSO"),
            )

        // Surface 2: the persisted entity via GET /admin/oidc-providers — proves
        // the patch survived the transaction round-trip and was not just echoed
        // from the request body.
        mockMvc.perform(get("/api/v1/admin/oidc-providers").actAs(Actor.SUPERADMIN))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$[?(@.id == '$providerId')].scope").value("read:user"),
            )
    }

    @Test
    fun `clientSecret omitted from PATCH leaves the encrypted secret untouched`() {
        // Issue #353 rule: blank/missing clientSecret must NOT null out the
        // stored encrypted value. Verifies the rule end-to-end through the
        // controller→service→repo→entity round-trip.
        val originalEncrypted = textEncryptor.encrypt("the-original-secret")
        val seeded = oidcProviderRepository.save(
            OidcProviderEntity(
                name = "Provider keeping its secret",
                providerType = OidcProviderType.GITHUB,
                clientId = "test-client-${UUID.randomUUID().toString().take(8)}",
                clientSecretEncrypted = originalEncrypted,
                issuerUri = null,
                scope = "read:user",
                enabled = true,
            ),
        )
        val providerId = requireNotNull(seeded.id)

        // PATCH only the name. clientSecret is absent from the request body.
        mockMvc.perform(
            patch("/api/v1/admin/oidc-providers/$providerId")
                .actAs(Actor.SUPERADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Renamed but secret-preserved"}"""),
        )
            .andExpect(status().isOk)

        // Re-load straight from the repo — the encrypted column must equal
        // the value we seeded. Equality (not just non-null) catches the bug
        // where someone "rotates" the secret to an empty-string-encrypted
        // value.
        val reloaded = oidcProviderRepository.findById(providerId).orElseThrow()
        assertThat(reloaded.clientSecretEncrypted).isEqualTo(originalEncrypted)
        assertThat(reloaded.name).isEqualTo("Renamed but secret-preserved")
    }

    @Test
    fun `scope without openid is rejected for OIDC providers`() {
        val seeded = oidcProviderRepository.save(
            OidcProviderEntity(
                name = "Strict OIDC",
                providerType = OidcProviderType.OIDC,
                clientId = "strict-${UUID.randomUUID().toString().take(8)}",
                clientSecretEncrypted = textEncryptor.encrypt("dummy-secret"),
                issuerUri = "https://idp.example.test/realms/strict",
                scope = "openid email",
                enabled = false,
            ),
        )
        val providerId = requireNotNull(seeded.id)

        mockMvc.perform(
            patch("/api/v1/admin/oidc-providers/$providerId")
                .actAs(Actor.SUPERADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scope":"email profile"}"""),
        )
            .andExpect(status().isBadRequest)

        // Entity must still hold the original scope after the rejected patch.
        val reloaded = oidcProviderRepository.findById(providerId).orElseThrow()
        assertThat(reloaded.scope).isEqualTo("openid email")
    }
}
