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
class ProviderPrincipalAdapterRegistryTest {

    @Test
    fun `routes by provider type`() {
        val oidc = OidcPrincipalAdapter()
        val registry = ProviderPrincipalAdapterRegistry(listOf(oidc))

        assertThat(registry.forProviderType(OidcProviderType.OIDC)).isSameAs(oidc)
        assertThat(registry.forProviderType(OidcProviderType.GOOGLE)).isSameAs(oidc)
    }

    @Test
    fun `fails with actionable message when no adapter is registered for a provider type`() {
        // Build a registry with only the OIDC adapter — Facebook is therefore
        // an unconfigured branch from this registry's point of view, even
        // though a real FacebookPrincipalAdapter exists in the application
        // context. The error must point at #357 so any future addition to
        // OidcProviderType lands on a discoverable signal.
        val registry = ProviderPrincipalAdapterRegistry(listOf(OidcPrincipalAdapter()))

        assertThatThrownBy { registry.forProviderType(OidcProviderType.FACEBOOK) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("FACEBOOK")
            .hasMessageContaining("#357")
    }

    @Test
    fun `rejects duplicate registrations at construction time`() {
        // Two adapter instances claiming the same provider type set is a bean-wiring
        // bug — fail fast at construction. Sealed interface forbids anonymous test
        // implementations, so we provoke the duplicate with two real adapter
        // instances (both claim OIDC + GOOGLE).
        val first = OidcPrincipalAdapter()
        val duplicate = OidcPrincipalAdapter()

        assertThatThrownBy { ProviderPrincipalAdapterRegistry(listOf(first, duplicate)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Multiple ProviderPrincipalAdapter")
            .hasMessageContaining("OIDC")
    }
}
