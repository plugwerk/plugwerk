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
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Full-roundtrip OIDC web-login integration test against a **real Keycloak**
 * (issue #79) running in a Testcontainers container, complementing the
 * mock-oauth2-server flow in [OidcWebLoginIT].
 *
 * Where the mock server shortcircuits user consent, Keycloak serves a real HTML
 * login form — so this test additionally submits `alice` / `password` against
 * Keycloak's `login-actions/authenticate` endpoint, proving Plugwerk's OIDC
 * integration works against a genuine, spec-compliant IdP (discovery metadata,
 * PKCE S256, ID-token signature + issuer validation, code exchange).
 *
 * The container imports [/keycloak/plugwerk-test-realm.json]: the same
 * `plugwerk-local` confidential client as the dev realm, but with a wildcard
 * redirect URI because the Spring Boot test binds a RANDOM_PORT the realm cannot
 * know in advance.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OidcKeycloakLoginIT {

    companion object {
        private const val REALM = "plugwerk"
        private const val CLIENT_ID = "plugwerk-local"
        private const val CLIENT_SECRET = "local-dev-secret-do-not-use-in-prod"
        private const val KEYCLOAK_INTERNAL_PORT = 8080

        /** Matches Keycloak's login `<form id="kc-form-login" … action="…">` to recover the POST target. */
        private val LOGIN_FORM_ACTION =
            Regex("<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)

        @JvmStatic
        private val keycloak: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("quay.io/keycloak/keycloak:26.1"))
                .withExposedPorts(KEYCLOAK_INTERNAL_PORT)
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/plugwerk-test-realm.json"),
                    "/opt/keycloak/data/import/plugwerk-test-realm.json",
                )
                .withCommand("start-dev", "--import-realm")
                .waitingFor(
                    Wait.forHttp("/realms/$REALM/.well-known/openid-configuration")
                        .forPort(KEYCLOAK_INTERNAL_PORT)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)),
                )

        @BeforeAll
        @JvmStatic
        fun startKeycloak() {
            keycloak.start()
        }

        @AfterAll
        @JvmStatic
        fun stopKeycloak() {
            keycloak.stop()
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

    private val keycloakBaseUrl: String
        get() = "http://${keycloak.host}:${keycloak.getMappedPort(KEYCLOAK_INTERNAL_PORT)}"

    @BeforeEach
    fun seedProvider() {
        refreshTokenRepository.deleteAll()
        oidcIdentityRepository.deleteAll()
        userRepository.findAll()
            .filter { it.enabled && it.source == UserSource.EXTERNAL }
            .forEach { userRepository.delete(it) }
        oidcProviderRepository.deleteAll()

        oidcProviderRepository.saveAndFlush(
            OidcProviderEntity(
                name = "Keycloak",
                providerType = OidcProviderType.OIDC,
                enabled = true,
                clientId = CLIENT_ID,
                clientSecretEncrypted = textEncryptor.encrypt(CLIENT_SECRET),
                issuerUri = "$keycloakBaseUrl/realms/$REALM",
                scope = "openid email profile",
            ),
        )
        // Load the freshly-registered provider into the in-memory registry so the
        // first /oauth2/authorization/{id} request resolves it.
        dbClientRegistrationRepository.refresh()
    }

    @Test
    fun `alice logs in through Keycloak - user is provisioned and a refresh cookie is set`() {
        val providerId = oidcProviderRepository.findAll().single().id.toString()

        val callback = loginThroughKeycloak(providerId, "alice", "password")

        // Plugwerk's OidcLoginSuccessHandler redirects the SPA to root after a
        // successful login and drops the httpOnly refresh cookie.
        assertThat(callback.statusCode()).isEqualTo(302)
        assertThat(URI.create(callback.headers().firstValue("Location").orElse("")).path).isEqualTo("/")
        val cookies = callback.headers().allValues("Set-Cookie")
        assertThat(cookies).anyMatch { it.startsWith("plugwerk_refresh=") && it.contains("HttpOnly") }

        // A plugwerk_user row was provisioned from the Keycloak identity.
        val user = userRepository.findAll().single { it.email == "alice@plugwerk.test" }
        assertThat(user.source).isEqualTo(UserSource.EXTERNAL)
        assertThat(user.username).isNull()
        assertThat(user.passwordHash).isNull()
        assertThat(user.displayName).isEqualTo("Alice Tester")
        assertThat(user.enabled).isTrue()

        val refreshTokens = refreshTokenRepository.findAll().filter { it.userId == user.id }
        assertThat(refreshTokens).hasSize(1)
    }

    @Test
    fun `wrong Keycloak password never provisions a Plugwerk user`() {
        val providerId = oidcProviderRepository.findAll().single().id.toString()

        // Keycloak re-renders the login form (200) instead of redirecting back,
        // so the flow never reaches Plugwerk's callback.
        val loginForm = submitKeycloakLogin(providerId, "alice", "wrong-password")
        assertThat(loginForm.statusCode()).isEqualTo(200)
        assertThat(userRepository.findAll().none { it.email == "alice@plugwerk.test" }).isTrue()
    }

    /**
     * Drives authorize → Keycloak login form → callback and returns Plugwerk's
     * final callback response.
     */
    private fun loginThroughKeycloak(providerId: String, username: String, password: String): HttpResponse<String> {
        val flow = HttpFlow()
        val callbackUrl = submitKeycloakLoginWith(flow, providerId, username, password)
            .headers().firstValue("Location")
            .orElseThrow { IllegalStateException("Keycloak did not redirect back to Plugwerk — login likely failed") }
        return flow.get(callbackUrl)
    }

    /** Runs authorize + login-form POST and returns Keycloak's raw response to the POST. */
    private fun submitKeycloakLogin(providerId: String, username: String, password: String): HttpResponse<String> =
        submitKeycloakLoginWith(HttpFlow(), providerId, username, password)

    private fun submitKeycloakLoginWith(
        flow: HttpFlow,
        providerId: String,
        username: String,
        password: String,
    ): HttpResponse<String> {
        // 1. authorize-start: Plugwerk redirects to Keycloak's authorize endpoint.
        val authorizeStart = flow.get("http://localhost:$port/oauth2/authorization/$providerId")
        check(authorizeStart.statusCode() == 302) {
            "authorize-start returned ${authorizeStart.statusCode()}, expected 302"
        }
        val keycloakAuthorizeUrl = authorizeStart.headers().firstValue("Location")
            .orElseThrow { IllegalStateException("authorize-start missing Location header") }

        // 2. Keycloak serves the HTML login form.
        val loginPage = flow.get(keycloakAuthorizeUrl)
        check(loginPage.statusCode() == 200) {
            "expected Keycloak login page (200), got ${loginPage.statusCode()}"
        }
        val formAction = LOGIN_FORM_ACTION.find(loginPage.body())?.groupValues?.get(1)?.replace("&amp;", "&")
            ?: error("could not locate the Keycloak login form action in the response body")

        // 3. Submit credentials.
        return flow.postForm(formAction, "username=${enc(username)}&password=${enc(password)}&credentialId=")
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    /**
     * Minimal manual cookie jar over the JDK [HttpClient]. Needed because
     * Keycloak marks its session cookies `Secure` even on the plain-HTTP test
     * connection, and the JDK's built-in [CookieManager] refuses to replay
     * `Secure` cookies over HTTP — which breaks the login-form POST. We instead
     * capture every `Set-Cookie` name/value and echo it back on the next request
     * (ignoring attributes), mirroring how a browser treats cookies as
     * host-scoped rather than port-scoped on localhost.
     */
    private inner class HttpFlow {
        private val jar = LinkedHashMap<String, String>()
        private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

        fun get(url: String): HttpResponse<String> = send(HttpRequest.newBuilder(URI.create(url)).GET())

        fun postForm(url: String, body: String): HttpResponse<String> = send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)),
        )

        private fun send(builder: HttpRequest.Builder): HttpResponse<String> {
            if (jar.isNotEmpty()) {
                builder.header("Cookie", jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            response.headers().allValues("set-cookie").forEach { setCookie ->
                val pair = setCookie.substringBefore(";")
                val name = pair.substringBefore("=").trim()
                val value = pair.substringAfter("=", "").trim()
                if (name.isNotEmpty()) {
                    if (value.isEmpty()) jar.remove(name) else jar[name] = value
                }
            }
            return response
        }
    }
}
