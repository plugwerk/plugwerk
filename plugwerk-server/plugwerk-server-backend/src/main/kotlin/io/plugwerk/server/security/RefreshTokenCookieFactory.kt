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
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Builds the httpOnly `Set-Cookie` header for the opaque refresh token (ADR-0027 / #294).
 *
 * Attribute invariants:
 *   - `HttpOnly=true`: JavaScript cannot read the cookie. The whole point of moving off
 *     localStorage is that XSS cannot exfiltrate the session credential.
 *   - `Secure`: configurable via [PlugwerkProperties.AuthProperties.cookieSecure]; defaults
 *     to `true` so HTTPS deployments get it right by default. Local HTTP dev overrides to
 *     `false` (browsers silently drop `Secure` cookies on plain HTTP).
 *   - `SameSite=Strict`: strongest option — cookie is not sent on any cross-site request,
 *     including top-level navigations. Combined with the double-submit CSRF token this
 *     gives defence in depth against CSRF on the refresh endpoint.
 *   - `Path=/api/v1/auth`: narrow scope so the cookie is only sent on auth-related calls
 *     (`/login`, `/refresh`, `/logout`) and nothing else.
 *   - `Max-Age` matches the server-side refresh-token expiry exactly — no drift.
 */
@Component
class RefreshTokenCookieFactory(private val props: PlugwerkProperties) {

    private val log = LoggerFactory.getLogger(RefreshTokenCookieFactory::class.java)

    /**
     * Warn loudly at startup when the deployment combines a plain-HTTP base URL with the
     * default `cookieSecure = true`. Browsers silently drop `Secure` cookies on HTTP
     * connections, which makes the refresh cookie invisible to the server on the next
     * request — every page reload then looks like a forced logout. Dev setups routinely
     * trip on this without a clear signal; a single `WARN` line here beats hours of
     * debugging. See ADR-0027 and `.env.example` for the override.
     */
    @PostConstruct
    fun validateCookieSecureAgainstBaseUrl() {
        val baseUrl = props.server.baseUrl
        if (baseUrl.startsWith("http://") && props.auth.cookieSecure) {
            log.warn(
                "PLUGWERK_AUTH_COOKIE_SECURE=true combined with an http:// base URL " +
                    "({}) — browsers drop `Secure` cookies on plain HTTP, so the refresh " +
                    "cookie will never be stored and every reload will redirect to /login. " +
                    "Set PLUGWERK_AUTH_COOKIE_SECURE=false for local HTTP dev (see ADR-0027).",
                baseUrl,
            )
        }
    }

    /** Builds the cookie for a freshly-issued refresh token. */
    fun build(plaintext: String, maxAge: Duration): ResponseCookie = ResponseCookie.from(COOKIE_NAME, plaintext)
        .httpOnly(true)
        .secure(props.auth.cookieSecure)
        .sameSite(SAME_SITE)
        .path(COOKIE_PATH)
        .maxAge(maxAge)
        .build()

    /** Builds a clearing cookie (empty value, `Max-Age=0`) for logout / failed refresh. */
    fun clear(): ResponseCookie = ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .secure(props.auth.cookieSecure)
        .sameSite(SAME_SITE)
        .path(COOKIE_PATH)
        .maxAge(0)
        .build()

    companion object {
        /** Cookie name — stable so future refactors cannot accidentally rename it under us. */
        const val COOKIE_NAME = "plugwerk_refresh"

        /** Narrow cookie path — only auth endpoints see the cookie at all. */
        const val COOKIE_PATH = "/api/v1/auth"

        /** `Strict` blocks the cookie on every cross-site request, top-level navigation included. */
        const val SAME_SITE = "Strict"
    }
}
