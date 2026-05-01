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
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.DbClientRegistrationRepository
import io.plugwerk.server.security.OidcProviderRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.encrypt.TextEncryptor
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [OidcProviderService.update]. Mocks the repository and
 * registries; the focus is on the patch-application logic, the
 * single-refresh guarantee, and the per-field validation rules.
 */
class OidcProviderServiceTest {

    private lateinit var oidcProviderRepository: OidcProviderRepository
    private lateinit var oidcProviderRegistry: OidcProviderRegistry
    private lateinit var dbClientRegistrationRepository: DbClientRegistrationRepository
    private lateinit var oidcIdentityRepository: OidcIdentityRepository
    private lateinit var userRepository: UserRepository
    private lateinit var textEncryptor: TextEncryptor

    private lateinit var service: OidcProviderService

    private val providerId = UUID.randomUUID()

    private fun newProvider() = OidcProviderEntity(
        id = providerId,
        name = "Original",
        providerType = OidcProviderType.OIDC,
        enabled = false,
        clientId = "original-client",
        clientSecretEncrypted = "ENCRYPTED-OLD",
        issuerUri = "https://old.example.com",
        scope = "openid email profile",
    )

    @BeforeEach
    fun setUp() {
        oidcProviderRepository = mock()
        oidcProviderRegistry = mock()
        dbClientRegistrationRepository = mock()
        oidcIdentityRepository = mock()
        userRepository = mock()
        textEncryptor = mock {
            on { encrypt(any()) } doAnswerReturn { "ENCRYPTED-${it.arguments[0]}" }
        }

        service = OidcProviderService(
            oidcProviderRepository,
            oidcProviderRegistry,
            dbClientRegistrationRepository,
            oidcIdentityRepository,
            userRepository,
            textEncryptor,
        )
    }

    @Test
    fun `update with multiple fields refreshes both registries exactly once`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        service.update(
            providerId,
            OidcProviderPatch(
                name = "New name",
                clientId = "new-client",
                clientSecretPlaintext = "rotated-secret-12345",
                scope = "openid profile",
            ),
        )

        verify(oidcProviderRegistry, times(1)).refresh()
        verify(dbClientRegistrationRepository, times(1)).refresh()
        verify(oidcProviderRepository, times(1)).save(any())
    }

    @Test
    fun `update with blank clientSecret keeps the existing encrypted value`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        service.update(providerId, OidcProviderPatch(clientSecretPlaintext = ""))

        // The original encrypted value must survive — blank is "do not change",
        // not "clear the secret".
        assertThat(provider.clientSecretEncrypted).isEqualTo("ENCRYPTED-OLD")
        verify(textEncryptor, never()).encrypt(any())
    }

    @Test
    fun `update with non-blank clientSecret re-encrypts and stores`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        service.update(providerId, OidcProviderPatch(clientSecretPlaintext = "rotated-secret-12345"))

        assertThat(provider.clientSecretEncrypted).isEqualTo("ENCRYPTED-rotated-secret-12345")
    }

    @Test
    fun `update rejects clientSecret shorter than 8 characters`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertThatThrownBy {
            service.update(providerId, OidcProviderPatch(clientSecretPlaintext = "short"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("at least 8")

        verify(oidcProviderRepository, never()).save(any())
        verify(oidcProviderRegistry, never()).refresh()
        verify(dbClientRegistrationRepository, never()).refresh()
    }

    @Test
    fun `update rejects scope without 'openid' for OIDC providers`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertThatThrownBy {
            service.update(providerId, OidcProviderPatch(scope = "email profile"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("openid")

        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `update accepts scope without 'openid' for non-OIDC providers like GITHUB`() {
        val ghProvider = newProvider().apply {
            providerType = OidcProviderType.GITHUB
            issuerUri = null
        }
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(ghProvider))
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        service.update(providerId, OidcProviderPatch(scope = "user:email"))

        assertThat(ghProvider.scope).isEqualTo("user:email")
    }

    @Test
    fun `update rejects issuerUri with non-http scheme`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertThatThrownBy {
            service.update(providerId, OidcProviderPatch(issuerUri = "ftp://nope.example.com"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("http")
    }

    @Test
    fun `update preserves the provider UUID across edits`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        val result = service.update(providerId, OidcProviderPatch(name = "Renamed"))

        assertThat(result.id).isEqualTo(providerId)
    }

    @Test
    fun `update with no changes still calls refresh once (idempotent path)`() {
        // Intentional: even an "empty" PATCH from the controller flows through
        // the same path. Refreshing once is harmless and matches the
        // single-method contract.
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        service.update(providerId, OidcProviderPatch())

        verify(oidcProviderRegistry, times(1)).refresh()
        verify(dbClientRegistrationRepository, times(1)).refresh()
    }

    @Test
    fun `update rejects name longer than 255 characters`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertThatThrownBy {
            service.update(providerId, OidcProviderPatch(name = "x".repeat(256)))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("255")
    }

    @Test
    fun `update treats blank name as a hard rejection rather than a no-op`() {
        // Empty string is meaningless for a display name; the controller's UI
        // contract is "absent = keep". A non-null but blank value is a bug
        // upstream and should surface as 400 not silently succeed.
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertThatThrownBy {
            service.update(providerId, OidcProviderPatch(name = "   "))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("blank")
    }

    @Test
    fun `discoverEndpoints returns failure outcome with error message for unreachable issuer`() {
        // Port 1 is closed by convention — Spring's ClientRegistrations.fromIssuerLocation
        // will fail with a connection / network error, which the service must surface as
        // success=false rather than rethrowing. The UI uses this branch to render an
        // operator-actionable hint.
        val outcome = service.discoverEndpoints("http://localhost:1/realms/never")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).isNotBlank()
        assertThat(outcome.authorizationUri).isNull()
    }

    @Test
    fun `discoverEndpoints rejects blank issuer URI without attempting network IO`() {
        val outcome = service.discoverEndpoints("   ")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).contains("issuerUri")
    }
}

/**
 * Tiny shim around Mockito's [org.mockito.kotlin.OngoingStubbing.doAnswer]
 * for the common "echo a transformed argument" pattern, used here to make
 * the `textEncryptor` mock return a deterministic ENCRYPTED-prefixed string
 * without writing a separate Answer class for each test.
 */
private infix fun <T> org.mockito.stubbing.OngoingStubbing<T>.doAnswerReturn(
    transform: (org.mockito.invocation.InvocationOnMock) -> T,
): org.mockito.stubbing.OngoingStubbing<T> = thenAnswer { transform(it) }
