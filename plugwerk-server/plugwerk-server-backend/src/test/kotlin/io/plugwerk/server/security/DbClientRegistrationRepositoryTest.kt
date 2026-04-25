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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.encrypt.TextEncryptor
import java.util.UUID

/**
 * Unit tests for [DbClientRegistrationRepository] that exercise the parts which
 * do not require a live OIDC discovery endpoint:
 *
 *   - empty enabled-providers list → empty registry, all lookups return null
 *   - unreachable issuer is logged-and-skipped, not propagated as an exception
 *     (so one broken provider cannot break authentication for the others)
 *
 * The happy path — building a real ClientRegistration from a working
 * `issuerUri` — needs a live OIDC discovery endpoint and is covered by the
 * manual end-to-end test against the local Keycloak (issue #79). A future
 * Testcontainers-based integration test can pin that interaction.
 */
@ExtendWith(MockitoExtension::class)
class DbClientRegistrationRepositoryTest {

    @Mock lateinit var oidcProviderRepository: OidcProviderRepository

    @Mock lateinit var textEncryptor: TextEncryptor

    @Test
    fun `findByRegistrationId returns null when no provider is enabled`() {
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(emptyList())

        val repo = DbClientRegistrationRepository(oidcProviderRepository, textEncryptor)

        assertThat(repo.findByRegistrationId(UUID.randomUUID().toString())).isNull()
        assertThat(repo.iterator().hasNext()).isFalse()
    }

    @Test
    fun `unreachable issuer is skipped, other providers continue to load (failure isolation)`() {
        // Build a single provider whose issuerUri is a syntactically-valid URL
        // pointing at a port nothing is listening on — Spring's
        // ClientRegistrations.fromIssuerLocation will throw when it tries to
        // fetch /.well-known/openid-configuration. The repository must catch
        // that, log it, and produce an empty registry rather than propagating
        // the exception to the bean container (which would fail server boot).
        val unreachableProvider = OidcProviderEntity(
            id = UUID.fromString("99999999-9999-9999-9999-999999999999"),
            name = "Down Keycloak",
            providerType = OidcProviderType.KEYCLOAK,
            enabled = true,
            clientId = "ignored",
            clientSecretEncrypted = "{cipher}ignored",
            issuerUri = "http://localhost:1/realms/never-listening", // port 1 is reserved/closed
        )
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(listOf(unreachableProvider))

        val repo = DbClientRegistrationRepository(oidcProviderRepository, textEncryptor)

        assertThat(repo.iterator().hasNext()).isFalse()
        assertThat(repo.findByRegistrationId(unreachableProvider.id.toString())).isNull()
    }
}
