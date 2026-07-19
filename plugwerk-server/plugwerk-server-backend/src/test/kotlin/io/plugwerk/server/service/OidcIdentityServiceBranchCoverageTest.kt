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

import io.plugwerk.server.domain.OidcIdentityEntity
import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.ResolvedPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Branch-coverage tests for [OidcIdentityService.upsertOnLogin] that complement
 * the existing `OidcIdentityServiceTest` (which pins the #367 last-login bump on
 * the happy paths against H2). This class drives the uncovered decision arms
 * with pure Mockito stubs:
 *  - `requireNotNull(provider.id)` throwing for an unpersisted provider,
 *  - the new-identity `email == null` / `email isBlank` arms that raise
 *    [OidcEmailMissingException],
 *  - the `displayName` present vs null-fallback-to-subject arms on creation.
 */
@ExtendWith(MockitoExtension::class)
class OidcIdentityServiceBranchCoverageTest {

    @Mock lateinit var oidcIdentityRepository: OidcIdentityRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var userService: UserService

    @InjectMocks
    lateinit var service: OidcIdentityService

    private fun persistedProvider(type: OidcProviderType = OidcProviderType.OIDC) = OidcProviderEntity(
        id = UUID.randomUUID(),
        name = "p",
        providerType = type,
        enabled = true,
        clientId = "client",
        clientSecretEncrypted = "enc",
        issuerUri = "https://idp.example.com/",
    )

    private fun principal(
        subject: String = "sub-1",
        email: String? = "user@example.com",
        displayName: String? = "User One",
    ) = ResolvedPrincipal(
        subject = subject,
        email = email,
        displayName = displayName,
        upstreamIdToken = null,
    )

    @Test
    fun `upsertOnLogin requires a persisted provider`() {
        // id == null -> requireNotNull throws before any repository access.
        val unpersisted = OidcProviderEntity(
            name = "p",
            providerType = OidcProviderType.OIDC,
            enabled = true,
            clientId = "client",
            clientSecretEncrypted = "enc",
            issuerUri = "https://idp.example.com/",
        )

        assertFailsWith<IllegalArgumentException> {
            service.upsertOnLogin(unpersisted, principal())
        }
    }

    @Test
    fun `new identity with a null email is rejected with OidcEmailMissingException`() {
        val provider = persistedProvider()
        whenever(oidcIdentityRepository.findByOidcProviderIdAndSubject(provider.id!!, "sub-1"))
            .thenReturn(Optional.empty())

        assertFailsWith<OidcEmailMissingException> {
            service.upsertOnLogin(provider, principal(email = null))
        }
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `new identity with a blank email is rejected with OidcEmailMissingException`() {
        val provider = persistedProvider()
        whenever(oidcIdentityRepository.findByOidcProviderIdAndSubject(provider.id!!, "sub-1"))
            .thenReturn(Optional.empty())

        assertFailsWith<OidcEmailMissingException> {
            service.upsertOnLogin(provider, principal(email = "   "))
        }
    }

    @Test
    fun `new identity uses the provided display name`() {
        val provider = persistedProvider()
        whenever(oidcIdentityRepository.findByOidcProviderIdAndSubject(provider.id!!, "sub-1"))
            .thenReturn(Optional.empty())
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] as UserEntity }

        val user = service.upsertOnLogin(provider, principal(displayName = "Alice"))

        assertThat(user.displayName).isEqualTo("Alice")
        assertThat(user.email).isEqualTo("user@example.com")
        verify(oidcIdentityRepository).save(any<OidcIdentityEntity>())
    }

    @Test
    fun `new identity falls back to the subject when no display name is present`() {
        val provider = persistedProvider()
        whenever(oidcIdentityRepository.findByOidcProviderIdAndSubject(provider.id!!, "sub-1"))
            .thenReturn(Optional.empty())
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] as UserEntity }

        service.upsertOnLogin(provider, principal(displayName = null))

        val captor = argumentCaptor<UserEntity>()
        verify(userRepository).save(captor.capture())
        assertThat(captor.firstValue.displayName).isEqualTo("sub-1")
    }
}
