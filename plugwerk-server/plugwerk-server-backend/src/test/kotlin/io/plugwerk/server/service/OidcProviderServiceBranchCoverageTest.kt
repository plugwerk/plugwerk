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
import io.plugwerk.server.security.url.OidcSsrfPolicy
import org.assertj.core.api.Assertions.assertThat
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
import kotlin.test.assertFailsWith

/**
 * Branch-coverage focused unit tests for [OidcProviderService]. Complements
 * [OidcProviderServiceTest] by exercising the per-provider-type create arms
 * (GOOGLE / GITHUB / FACEBOOK skip the OAUTH2 + required-issuer gates), the
 * individual OAUTH2 endpoint rejections, and the remaining `update` validation
 * arms (clientId length/blank, issuerUri blank, jwkSetUri set/clear-reject,
 * applyOAuth2GenericUriPatch wrong-type / blank, applyAttributeNamePatch blank,
 * enabled toggle, scope blank). Pure unit tests — no Spring context.
 */
class OidcProviderServiceBranchCoverageTest {

    private lateinit var oidcProviderRepository: OidcProviderRepository
    private lateinit var oidcProviderRegistry: OidcProviderRegistry
    private lateinit var dbClientRegistrationRepository: DbClientRegistrationRepository
    private lateinit var oidcIdentityRepository: OidcIdentityRepository
    private lateinit var userRepository: UserRepository
    private lateinit var textEncryptor: TextEncryptor

    private lateinit var service: OidcProviderService

    private val providerId: UUID = UUID.randomUUID()

    private fun oidcProvider() = OidcProviderEntity(
        id = providerId,
        name = "Original",
        providerType = OidcProviderType.OIDC,
        enabled = false,
        clientId = "original-client",
        clientSecretEncrypted = "ENCRYPTED-OLD",
        issuerUri = "https://old.example.com",
        scope = "openid email profile",
    )

    private fun oauth2Provider() = OidcProviderEntity(
        id = providerId,
        name = "Generic OAuth2",
        providerType = OidcProviderType.OAUTH2,
        enabled = false,
        clientId = "client",
        clientSecretEncrypted = "ENCRYPTED-OLD",
        issuerUri = null,
        scope = "read_user",
        authorizationUri = "https://idp.example.com/authorize",
        tokenUri = "https://idp.example.com/token",
        userInfoUri = "https://idp.example.com/userinfo",
    )

    @BeforeEach
    fun setUp() {
        oidcProviderRepository = mock()
        oidcProviderRegistry = mock()
        dbClientRegistrationRepository = mock()
        oidcIdentityRepository = mock()
        userRepository = mock()
        textEncryptor = mock {
            on { encrypt(any()) } doAnswerReturnLocal { "ENCRYPTED-${it.arguments[0]}" }
        }
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

    private fun stubFindAndSave(provider: OidcProviderEntity) {
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }
    }

    // -----------------------------------------------------------------------
    // create — vendor types skip the OAUTH2 + required-issuer validation arms.
    // -----------------------------------------------------------------------

    @Test
    fun `create GITHUB provider skips OAUTH2 and issuer validation (issuerUri null is fine)`() {
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        val created = service.create(
            name = "GitHub",
            providerType = OidcProviderType.GITHUB,
            clientId = "gh-client",
            clientSecret = "secret-1234",
            issuerUri = null,
            scope = "user:email",
        )

        assertThat(created.providerType).isEqualTo(OidcProviderType.GITHUB)
        assertThat(created.issuerUri).isNull()
        verify(oidcProviderRepository).save(any())
    }

    @Test
    fun `create GOOGLE provider with null issuerUri is accepted (issuer not required)`() {
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        val created = service.create(
            name = "Google",
            providerType = OidcProviderType.GOOGLE,
            clientId = "g-client",
            clientSecret = "secret-1234",
            issuerUri = null,
            scope = "openid email",
        )

        assertThat(created.providerType).isEqualTo(OidcProviderType.GOOGLE)
    }

    @Test
    fun `create FACEBOOK provider with null issuerUri is accepted`() {
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        val created = service.create(
            name = "Facebook",
            providerType = OidcProviderType.FACEBOOK,
            clientId = "fb-client",
            clientSecret = "secret-1234",
            issuerUri = null,
            scope = "public_profile email",
        )

        assertThat(created.providerType).isEqualTo(OidcProviderType.FACEBOOK)
    }

    @Test
    fun `create OAUTH2 rejects a private tokenUri (SSRF guard on the second endpoint)`() {
        assertFailsWith<IllegalArgumentException> {
            service.create(
                name = "SSRF token",
                providerType = OidcProviderType.OAUTH2,
                clientId = "c",
                clientSecret = "secret-1234",
                issuerUri = null,
                scope = "read_user",
                authorizationUri = "https://idp.example.com/authorize",
                tokenUri = "http://10.0.0.1/token",
                userInfoUri = "https://idp.example.com/userinfo",
            )
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `create OAUTH2 rejects a private userInfoUri`() {
        assertFailsWith<IllegalArgumentException> {
            service.create(
                name = "SSRF userinfo",
                providerType = OidcProviderType.OAUTH2,
                clientId = "c",
                clientSecret = "secret-1234",
                issuerUri = null,
                scope = "read_user",
                authorizationUri = "https://idp.example.com/authorize",
                tokenUri = "https://idp.example.com/token",
                userInfoUri = "http://127.0.0.1/userinfo",
            )
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `create OAUTH2 rejects a private jwkSetUri even though it is optional`() {
        // required=false but, when supplied, the SSRF host gate still applies.
        assertFailsWith<IllegalArgumentException> {
            service.create(
                name = "SSRF jwks",
                providerType = OidcProviderType.OAUTH2,
                clientId = "c",
                clientSecret = "secret-1234",
                issuerUri = null,
                scope = "read_user",
                authorizationUri = "https://idp.example.com/authorize",
                tokenUri = "https://idp.example.com/token",
                userInfoUri = "https://idp.example.com/userinfo",
                jwkSetUri = "http://169.254.169.254/jwks",
            )
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `create OAUTH2 accepts a public jwkSetUri and trims it onto the entity`() {
        whenever(oidcProviderRepository.save(any<OidcProviderEntity>())).thenAnswer {
            it.arguments[0] as OidcProviderEntity
        }

        val created = service.create(
            name = "With jwks",
            providerType = OidcProviderType.OAUTH2,
            clientId = "c",
            clientSecret = "secret-1234",
            issuerUri = null,
            scope = "read_user",
            authorizationUri = "https://idp.example.com/authorize",
            tokenUri = "https://idp.example.com/token",
            userInfoUri = "https://idp.example.com/userinfo",
            jwkSetUri = "  https://idp.example.com/jwks  ",
        )

        assertThat(created.jwkSetUri).isEqualTo("https://idp.example.com/jwks")
    }

    // -----------------------------------------------------------------------
    // update — clientId arms.
    // -----------------------------------------------------------------------

    @Test
    fun `update rejects blank clientId`() {
        val provider = oidcProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertFailsWith<IllegalArgumentException> {
            service.update(providerId, OidcProviderPatch(clientId = "   "))
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `update rejects clientId longer than 255 characters`() {
        val provider = oidcProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertFailsWith<IllegalArgumentException> {
            service.update(providerId, OidcProviderPatch(clientId = "x".repeat(256)))
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `update trims and applies a valid clientId`() {
        val provider = oidcProvider()
        stubFindAndSave(provider)

        service.update(providerId, OidcProviderPatch(clientId = "  new-client  "))

        assertThat(provider.clientId).isEqualTo("new-client")
    }

    // -----------------------------------------------------------------------
    // update — enabled toggle + scope blank arm.
    // -----------------------------------------------------------------------

    @Test
    fun `update applies the enabled flag`() {
        val provider = oidcProvider()
        stubFindAndSave(provider)

        service.update(providerId, OidcProviderPatch(enabled = true))

        assertThat(provider.enabled).isTrue()
    }

    @Test
    fun `update rejects a blank scope`() {
        val provider = oidcProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertFailsWith<IllegalArgumentException> {
            service.update(providerId, OidcProviderPatch(scope = "   "))
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    // -----------------------------------------------------------------------
    // update — issuerUri arms (blank reject, valid set).
    // -----------------------------------------------------------------------

    @Test
    fun `update rejects a blank issuerUri`() {
        val provider = oidcProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertFailsWith<IllegalArgumentException> {
            service.update(providerId, OidcProviderPatch(issuerUri = "   "))
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `update accepts and trims a valid public issuerUri`() {
        val provider = oidcProvider()
        stubFindAndSave(provider)

        service.update(providerId, OidcProviderPatch(issuerUri = "  https://new.example.com  "))

        assertThat(provider.issuerUri).isEqualTo("https://new.example.com")
    }

    // -----------------------------------------------------------------------
    // update — jwkSetUri arms (blank reject, valid set on OAUTH2).
    // -----------------------------------------------------------------------

    @Test
    fun `update rejects a blank jwkSetUri`() {
        val provider = oauth2Provider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertFailsWith<IllegalArgumentException> {
            service.update(providerId, OidcProviderPatch(jwkSetUri = "   "))
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `update applies a valid public jwkSetUri`() {
        val provider = oauth2Provider()
        stubFindAndSave(provider)

        service.update(providerId, OidcProviderPatch(jwkSetUri = "https://idp.example.com/jwks"))

        assertThat(provider.jwkSetUri).isEqualTo("https://idp.example.com/jwks")
    }

    // -----------------------------------------------------------------------
    // update — applyOAuth2GenericUriPatch arms.
    // -----------------------------------------------------------------------

    @Test
    fun `update rejects an OAUTH2 endpoint patch on a non-OAUTH2 provider`() {
        // The OIDC provider is the wrong type for authorizationUri.
        val provider = oidcProvider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertFailsWith<IllegalArgumentException> {
            service.update(providerId, OidcProviderPatch(authorizationUri = "https://idp.example.com/authorize"))
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `update rejects clearing an OAUTH2 endpoint URI to blank`() {
        val provider = oauth2Provider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertFailsWith<IllegalArgumentException> {
            service.update(providerId, OidcProviderPatch(tokenUri = "   "))
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `update applies all three OAUTH2 endpoint URIs on an OAUTH2 provider`() {
        val provider = oauth2Provider()
        stubFindAndSave(provider)

        service.update(
            providerId,
            OidcProviderPatch(
                authorizationUri = "https://new.example.com/authorize",
                tokenUri = "https://new.example.com/token",
                userInfoUri = "https://new.example.com/userinfo",
            ),
        )

        assertThat(provider.authorizationUri).isEqualTo("https://new.example.com/authorize")
        assertThat(provider.tokenUri).isEqualTo("https://new.example.com/token")
        assertThat(provider.userInfoUri).isEqualTo("https://new.example.com/userinfo")
        verify(oidcProviderRegistry, times(1)).refresh()
    }

    // -----------------------------------------------------------------------
    // update — applyAttributeNamePatch arms.
    // -----------------------------------------------------------------------

    @Test
    fun `update rejects a blank attribute name`() {
        val provider = oauth2Provider()
        whenever(oidcProviderRepository.findById(providerId)).thenReturn(Optional.of(provider))

        assertFailsWith<IllegalArgumentException> {
            service.update(providerId, OidcProviderPatch(subjectAttribute = "   "))
        }
        verify(oidcProviderRepository, never()).save(any())
    }

    @Test
    fun `update applies trimmed attribute names`() {
        val provider = oauth2Provider()
        stubFindAndSave(provider)

        service.update(
            providerId,
            OidcProviderPatch(
                subjectAttribute = "  sub  ",
                emailAttribute = "  mail  ",
                displayNameAttribute = "  name  ",
            ),
        )

        assertThat(provider.subjectAttribute).isEqualTo("sub")
        assertThat(provider.emailAttribute).isEqualTo("mail")
        assertThat(provider.displayNameAttribute).isEqualTo("name")
    }

    // -----------------------------------------------------------------------
    // discoverEndpoints — non-blank-after-trim happy entry through the guard.
    // -----------------------------------------------------------------------

    @Test
    fun `discoverEndpoints trims surrounding whitespace before the guard runs`() {
        // Surrounding whitespace must not short-circuit to the blank branch;
        // a private host after trim still routes to the generic failure arm.
        val outcome = service.discoverEndpoints("   http://10.0.0.9/realms/x   ")

        assertThat(outcome.success).isFalse()
        assertThat(outcome.error).startsWith("OIDC discovery failed")
    }
}

/**
 * Local shim mirroring the helper in [OidcProviderServiceTest] (private there,
 * so it cannot be reused across files). Echoes a transformed argument from a
 * Mockito stub without a bespoke Answer class.
 */
private infix fun <T> org.mockito.stubbing.OngoingStubbing<T>.doAnswerReturnLocal(
    transform: (org.mockito.invocation.InvocationOnMock) -> T,
): org.mockito.stubbing.OngoingStubbing<T> = thenAnswer { transform(it) }
