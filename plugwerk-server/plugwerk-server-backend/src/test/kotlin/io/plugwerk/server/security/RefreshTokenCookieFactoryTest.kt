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
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Pins the cookie-attribute contract for the refresh-cookie factory. The
 * load-bearing assertion for issue #317 lives here: when `persistent=false`,
 * the resulting Set-Cookie header has neither `Max-Age` nor `Expires`, while
 * every other security attribute (`HttpOnly`, `Secure`, `SameSite=Strict`,
 * `Path=/api/v1/auth`) stays untouched. RFC 6265 §5.3 promotes a cookie with
 * neither expiry attribute to a session cookie that the browser drops on close.
 */
class RefreshTokenCookieFactoryTest {

    private fun factory(cookieSecure: Boolean = true): RefreshTokenCookieFactory {
        val props = PlugwerkProperties(
            server = PlugwerkProperties.ServerProperties(baseUrl = "https://example.test"),
            auth = PlugwerkProperties.AuthProperties(cookieSecure = cookieSecure),
        )
        return RefreshTokenCookieFactory(props)
    }

    @Test
    fun `persistent default carries Max-Age matching the requested duration`() {
        val cookie = factory().build("plain", Duration.ofDays(7))
        val header = cookie.toString()

        // 7 days = 604800 seconds — Spring's ResponseCookie serialises Max-Age
        // in seconds, so the literal value is what the browser receives.
        assertThat(header).contains("Max-Age=604800")
        assertThat(header).contains("HttpOnly")
        assertThat(header).contains("Secure")
        assertThat(header).contains("SameSite=Strict")
        assertThat(header).contains("Path=/api/v1/auth")
    }

    @Test
    fun `explicit persistent=true is identical to the default`() {
        val implicit = factory().build("plain", Duration.ofDays(7)).toString()
        val explicit = factory().build("plain", Duration.ofDays(7), persistent = true).toString()
        assertThat(explicit).isEqualTo(implicit)
    }

    @Test
    fun `persistent=false produces a session cookie without Max-Age or Expires`() {
        val cookie = factory().build("plain", Duration.ofDays(7), persistent = false)
        val header = cookie.toString()

        // The load-bearing assertion: neither expiry attribute may appear.
        // Either one would convert the cookie back to persistent and defeat
        // the entire point of the unticked checkbox.
        assertThat(header).doesNotContain("Max-Age")
        assertThat(header).doesNotContain("Expires")

        // All other attributes must be unchanged — the cookie is still
        // HttpOnly + Secure + SameSite=Strict + scoped to /api/v1/auth.
        assertThat(header).contains("HttpOnly")
        assertThat(header).contains("Secure")
        assertThat(header).contains("SameSite=Strict")
        assertThat(header).contains("Path=/api/v1/auth")
        assertThat(header).startsWith("plugwerk_refresh=plain")
    }

    @Test
    fun `persistent=false respects cookieSecure=false for plain-HTTP dev`() {
        // Even when the operator opts out of `Secure` for local HTTP dev, the
        // session-vs-persistent toggle must still work — they are independent
        // dimensions.
        val header = factory(cookieSecure = false).build("plain", Duration.ofDays(7), persistent = false).toString()
        assertThat(header).doesNotContain("Max-Age")
        assertThat(header).doesNotContain("Secure")
        assertThat(header).contains("HttpOnly")
    }

    @Test
    fun `clear cookie is unaffected by the persistent toggle`() {
        // The clearing cookie always carries Max-Age=0 — this is how the
        // browser is asked to delete the existing cookie. Issue #317 only
        // changes the build path, not the clear path.
        val header = factory().clear().toString()
        assertThat(header).contains("Max-Age=0")
        assertThat(header).contains("plugwerk_refresh=")
    }
}
