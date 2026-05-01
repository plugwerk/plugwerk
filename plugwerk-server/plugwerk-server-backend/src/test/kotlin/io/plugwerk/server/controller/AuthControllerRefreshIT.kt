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
package io.plugwerk.server.controller

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.RefreshTokenRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.OidcEndSessionUrlResolver
import io.plugwerk.server.security.RefreshTokenCookieFactory
import io.plugwerk.server.service.JwtTokenService
import io.plugwerk.server.service.RefreshTokenService
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * End-to-end auth flow tests (ADR-0027 / #294).
 *
 * Exercises login → refresh → rotate → reuse-detection → logout via MockMvc, asserting
 * cookie attributes, CSRF enforcement on the refresh endpoint only, and that the
 * refresh-token row state mirrors the expected DB side effects at each step.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:auth-refresh-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        // Secure=false because MockMvc does not use HTTPS; verified in a separate unit
        // test on RefreshTokenCookieFactory itself.
        "plugwerk.auth.cookie-secure=false",
    ],
)
class AuthControllerRefreshIT {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var jwtTokenService: JwtTokenService

    @Autowired private lateinit var refreshTokenService: RefreshTokenService

    /**
     * The real resolver depends on a populated [io.plugwerk.server.security.DbClientRegistrationRepository]
     * (i.e. a Keycloak/issuer reachable for OIDC discovery), which is overkill
     * for an in-memory IT. We mock the resolver per-test to control whether the
     * controller hits the OIDC code path or the local-login code path.
     */
    @MockitoBean private lateinit var oidcEndSessionUrlResolver: OidcEndSessionUrlResolver

    private val username = "auth-refresh-it-user"
    private val password = "refresh-it-password-123"

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        userRepository.save(
            UserEntity(
                username = username,
                displayName = username,
                email = "$username@refresh.test",
                source = io.plugwerk.server.domain.UserSource.LOCAL,
                passwordHash = passwordEncoder.encode(password)!!,
                enabled = true,
                passwordChangeRequired = false,
                isSuperadmin = false,
            ),
        )
    }

    @Test
    fun `login issues refresh cookie with httpOnly SameSite=Strict Path=auth`() {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"$password"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val setCookie = result.response.getHeader("Set-Cookie")
        assertThat(setCookie).isNotNull
        assertThat(setCookie!!).contains("${RefreshTokenCookieFactory.COOKIE_NAME}=")
        assertThat(setCookie).contains("HttpOnly")
        assertThat(setCookie).contains("SameSite=Strict")
        assertThat(setCookie).contains("Path=/api/v1/auth")
        assertThat(refreshTokenRepository.findAll()).hasSize(1)
    }

    @Test
    fun `login bumps lastLoginAt but refresh does NOT (#367)`() {
        // Pre-login the column is null — the user was created via repository.save in
        // setUp, never authenticated.
        val before = userRepository.findByUsernameAndSource(username, io.plugwerk.server.domain.UserSource.LOCAL)
            .orElseThrow()
        assertThat(before.lastLoginAt).isNull()

        val loginCookie = loginAndExtractCookie()
        val afterLogin = userRepository.findByUsernameAndSource(username, io.plugwerk.server.domain.UserSource.LOCAL)
            .orElseThrow()
        // Login is a fresh credential check → bumps the column.
        assertThat(afterLogin.lastLoginAt).isNotNull()
        val loginStamp = afterLogin.lastLoginAt

        // Sleep 10ms so any accidental bump is timestamp-distinguishable from
        // the login bump.
        Thread.sleep(10)

        mockMvc.post("/api/v1/auth/refresh") {
            cookie(loginCookie)
            with(csrf())
        }.andExpect { status { isOk() } }

        val afterRefresh = userRepository.findByUsernameAndSource(username, io.plugwerk.server.domain.UserSource.LOCAL)
            .orElseThrow()
        // Refresh is silent session renewal, NOT a fresh credential check —
        // the column must remain at its login-time value.
        assertThat(afterRefresh.lastLoginAt).isEqualTo(loginStamp)
    }

    @Test
    fun `refresh without cookie returns 401`() {
        mockMvc.post("/api/v1/auth/refresh") {
            with(csrf())
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `refresh without CSRF header returns 403 even with valid cookie`() {
        val loginCookie = loginAndExtractCookie()

        mockMvc.post("/api/v1/auth/refresh") {
            cookie(loginCookie)
            // No .with(csrf()) — refresh endpoint requires CSRF per ADR-0027
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `refresh with valid cookie and CSRF rotates token and returns new access token`() {
        val loginCookie = loginAndExtractCookie()
        val firstRowCount = refreshTokenRepository.findAll().size
        assertThat(firstRowCount).isEqualTo(1)

        val response = mockMvc.post("/api/v1/auth/refresh") {
            cookie(loginCookie)
            with(csrf())
        }.andReturn().response

        assertThat(response.status).isEqualTo(200)
        // New access token
        val body = objectMapper.readTree(response.contentAsString)
        assertThat(body["accessToken"].asString()).isNotBlank
        assertThat(body["tokenType"].asString()).isEqualTo("Bearer")
        // New refresh cookie (Set-Cookie header present)
        val setCookie = response.getHeader("Set-Cookie")
        assertThat(setCookie).contains("${RefreshTokenCookieFactory.COOKIE_NAME}=")
        assertThat(setCookie).doesNotContain("${RefreshTokenCookieFactory.COOKIE_NAME}=${loginCookie.value}")
        // Two DB rows now — original (revoked=ROTATED) plus successor (active)
        val rows = refreshTokenRepository.findAll().sortedBy { it.issuedAt }
        assertThat(rows).hasSize(2)
        assertThat(rows[0].revokedAt).isNotNull
        assertThat(rows[0].revocationReason).isEqualTo("ROTATED")
        assertThat(rows[1].revokedAt).isNull()
        assertThat(rows[0].familyId).isEqualTo(rows[1].familyId)
    }

    @Test
    fun `replaying a revoked refresh cookie revokes the whole family`() {
        val loginCookie = loginAndExtractCookie()
        // First rotation: valid
        mockMvc.post("/api/v1/auth/refresh") {
            cookie(loginCookie)
            with(csrf())
        }.andExpect { status { isOk() } }
        // Replay with the original (now-revoked) cookie
        mockMvc.post("/api/v1/auth/refresh") {
            cookie(loginCookie)
            with(csrf())
        }.andExpect { status { isUnauthorized() } }

        // Both rows must now be revoked, with at least one REUSE_DETECTED
        val rows = refreshTokenRepository.findAll()
        assertThat(rows).hasSize(2)
        assertThat(rows.all { it.revokedAt != null }).isTrue()
        assertThat(rows.map { it.revocationReason }).contains("REUSE_DETECTED")
    }

    @Test
    fun `logout clears refresh cookie and revokes family`() {
        val (accessToken, cookie) = loginAndGetAccessAndCookie()

        val response = mockMvc.post("/api/v1/auth/logout") {
            header("Authorization", "Bearer $accessToken")
            cookie(cookie)
            with(csrf())
        }.andReturn().response

        assertThat(response.status).isEqualTo(204)
        val setCookie = response.getHeader("Set-Cookie")
        assertThat(setCookie).contains("${RefreshTokenCookieFactory.COOKIE_NAME}=")
        assertThat(setCookie).contains("Max-Age=0")
        // Refresh-token row is revoked
        val rows = refreshTokenRepository.findAll()
        assertThat(rows).allSatisfy { assertThat(it.revokedAt).isNotNull }
    }

    @Test
    fun `logout for OIDC subject returns 200 and endSessionUrl when provider supports RP-initiated logout (#352)`() {
        val (accessToken, cookie) = setUpOidcSession(idTokenHint = "eyJ.alice.id-token")
        org.mockito.kotlin.whenever(
            oidcEndSessionUrlResolver.resolve(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
            ),
        ).thenReturn("http://kc.local/realms/plugwerk/protocol/openid-connect/logout?id_token_hint=eyJ.alice.id-token")

        val response = mockMvc.post("/api/v1/auth/logout") {
            header("Authorization", "Bearer $accessToken")
            cookie(cookie)
            with(csrf())
        }.andReturn().response

        assertThat(response.status).isEqualTo(200)
        val body = objectMapper.readTree(response.contentAsString)
        assertThat(body["endSessionUrl"].asString())
            .startsWith("http://kc.local/realms/plugwerk/protocol/openid-connect/logout")
        // Cookie still cleared and family still revoked, regardless of the response shape.
        val setCookie = response.getHeader("Set-Cookie")
        assertThat(setCookie).contains("Max-Age=0")
        assertThat(refreshTokenRepository.findAll()).allSatisfy { assertThat(it.revokedAt).isNotNull }
    }

    @Test
    fun `logout for OIDC subject returns 204 when provider does not advertise end_session_endpoint (#352)`() {
        val (accessToken, cookie) = setUpOidcSession(idTokenHint = "eyJ.alice.id-token")
        // Resolver returns null when the IdP did not advertise end_session_endpoint
        // — controller MUST fall back to the local cookie-clear flow.
        org.mockito.kotlin.whenever(
            oidcEndSessionUrlResolver.resolve(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
            ),
        ).thenReturn(null)

        val response = mockMvc.post("/api/v1/auth/logout") {
            header("Authorization", "Bearer $accessToken")
            cookie(cookie)
            with(csrf())
        }.andReturn().response

        assertThat(response.status).isEqualTo(204)
        assertThat(response.contentLength).isLessThanOrEqualTo(0)
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0")
        assertThat(refreshTokenRepository.findAll()).allSatisfy { assertThat(it.revokedAt).isNotNull }
    }

    @Test
    fun `logout for local-login subject never invokes the OIDC resolver (#352)`() {
        val (accessToken, cookie) = loginAndGetAccessAndCookie()

        mockMvc.post("/api/v1/auth/logout") {
            header("Authorization", "Bearer $accessToken")
            cookie(cookie)
            with(csrf())
        }.andReturn().response

        // Local usernames have no colon → regex never matches → resolver never called.
        // Guards against accidentally widening the regex (e.g. dropping the UUID anchor).
        org.mockito.kotlin.verify(oidcEndSessionUrlResolver, org.mockito.kotlin.never())
            .resolve(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var oidcProviderRepository: io.plugwerk.server.repository.OidcProviderRepository

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var oidcIdentityRepository: io.plugwerk.server.repository.OidcIdentityRepository

    /**
     * Builds an OIDC-shaped session in the DB and returns a Bearer access token plus the
     * refresh cookie that points at the just-issued refresh-token row. Mirrors what
     * [io.plugwerk.server.security.OidcLoginSuccessHandler] does on a successful OIDC
     * callback, minus the actual OAuth2 round-trip — the post-#351 shape requires both
     * a `plugwerk_user` row (source=OIDC) and a linked `oidc_identity` row, because
     * the logout flow discriminates OIDC sessions by looking up the identity FK.
     */
    private fun setUpOidcSession(idTokenHint: String): Pair<String, Cookie> {
        val provider = oidcProviderRepository.save(
            io.plugwerk.server.domain.OidcProviderEntity(
                name = "Test KC ${UUID.randomUUID()}",
                providerType = io.plugwerk.server.domain.OidcProviderType.OIDC,
                clientId = "test-client",
                clientSecretEncrypted = "encrypted",
                issuerUri = "https://kc/realms/test",
                enabled = true,
            ),
        )
        val user = userRepository.save(
            UserEntity(
                username = null,
                displayName = "Alice (Keycloak)",
                email = "alice-${UUID.randomUUID()}@oidc.test",
                source = io.plugwerk.server.domain.UserSource.OIDC,
                passwordHash = null,
                enabled = true,
                passwordChangeRequired = false,
                isSuperadmin = false,
            ),
        )
        oidcIdentityRepository.save(
            io.plugwerk.server.domain.OidcIdentityEntity(
                oidcProvider = provider,
                subject = "alice-sub-${UUID.randomUUID()}",
                user = user,
            ),
        )
        val userId = user.id!!
        val accessToken = jwtTokenService.generateToken(userId.toString())
        val issued = refreshTokenService.issue(userId, idTokenHint)
        val cookie = Cookie(RefreshTokenCookieFactory.COOKIE_NAME, issued.plaintext)
        return accessToken to cookie
    }

    // ---------- helpers ----------

    /** One login → returns both access token and refresh cookie so tests avoid issuing twice. */
    private fun loginAndGetAccessAndCookie(): Pair<String, Cookie> {
        val response = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"$password"}"""
        }.andReturn().response
        check(response.status == 200) { "Login failed with ${response.status}: ${response.contentAsString}" }
        val body = objectMapper.readTree(response.contentAsString)
        val accessToken = body["accessToken"].asString()
        val setCookie = response.getHeader("Set-Cookie") ?: error("Login did not set a refresh cookie")
        val cookie = Cookie(RefreshTokenCookieFactory.COOKIE_NAME, extractCookieValue(setCookie))
        return accessToken to cookie
    }

    private fun loginAndExtractCookie(): Cookie = loginAndGetAccessAndCookie().second

    /** Parses the plain cookie value out of a `Set-Cookie` header string. */
    private fun extractCookieValue(setCookieHeader: String): String {
        val prefix = "${RefreshTokenCookieFactory.COOKIE_NAME}="
        val start = setCookieHeader.indexOf(prefix) + prefix.length
        val end = setCookieHeader.indexOf(';', start).let { if (it < 0) setCookieHeader.length else it }
        return setCookieHeader.substring(start, end)
    }
}
