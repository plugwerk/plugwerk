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
package io.plugwerk.server.repository

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.OidcIdentityEntity
import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Slice tests for [OidcIdentityRepository.findProviderNamesForUsers] (issue
 * #412). The query is a JPQL projection that joins
 * `oidc_identity → oidc_provider` and returns one row per matching user id.
 *
 * The JPQL path `i.oidcProvider.name` resolves through the existing
 * `@ManyToOne(fetch = LAZY)` association — Hibernate flattens it into a
 * SQL JOIN at projection-build time, so no separate per-row query fires.
 * If anyone renames `OidcProviderEntity.name` or the `oidcProvider`
 * association field, these tests fail with a JPQL-parse error rather than
 * silently producing N+1 in production.
 */
class OidcIdentityRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var oidcProviderRepository: OidcProviderRepository

    @Autowired
    lateinit var oidcIdentityRepository: OidcIdentityRepository

    private fun internalUser(username: String) = UserEntity(
        username = username,
        displayName = username,
        email = "$username@example.test",
        source = UserSource.INTERNAL,
        passwordHash = "\$2a\$12\$hash",
    )

    private fun externalUser(displayName: String) = UserEntity(
        username = null,
        displayName = displayName,
        email = "${displayName.lowercase().replace(" ", ".")}@example.test",
        source = UserSource.EXTERNAL,
        passwordHash = null,
    )

    private fun provider(name: String) = OidcProviderEntity(
        name = name,
        providerType = OidcProviderType.OIDC,
        clientId = "client-${name.lowercase().replace(" ", "-")}",
        clientSecretEncrypted = "{cipher}fake",
        issuerUri = "https://${name.lowercase().replace(" ", "-")}.example.test",
        scope = "openid email profile",
        enabled = true,
    )

    @Test
    fun `findProviderNamesForUsers returns one row per EXTERNAL user joined with provider name`() {
        val google = oidcProviderRepository.save(provider("Google"))
        val keycloak = oidcProviderRepository.save(provider("Company Keycloak"))
        val alice = userRepository.save(externalUser("Alice"))
        val bob = userRepository.save(externalUser("Bob"))
        oidcIdentityRepository.save(
            OidcIdentityEntity(oidcProvider = google, subject = "g-alice", user = alice),
        )
        oidcIdentityRepository.save(
            OidcIdentityEntity(oidcProvider = keycloak, subject = "k-bob", user = bob),
        )

        val rows = oidcIdentityRepository.findProviderNamesForUsers(
            listOf(alice.id!!, bob.id!!),
        )

        assertThat(rows).hasSize(2)
        val byUserId = rows.associate { it.userId to it.providerName }
        assertThat(byUserId[alice.id]).isEqualTo("Google")
        assertThat(byUserId[bob.id]).isEqualTo("Company Keycloak")
    }

    @Test
    fun `findProviderNamesForUsers omits user ids that have no oidc_identity row`() {
        // INTERNAL user — no oidc_identity row will ever be created. The
        // controller filters these out before calling the repo, but defending
        // the repo against accidental inclusion keeps the contract explicit:
        // "ids without a join match are simply absent from the result."
        val internal = userRepository.save(internalUser("admin"))

        val rows = oidcIdentityRepository.findProviderNamesForUsers(listOf(internal.id!!))

        assertThat(rows).isEmpty()
    }

    @Test
    fun `findProviderNamesForUsers does not return rows for users outside the input collection`() {
        // The IN-list filter must actually scope the query — a stray row leak
        // would happen if someone refactored the JPQL to drop the WHERE.
        val google = oidcProviderRepository.save(provider("Google"))
        val alice = userRepository.save(externalUser("Alice"))
        val charlie = userRepository.save(externalUser("Charlie"))
        oidcIdentityRepository.save(
            OidcIdentityEntity(oidcProvider = google, subject = "g-alice", user = alice),
        )
        oidcIdentityRepository.save(
            OidcIdentityEntity(oidcProvider = google, subject = "g-charlie", user = charlie),
        )

        val rows = oidcIdentityRepository.findProviderNamesForUsers(listOf(alice.id!!))

        assertThat(rows).hasSize(1)
        assertThat(rows[0].userId).isEqualTo(alice.id)
        assertThat(rows[0].providerName).isEqualTo("Google")
    }
}
