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

import io.plugwerk.server.PlugwerkProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Locks the security-relevant attribute invariants of the refresh-token cookie
 * (ADR-0027 / #294). These attributes are the entire reason the session moved
 * off `localStorage`, so a silent regression on any of them must fail the build.
 */
class RefreshTokenCookieFactoryTest {

    private val jwtSecret = "test-secret-key-that-is-long-enough-32chars"

    private fun factory(cookieSecure: Boolean = true, baseUrl: String = "https://plugwerk.example.com") =
        RefreshTokenCookieFactory(
            PlugwerkProperties(
                server = PlugwerkProperties.ServerProperties(baseUrl = baseUrl),
                auth = PlugwerkProperties.AuthProperties(jwtSecret = jwtSecret, cookieSecure = cookieSecure),
            ),
        )

    @Test
    fun `build sets the hardened attribute set for an issued token`() {
        val cookie = factory().build("opaque-token", Duration.ofHours(168))

        assertThat(cookie.name).isEqualTo(RefreshTokenCookieFactory.COOKIE_NAME)
        assertThat(cookie.value).isEqualTo("opaque-token")
        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.isSecure).isTrue()
        assertThat(cookie.sameSite).isEqualTo(RefreshTokenCookieFactory.SAME_SITE)
        assertThat(cookie.sameSite).isEqualTo("Strict")
        assertThat(cookie.path).isEqualTo(RefreshTokenCookieFactory.COOKIE_PATH)
        assertThat(cookie.path).isEqualTo("/api/v1/auth")
        assertThat(cookie.maxAge).isEqualTo(Duration.ofHours(168))
    }

    @Test
    fun `build honours cookieSecure=false for local HTTP dev`() {
        val cookie = factory(cookieSecure = false, baseUrl = "http://localhost:8080")
            .build("opaque-token", Duration.ofHours(1))

        assertThat(cookie.isSecure).isFalse()
        // Everything else stays hardened even when Secure is off.
        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.sameSite).isEqualTo("Strict")
    }

    @Test
    fun `clear produces an expired empty cookie with the same scope and hardening`() {
        val cookie = factory().clear()

        assertThat(cookie.name).isEqualTo(RefreshTokenCookieFactory.COOKIE_NAME)
        assertThat(cookie.value).isEmpty()
        assertThat(cookie.maxAge).isEqualTo(Duration.ZERO)
        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.sameSite).isEqualTo("Strict")
        assertThat(cookie.path).isEqualTo("/api/v1/auth")
    }

    @Test
    fun `startup validation warns without throwing for the http-plus-secure misconfiguration`() {
        // http:// base URL + Secure=true is the classic "silent logout" trap; the
        // validator logs a WARN but must never fail startup.
        assertThatCode {
            factory(cookieSecure = true, baseUrl = "http://localhost:8080")
                .validateCookieSecureAgainstBaseUrl()
        }.doesNotThrowAnyException()
    }

    @Test
    fun `startup validation is a no-op for a correct HTTPS-plus-secure deployment`() {
        assertThatCode {
            factory(cookieSecure = true, baseUrl = "https://plugwerk.example.com")
                .validateCookieSecureAgainstBaseUrl()
        }.doesNotThrowAnyException()
    }
}
