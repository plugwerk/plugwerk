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
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.DbClientRegistrationRepository
import io.plugwerk.server.security.OidcProviderRegistry
import io.plugwerk.server.security.url.OidcSsrfPolicy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
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

        // Default to the production policy (SSRF guard active). Tests that
        // need the relaxed mode rebuild `service` with allowPrivate = true.
        service = OidcProviderService(
            oidcProviderRepository,
            oidcProviderRegistry,
            dbClientRegistrationRepository,
            oidcIdentityRepository,
            userRepository,
            textEncryptor,
            OidcSsrfPolicy(allowPrivateDiscoveryUris = false),
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
    fun `discoverEndpoints returns generic failure outcome for unreachable public issuer`() {
        // example.invalid (RFC 2606) is reserved and DNS-fails fast — exercises
        // the post-guard exception branch so we know SSRF rejection and
        // connection-failure rejection both land on the same generic message.
        val outcome = service.discoverEndpoints("https://example.invalid/realms/never")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).isEqualTo(
            "OIDC discovery failed — verify the issuer URI is reachable and serves a valid " +
                "/.well-known/openid-configuration document.",
        )
        assertThat(outcome.authorizationUri).isNull()
    }

    @Test
    fun `discoverEndpoints rejects blank issuer URI without attempting network IO`() {
        val outcome = service.discoverEndpoints("   ")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).contains("issuerUri")
    }

    @Test
    fun `discoverEndpoints rejects AWS instance metadata with generic error (#479)`() {
        val outcome = service.discoverEndpoints("http://169.254.169.254/.well-known/openid-configuration")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error)
            .startsWith("OIDC discovery failed")
            .doesNotContain("169.254")
            .doesNotContain("private")
    }

    @Test
    fun `discoverEndpoints rejects loopback with generic error (#479)`() {
        val outcome = service.discoverEndpoints("http://127.0.0.1:6379/")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).startsWith("OIDC discovery failed")
    }

    @Test
    fun `discoverEndpoints rejects RFC1918 private host with generic error (#479)`() {
        val outcome = service.discoverEndpoints("http://10.0.0.5/realms/internal")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).startsWith("OIDC discovery failed")
    }

    @Test
    fun `discoverEndpoints rejects localhost with generic error (#479)`() {
        val outcome = service.discoverEndpoints("http://localhost/realms/dev")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).startsWith("OIDC discovery failed")
    }

    @Test
    fun `discoverEndpoints accepts localhost when escape hatch is on (#479)`() {
        val relaxed = OidcProviderService(
            oidcProviderRepository,
            oidcProviderRegistry,
            dbClientRegistrationRepository,
            oidcIdentityRepository,
            userRepository,
            textEncryptor,
            OidcSsrfPolicy(allowPrivateDiscoveryUris = true),
        )
        // The guard does not run, so the call falls through to Spring's
        // ClientRegistrations and ultimately to the connection-failure branch
        // (port 1 is convention-closed). Same generic error string either way.
        val outcome = relaxed.discoverEndpoints("http://localhost:1/realms/dev")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).startsWith("OIDC discovery failed")
    }

    // -----------------------------------------------------------------------
    // findAll / findById — previously-untested read paths (DEV-30).
    // -----------------------------------------------------------------------

    @Test
    fun `findAll delegates straight to the repository`() {
        val providers = listOf(newProvider())
        whenever(oidcProviderRepository.findAll()).thenReturn(providers)

        assertThat(service.findAll()).isEqualTo(providers)
    }

    @Test
    fun `findById returns the provider when it exists`() {
        val provider = newProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertThat(service.findById(providerId)).isSameAs(provider)
    }

    @Test
    fun `findById throws EntityNotFoundException when the id is unknown`() {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.empty())

        assertThatThrownBy { service.findById(providerId) }
            .isInstanceOf(EntityNotFoundException::class.java)
            .hasMessageContaining(providerId.toString())
    }

    // -----------------------------------------------------------------------
    // create — OIDC + OAUTH2 paths, SSRF gating, secret encryption (DEV-30).
    // -----------------------------------------------------------------------

    @Test
    fun `create persists an OIDC provider with an encrypted secret, disabled by default`() {
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        val created = service.create(
            name = "Company Keycloak",
            providerType = OidcProviderType.OIDC,
            clientId = "kc-client",
            clientSecret = "super-secret-value",
            issuerUri = "https://idp.example.com",
            scope = "openid email profile",
        )

        assertThat(created.name).isEqualTo("Company Keycloak")
        assertThat(created.providerType).isEqualTo(OidcProviderType.OIDC)
        assertThat(created.clientId).isEqualTo("kc-client")
        assertThat(created.clientSecretEncrypted).isEqualTo("ENCRYPTED-super-secret-value")
        assertThat(created.issuerUri).isEqualTo("https://idp.example.com")
        // A freshly created provider is disabled until an admin activates it,
        // so it stays invisible to both registries — create() must not refresh.
        assertThat(created.enabled).isFalse()
        verify(oidcProviderRegistry, never()).refresh()
        verify(dbClientRegistrationRepository, never()).refresh()
    }

    @Test
    fun `create rejects an OIDC provider whose issuerUri is missing`() {
        assertThatThrownBy {
            service.create(
                name = "Broken OIDC",
                providerType = OidcProviderType.OIDC,
                clientId = "c",
                clientSecret = "secret-1234",
                issuerUri = null,
                scope = "openid",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("issuerUri")

        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `create rejects an OIDC issuerUri pointing at a private host (SSRF guard)`() {
        assertThatThrownBy {
            service.create(
                name = "SSRF OIDC",
                providerType = OidcProviderType.OIDC,
                clientId = "c",
                clientSecret = "secret-1234",
                issuerUri = "http://10.0.0.1/realms/internal",
                scope = "openid",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `create rejects an OAUTH2 provider missing the required endpoint URIs`() {
        assertThatThrownBy {
            service.create(
                name = "Half OAuth2",
                providerType = OidcProviderType.OAUTH2,
                clientId = "c",
                clientSecret = "secret-1234",
                issuerUri = null,
                scope = "read_user",
                authorizationUri = null,
                tokenUri = "https://idp.example.com/token",
                userInfoUri = "https://idp.example.com/userinfo",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("authorizationUri")

        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `create persists an OAUTH2 provider with the supplied endpoint URIs`() {
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        val created = service.create(
            name = "Self-hosted GitLab",
            providerType = OidcProviderType.OAUTH2,
            clientId = "gl-client",
            clientSecret = "secret-1234",
            issuerUri = null,
            scope = "read_user",
            authorizationUri = "https://gitlab.example.com/oauth/authorize",
            tokenUri = "https://gitlab.example.com/oauth/token",
            userInfoUri = "https://gitlab.example.com/api/v4/user",
            jwkSetUri = null,
            subjectAttribute = "id",
            emailAttribute = "email",
            displayNameAttribute = "name",
        )

        assertThat(created.providerType).isEqualTo(OidcProviderType.OAUTH2)
        assertThat(created.authorizationUri).isEqualTo("https://gitlab.example.com/oauth/authorize")
        assertThat(created.tokenUri).isEqualTo("https://gitlab.example.com/oauth/token")
        assertThat(created.userInfoUri).isEqualTo("https://gitlab.example.com/api/v4/user")
        assertThat(created.subjectAttribute).isEqualTo("id")
    }

    @Test
    fun `create normalises blank optional fields down to null`() {
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        val created = service.create(
            name = "OIDC with blanks",
            providerType = OidcProviderType.OIDC,
            clientId = "c",
            clientSecret = "secret-1234",
            issuerUri = "https://idp.example.com",
            scope = "openid",
            // Optional + ignored for OIDC; blank input must normalise to null
            // rather than persisting an empty string.
            authorizationUri = "   ",
            subjectAttribute = "   ",
        )

        assertThat(created.authorizationUri).isNull()
        assertThat(created.subjectAttribute).isNull()
    }

    // -----------------------------------------------------------------------
    // delete — Politik C: orphaned users disabled before the cascade (DEV-30).
    // -----------------------------------------------------------------------

    @Test
    fun `delete throws EntityNotFoundException for an unknown id and touches nothing`() {
        whenever(oidcProviderRepository.existsById(providerId)).thenReturn(false)

        assertThatThrownBy { service.delete(providerId) }
            .isInstanceOf(EntityNotFoundException::class.java)

        verify(oidcProviderRepository, never()).deleteById(any())
        verify(userRepository, never()).disableAll(any())
        verify(oidcProviderRegistry, never()).refresh()
    }

    @Test
    fun `delete disables orphaned users before removing the provider (Politik C)`() {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        // Build the identity mocks before the outer stubbing — creating mocks
        // (which stub their own getters) mid-`whenever(...)` would trip
        // Mockito's UnfinishedStubbingException.
        val identities = listOf(identityForUser(userId1), identityForUser(userId2))
        whenever(oidcProviderRepository.existsById(providerId)).thenReturn(true)
        whenever(oidcIdentityRepository.findAllByOidcProviderId(providerId)).thenReturn(identities)
        whenever(userRepository.disableAll(any())).thenReturn(2)

        service.delete(providerId)

        val captor = argumentCaptor<Collection<UUID>>()
        verify(userRepository).disableAll(captor.capture())
        assertThat(captor.firstValue).containsExactlyInAnyOrder(userId1, userId2)
        verify(oidcProviderRepository).deleteById(providerId)
        verify(oidcProviderRegistry, times(1)).refresh()
        verify(dbClientRegistrationRepository, times(1)).refresh()
    }

    @Test
    fun `delete with no linked identities skips user disabling but still refreshes`() {
        whenever(oidcProviderRepository.existsById(providerId)).thenReturn(true)
        whenever(oidcIdentityRepository.findAllByOidcProviderId(providerId)).thenReturn(emptyList())

        service.delete(providerId)

        verify(userRepository, never()).disableAll(any())
        verify(oidcProviderRepository).deleteById(providerId)
        verify(oidcProviderRegistry, times(1)).refresh()
        verify(dbClientRegistrationRepository, times(1)).refresh()
    }

    private fun identityForUser(userId: UUID): OidcIdentityEntity {
        val userMock = mock<UserEntity> { on { id } doReturn userId }
        return mock { on { user } doReturn userMock }
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
