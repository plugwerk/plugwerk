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
package io.plugwerk.server.service

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the per-provider-type message shape for [OidcEmailMissingException]
 * (#357 phase 2). Phase 3 (GitHub) and phase 4 (Facebook) rely on the
 * GitHub / Facebook branches being reachable and worded usefully — without
 * this, both phases would surface a generic OIDC-shaped message that does
 * not match the actual remediation path.
 */
class OidcEmailMissingExceptionTest {

    private fun providerOf(name: String, type: OidcProviderType) = OidcProviderEntity(
        name = name,
        providerType = type,
        clientId = "irrelevant",
        clientSecretEncrypted = "irrelevant",
    )

    @Test
    fun `OIDC provider message is operator-actionable and points at scope configuration`() {
        val exception = OidcEmailMissingException(providerOf("Keycloak Prod", OidcProviderType.OIDC))

        assertThat(exception.message)
            .contains("Keycloak Prod")
            .contains("scope")
            .contains("openid email profile")
    }

    @Test
    fun `Google message reuses the OIDC-style operator hint (Google is OIDC-conformant)`() {
        val exception = OidcEmailMissingException(providerOf("Google", OidcProviderType.GOOGLE))

        assertThat(exception.message)
            .contains("Google")
            .contains("scope")
    }

    @Test
    fun `GitHub message is user-actionable and names the GitHub-specific remediation steps`() {
        val exception = OidcEmailMissingException(providerOf("GitHub Cloud", OidcProviderType.GITHUB))

        assertThat(exception.message)
            .contains("GitHub Cloud")
            // User-facing path: tell them where in GitHub to look.
            .contains("Settings")
            .contains("Emails")
            // Operator-facing fallback: tell them about the missing scope.
            .contains("user:email")
    }

    @Test
    fun `Facebook message names App Review as the operator's path forward`() {
        val exception = OidcEmailMissingException(providerOf("Facebook Login", OidcProviderType.FACEBOOK))

        assertThat(exception.message)
            .contains("Facebook Login")
            .contains("App Review")
            .contains("Development mode")
    }

    @Test
    fun `provider name is verbatim in every variant (no transformation, no truncation)`() {
        val verbatim = "Provider with 'quotes' & spaces & üml@uts"

        for (type in OidcProviderType.entries) {
            val exception = OidcEmailMissingException(providerOf(verbatim, type))

            assertThat(exception.message)
                .`as`("provider name should appear verbatim for type $type")
                .contains(verbatim)
        }
    }
}
