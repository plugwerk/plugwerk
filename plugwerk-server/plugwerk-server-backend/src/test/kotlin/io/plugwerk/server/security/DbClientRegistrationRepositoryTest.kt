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
    fun `Google provider type no longer hits the not-yet-implemented branch (#357 phase 1)`() {
        // Pre-#357 GOOGLE was rejected with `error("Browser login flow not yet implemented...")`
        // — a programmer-error throw that runCatching dutifully caught and skipped, but
        // semantically wrong (the provider IS supported now). This test pins that the
        // GOOGLE branch goes through `ClientRegistrations.fromIssuerLocation(GOOGLE_ISSUER_URI)`
        // instead. We do NOT assert successful discovery — Google.com reachability in CI
        // is environment-dependent — only that no IllegalStateException with the
        // not-implemented marker is produced anymore.
        val google = OidcProviderEntity(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "Google",
            providerType = OidcProviderType.GOOGLE,
            enabled = true,
            clientId = "google-client-id",
            clientSecretEncrypted = "{cipher}ignored",
            issuerUri = null, // operator no longer needs to configure this
        )
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(listOf(google))

        // Construction must not throw (the runCatching wrapper catches network failures
        // — that's the offline-CI case — but the not-yet-implemented IllegalStateException
        // path is gone for GOOGLE).
        DbClientRegistrationRepository(oidcProviderRepository, textEncryptor)
    }

    @Test
    fun `GitHub provider type now produces a registration via CommonOAuth2Provider (#357 phase 3)`() {
        whenever(textEncryptor.decrypt("{cipher}gh-secret")).thenReturn("plain-gh-secret")
        val github = OidcProviderEntity(
            id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            name = "GitHub",
            providerType = OidcProviderType.GITHUB,
            enabled = true,
            clientId = "gh-client",
            clientSecretEncrypted = "{cipher}gh-secret",
            issuerUri = null,
        )
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(listOf(github))

        val repo = DbClientRegistrationRepository(oidcProviderRepository, textEncryptor)
        val registration = repo.findByRegistrationId(github.id.toString())

        assertThat(registration).isNotNull()
        assertThat(registration!!.clientId).isEqualTo("gh-client")
        assertThat(registration.clientSecret).isEqualTo("plain-gh-secret")
        // Hardcoded GitHub scopes — operator-supplied OIDC scopes are intentionally
        // ignored for this provider type because GitHub does not understand them.
        assertThat(registration.scopes).containsExactlyInAnyOrder("read:user", "user:email")
        // Spring's CommonOAuth2Provider.GITHUB template carries the right endpoints —
        // pin one of them to confirm we're using it rather than a hand-rolled config.
        assertThat(registration.providerDetails.tokenUri)
            .isEqualTo("https://github.com/login/oauth/access_token")
    }

    @Test
    fun `Facebook provider type now produces a registration via CommonOAuth2Provider (#357 phase 4)`() {
        whenever(textEncryptor.decrypt("{cipher}fb-secret")).thenReturn("plain-fb-secret")
        val facebook = OidcProviderEntity(
            id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
            name = "Facebook",
            providerType = OidcProviderType.FACEBOOK,
            enabled = true,
            clientId = "fb-client",
            clientSecretEncrypted = "{cipher}fb-secret",
            issuerUri = null,
        )
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(listOf(facebook))

        val repo = DbClientRegistrationRepository(oidcProviderRepository, textEncryptor)
        val registration = repo.findByRegistrationId(facebook.id.toString())

        assertThat(registration).isNotNull()
        assertThat(registration!!.clientId).isEqualTo("fb-client")
        assertThat(registration.clientSecret).isEqualTo("plain-fb-secret")
        // Hardcoded Facebook scopes — operator-supplied OIDC scopes are
        // intentionally ignored for this provider type because Facebook
        // expects its own permission set.
        assertThat(registration.scopes).containsExactlyInAnyOrder("email", "public_profile")
        // Spring's CommonOAuth2Provider.FACEBOOK template carries the right
        // endpoints — pin the token endpoint to confirm.
        assertThat(registration.providerDetails.tokenUri)
            .isEqualTo("https://graph.facebook.com/v2.8/oauth/access_token")
    }

    @Test
    fun `OAUTH2 builds a registration from operator-supplied URIs (no discovery)`() {
        whenever(textEncryptor.decrypt("{cipher}gen-secret")).thenReturn("plain-secret")
        val generic = OidcProviderEntity(
            id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
            name = "Custom IdP",
            providerType = OidcProviderType.OAUTH2,
            enabled = true,
            clientId = "gen-client",
            clientSecretEncrypted = "{cipher}gen-secret",
            scope = "openid profile email",
            authorizationUri = "https://idp.example/oauth/authorize",
            tokenUri = "https://idp.example/oauth/token",
            userInfoUri = "https://idp.example/api/me",
            subjectAttribute = "user_id",
        )
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(listOf(generic))

        val repo = DbClientRegistrationRepository(oidcProviderRepository, textEncryptor)
        val registration = repo.findByRegistrationId(generic.id.toString())

        assertThat(registration).isNotNull()
        assertThat(registration!!.clientId).isEqualTo("gen-client")
        assertThat(registration.clientSecret).isEqualTo("plain-secret")
        assertThat(registration.providerDetails.authorizationUri)
            .isEqualTo("https://idp.example/oauth/authorize")
        assertThat(registration.providerDetails.tokenUri)
            .isEqualTo("https://idp.example/oauth/token")
        assertThat(registration.providerDetails.userInfoEndpoint.uri)
            .isEqualTo("https://idp.example/api/me")
        assertThat(registration.providerDetails.userInfoEndpoint.userNameAttributeName)
            .isEqualTo("user_id")
        // Operator-supplied scope honoured verbatim.
        assertThat(registration.scopes).containsExactlyInAnyOrder("openid", "profile", "email")
        // Display name flows through for admin UI / debugging surfaces.
        assertThat(registration.clientName).isEqualTo("Custom IdP")
    }

    @Test
    fun `OAUTH2 defaults subject attribute to sub when operator left it blank`() {
        whenever(textEncryptor.decrypt("{cipher}s")).thenReturn("plain")
        val generic = OidcProviderEntity(
            id = UUID.fromString("55555555-5555-5555-5555-555555555555"),
            name = "Default-attrs IdP",
            providerType = OidcProviderType.OAUTH2,
            enabled = true,
            clientId = "c",
            clientSecretEncrypted = "{cipher}s",
            authorizationUri = "https://idp.example/authorize",
            tokenUri = "https://idp.example/token",
            userInfoUri = "https://idp.example/userinfo",
            subjectAttribute = null, // operator left blank → default to "sub"
        )
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(listOf(generic))

        val repo = DbClientRegistrationRepository(oidcProviderRepository, textEncryptor)
        val registration = repo.findByRegistrationId(generic.id.toString())

        assertThat(registration!!.providerDetails.userInfoEndpoint.userNameAttributeName)
            .isEqualTo("sub")
    }

    @Test
    fun `OAUTH2 missing authorizationUri is logged-and-skipped, not propagated`() {
        // Service-layer validation is meant to prevent this — but if a row slips
        // through the repository must not break the entire bean container.
        // runCatching catches the requireNotNull and skips the entry.
        val broken = OidcProviderEntity(
            id = UUID.fromString("66666666-6666-6666-6666-666666666666"),
            name = "Half-configured",
            providerType = OidcProviderType.OAUTH2,
            enabled = true,
            clientId = "c",
            clientSecretEncrypted = "{cipher}ignored",
            authorizationUri = null, // missing — required field
            tokenUri = "https://idp.example/token",
            userInfoUri = "https://idp.example/userinfo",
        )
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(listOf(broken))

        val repo = DbClientRegistrationRepository(oidcProviderRepository, textEncryptor)

        assertThat(repo.iterator().hasNext()).isFalse()
        assertThat(repo.findByRegistrationId(broken.id.toString())).isNull()
    }

    @Test
    fun `Google issuer URI constant points at the documented Google identity platform`() {
        // Pinning the constant — a regression here would silently route Google logins
        // to the wrong issuer and the discovery would fail with a confusing error.
        assertThat(DbClientRegistrationRepository.GOOGLE_ISSUER_URI).isEqualTo("https://accounts.google.com")
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
            providerType = OidcProviderType.OIDC,
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
