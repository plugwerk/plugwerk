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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Branch-coverage tests for [OidcRegistrationIds.of]: the persisted-entity arm
 * (non-null id → `id.toString()`) and the requireNotNull-throws arm (null id).
 */
class OidcRegistrationIdsBranchCoverageTest {

    private fun provider(id: UUID?) = OidcProviderEntity(
        id = id,
        name = "p",
        providerType = OidcProviderType.OIDC,
        enabled = false,
        clientId = "client",
        clientSecretEncrypted = "enc",
        issuerUri = "https://idp.example.com",
        scope = "openid",
    )

    @Test
    fun `of returns the stringified UUID for a persisted entity`() {
        val id = UUID.randomUUID()

        assertThat(OidcRegistrationIds.of(provider(id))).isEqualTo(id.toString())
    }

    @Test
    fun `of throws IllegalArgumentException for an unpersisted entity (null id)`() {
        assertFailsWith<IllegalArgumentException> {
            OidcRegistrationIds.of(provider(null))
        }
    }
}
