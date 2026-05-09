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
package io.plugwerk.server.e2e.auth

import io.plugwerk.server.SharedPostgresContainer
import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.repository.RefreshTokenRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.DbClientRegistrationRepository
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/**
 * Full-roundtrip integration test for the OIDC web-UI login flow (issue #315).
 *
 * Exercises the entire token-exchange dance against an in-process mock OIDC
 * provider (`navikt/mock-oauth2-server`):
 *
 * 1. SPA-equivalent: GET /oauth2/authorization/{providerId}
 *    Spring's OAuth2 client filter mints PKCE + state, redirects to the
 *    mock provider's authorize endpoint.
 * 2. Mock provider: receives the GET, redirects back to
 *    /login/oauth2/code/{providerId}?code=…&state=…
 * 3. SPA-equivalent: GET /login/oauth2/code/{providerId}?…
 *    Spring exchanges the code (POST to mock token endpoint), validates the
 *    ID token (signature against mock JWKS, issuer, audience), invokes
 *    OidcLoginSuccessHandler which delegates to OidcIdentityService.upsertOnLogin.
 *
 * Uses an actual embedded Tomcat (webEnvironment = RANDOM_PORT) plus the JDK
 * HttpClient with a CookieManager — Spring's HttpSession (which holds the
 * OAuth2AuthorizationRequest with the PKCE verifier and `state`) survives via
 * a `JSESSIONID` cookie that the manager replays on the callback request.
 * MockMvc cannot do this — its session is per-call, not cookie-mediated.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OidcWebLoginIT {

    companion object {
        @JvmStatic
        private val mockOAuth2Server: MockOAuth2Server = MockOAuth2Server()

        const val MOCK_ISSUER_ID = "plugwerk-test"
        const val MOCK_CLIENT_ID = "plugwerk-test-client"
        const val MOCK_CLIENT_SECRET = "test-secret"

        @BeforeAll
        @JvmStatic
        fun startMockOAuth2Server() {
            mockOAuth2Server.start(0)
        }

        @AfterAll
        @JvmStatic
        fun stopMockOAuth2Server() {
            mockOAuth2Server.shutdown()
        }

        @DynamicPropertySource
        @JvmStatic
        fun overrideDataSource(registry: DynamicPropertyRegistry) {
            val postgres = SharedPostgresContainer.instance
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var oidcProviderRepository: OidcProviderRepository

    @Autowired private lateinit var oidcIdentityRepository: OidcIdentityRepository

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired private lateinit var dbClientRegistrationRepository: DbClientRegistrationRepository

    @Autowired private lateinit var textEncryptor: TextEncryptor

    private lateinit var providerId: UUID

    @BeforeEach
    fun seedProvider() {
        // Wipe just the OIDC-related rows — leave the bootstrap admin user alone.
        // refresh_token cascades from plugwerk_user; oidc_identity cascades from
        // both plugwerk_user and oidc_provider, so a single delete on each end
        // table cleans everything we touch.
        refreshTokenRepository.deleteAll()
        oidcIdentityRepository.deleteAll()
        userRepository.findAll()
            .filter { it.enabled && it.source == UserSource.EXTERNAL }
            .forEach { userRepository.delete(it) }
        oidcProviderRepository.deleteAll()

        val provider = oidcProviderRepository.saveAndFlush(
            OidcProviderEntity(
                name = "Mock IdP",
                providerType = OidcProviderType.OIDC,
                enabled = true,
                clientId = MOCK_CLIENT_ID,
                clientSecretEncrypted = textEncryptor.encrypt(MOCK_CLIENT_SECRET),
                issuerUri = mockOAuth2Server.issuerUrl(MOCK_ISSUER_ID).toString(),
                scope = "openid email profile",
            ),
        )
        providerId = requireNotNull(provider.id)
        // Force a registry refresh so the new provider is in the in-memory map
        // before the test issues its first /oauth2/authorization/{id} request.
        dbClientRegistrationRepository.refresh()
    }

    @Test
    fun `first-time OIDC login provisions plugwerk_user + oidc_identity, sets refresh cookie, redirects to root`() {
        val sub = "alice-${UUID.randomUUID()}"
        val email = "alice-${UUID.randomUUID()}@example.test"
        enqueueIdToken(sub = sub, email = email, name = "Alice Anderson")

        val callbackResponse = performAuthorizeRoundtrip()

        // OIDC callback redirects to "/" (SPA root); OidcLoginSuccessHandler
        // sets that explicitly so the frontend's hydrate() picks up the just-set
        // refresh cookie via /api/v1/auth/refresh. Tomcat normalises the
        // Location header to an absolute URI on the listening origin, so we
        // compare the path component, not the full string.
        assertThat(callbackResponse.statusCode()).isEqualTo(302)
        val redirectPath = URI.create(callbackResponse.headers().firstValue("Location").orElse("")).path
        assertThat(redirectPath).isEqualTo("/")
        val setCookieHeaders = callbackResponse.headers().allValues("Set-Cookie")
        assertThat(setCookieHeaders).anyMatch { it.startsWith("plugwerk_refresh=") }
        assertThat(setCookieHeaders.first { it.startsWith("plugwerk_refresh=") }).contains("HttpOnly")

        val user = userBySubject(sub)
        assertThat(user.source).isEqualTo(UserSource.EXTERNAL)
        assertThat(user.username).isNull()
        assertThat(user.passwordHash).isNull()
        assertThat(user.email).isEqualTo(email)
        assertThat(user.displayName).isEqualTo("Alice Anderson")
        assertThat(user.enabled).isTrue()
        assertThat(user.passwordChangeRequired).isFalse()
        assertThat(user.isSuperadmin).isFalse()

        // refresh_token row was issued for the new user with the upstream id_token
        // captured for later RP-Initiated Logout (#352).
        val refreshTokens = refreshTokenRepository.findAll().filter { it.userId == user.id }
        assertThat(refreshTokens).hasSize(1)
        assertThat(refreshTokens[0].upstreamIdToken).isNotBlank()
    }

    @Test
    fun `subsequent OIDC login with same (provider, sub) updates last_login_at, no new user created`() {
        val sub = "alice-${UUID.randomUUID()}"
        val email = "alice-${UUID.randomUUID()}@example.test"

        enqueueIdToken(sub = sub, email = email, name = "Alice Anderson")
        performAuthorizeRoundtrip()

        val firstUser = userBySubject(sub)
        val firstUserId = requireNotNull(firstUser.id)
        // last_login_at moved from oidc_identity to plugwerk_user (issue #367 cleanup,
        // migration 0021). The user-level column is now the canonical signal.
        val firstLoginAt = requireNotNull(firstUser.lastLoginAt)

        Thread.sleep(50)
        enqueueIdToken(sub = sub, email = email, name = "Alice Anderson")
        performAuthorizeRoundtrip()

        // Same plugwerk_user; no second row provisioned.
        val secondUser = userBySubject(sub)
        assertThat(secondUser.id).`as`("same user, no fresh provisioning").isEqualTo(firstUserId)
        assertThat(secondUser.lastLoginAt)
            .`as`("plugwerk_user.last_login_at should advance on re-login")
            .isAfter(firstLoginAt)

        val refreshTokens = refreshTokenRepository.findAll().filter { it.userId == firstUserId }
        assertThat(refreshTokens).hasSize(2)
    }

    @Test
    fun `OIDC callback fails when ID token has no email claim`() {
        val sub = "alice-no-email-${UUID.randomUUID()}"
        enqueueIdToken(sub = sub, email = null, name = "Alice")

        val response = performAuthorizeRoundtrip()

        // The OidcEmailMissingException path is owned by OidcLoginSuccessHandler
        // which calls response.sendError(SC_BAD_REQUEST, ...). Spring Security's
        // OAuth2LoginAuthenticationFilter sees the response as already-committed
        // and stops processing — so we get a 4xx/5xx from the embedded Tomcat
        // error chain. The exact status varies by Spring Security minor; what
        // matters for the contract is "not a 302 to /" and "no user provisioned".
        assertThat(response.statusCode()).isNotEqualTo(302)
        assertThat(response.statusCode()).isGreaterThanOrEqualTo(400)
        assertThat(oidcIdentityRepository.findByOidcProviderIdAndSubject(providerId, sub)).isEmpty
    }

    @Test
    fun `displayName resolution prefers name claim over preferred_username`() {
        val sub = "alice-display-${UUID.randomUUID()}"
        val email = "$sub@example.test"
        enqueueIdToken(
            sub = sub,
            email = email,
            name = "Alice Anderson",
            preferredUsername = "alice-handle",
        )

        performAuthorizeRoundtrip()

        assertThat(userBySubject(sub).displayName).isEqualTo("Alice Anderson")
    }

    @Test
    fun `displayName falls back to preferred_username when name claim absent`() {
        val sub = "alice-handle-${UUID.randomUUID()}"
        val email = "$sub@example.test"
        enqueueIdToken(sub = sub, email = email, name = null, preferredUsername = "alice-handle")

        performAuthorizeRoundtrip()

        assertThat(userBySubject(sub).displayName).isEqualTo("alice-handle")
    }

    @Test
    fun `displayName falls back to subject when both name and preferred_username are absent`() {
        val sub = "alice-bare-${UUID.randomUUID()}"
        val email = "$sub@example.test"
        enqueueIdToken(sub = sub, email = email, name = null, preferredUsername = null)

        performAuthorizeRoundtrip()

        assertThat(userBySubject(sub).displayName).isEqualTo(sub)
    }

    /**
     * Resolves the [io.plugwerk.server.domain.UserEntity] for a given OIDC
     * subject without traversing the lazy `oidc_identity.user` proxy outside
     * a Hibernate session. We look the identity row up by `(providerId, sub)`,
     * read the `userId` foreign-key value, then load the user explicitly via
     * its own repository — both calls open and close their own short-lived
     * sessions, no LazyInitializationException.
     */
    private fun userBySubject(sub: String): io.plugwerk.server.domain.UserEntity {
        val identity = oidcIdentityRepository.findByOidcProviderIdAndSubject(providerId, sub)
            .orElseThrow { IllegalStateException("oidc_identity row for sub=$sub not found") }
        val userId = requireNotNull(identity.user.id) {
            "oidc_identity points to UserEntity without an id — entity not persisted?"
        }
        return userRepository.findById(userId)
            .orElseThrow { IllegalStateException("UserEntity $userId referenced from oidc_identity is gone") }
    }

    /**
     * Drives the full authorize → callback dance against [mockOAuth2Server] and
     * returns the final Plugwerk-side callback response (either a 302 to "/"
     * on success or a 400 when OidcEmailMissingException fires).
     *
     * Uses a single per-call [HttpClient] with a [CookieManager] so the
     * `JSESSIONID` cookie set by the authorize-start request — which holds the
     * `OAuth2AuthorizationRequest` with the PKCE verifier and `state` — is
     * automatically replayed on the callback request. Each step in the
     * three-leg redirect chain is fetched explicitly (the client is
     * configured with `Redirect.NEVER`) so the test owns the chain
     * end-to-end and can assert at each hop.
     */
    private fun performAuthorizeRoundtrip(): HttpResponse<String> {
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        val client = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

        // Step 1: SPA hits /oauth2/authorization/{providerId}; Spring stashes
        // the OAuth2AuthorizationRequest in the HttpSession (JSESSIONID cookie
        // set on the response) and redirects to the IdP authorize endpoint.
        val authorizeStart = client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/oauth2/authorization/$providerId"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(authorizeStart.statusCode() == 302) {
            "Expected 302 from /oauth2/authorization/$providerId, got ${authorizeStart.statusCode()}: ${authorizeStart.body()}"
        }
        val mockProviderAuthorizeUrl = authorizeStart.headers().firstValue("Location")
            .orElseThrow { IllegalStateException("authorize-start redirect missing Location header") }

        // Step 2: Browser visits the IdP authorize endpoint. The mock server
        // shortcircuits the user-consent step and immediately redirects back
        // to the registered redirect URI with `code` + `state`.
        val providerRedirect = client.send(
            HttpRequest.newBuilder(URI.create(mockProviderAuthorizeUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(providerRedirect.statusCode() in setOf(302, 303)) {
            "Mock provider returned ${providerRedirect.statusCode()}, expected 302/303"
        }
        val callbackUrl = providerRedirect.headers().firstValue("Location")
            .orElseThrow { IllegalStateException("Mock provider redirect missing Location header") }

        // Step 3: Browser follows the redirect to /login/oauth2/code/{providerId}
        // on the Plugwerk backend. The CookieManager replays JSESSIONID, so
        // Spring can recover the OAuth2AuthorizationRequest and complete the
        // code exchange against the mock token endpoint.
        return client.send(
            HttpRequest.newBuilder(URI.create(callbackUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    /**
     * Push a single ID-token spec onto [mockOAuth2Server]'s queue. The mock
     * server pops it on the next /token request and signs the resulting JWT
     * with its in-memory keypair (whose JWKS Spring fetches via OIDC discovery).
     *
     * Setting [email] to null exercises the OidcEmailMissingException path.
     */
    private fun enqueueIdToken(sub: String, email: String?, name: String?, preferredUsername: String? = null) {
        val claims = mutableMapOf<String, Any>()
        email?.let { claims["email"] = it }
        name?.let { claims["name"] = it }
        preferredUsername?.let { claims["preferred_username"] = it }

        mockOAuth2Server.enqueueCallback(
            DefaultOAuth2TokenCallback(
                issuerId = MOCK_ISSUER_ID,
                subject = sub,
                audience = listOf(MOCK_CLIENT_ID),
                claims = claims,
            ),
        )
    }

    @Suppress("unused") // helper kept for future query-parameter assertions
    private fun parseQueryParams(uri: URI): Map<String, String> =
        uri.query.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, Charsets.UTF_8)
        }
}
