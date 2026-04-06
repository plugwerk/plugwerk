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

import io.plugwerk.server.service.TokenRevocationService
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component

/**
 * Composite [JwtDecoder] that tries the local HMAC decoder first, then falls back to
 * any enabled OIDC provider decoders from [OidcProviderRegistry].
 *
 * **Decoding strategy:**
 * 1. Try the [localDecoder] (HMAC-SHA256 — locally issued tokens from `/api/v1/auth/login`).
 *    If the local decoder succeeds, check whether the token has been revoked.
 * 2. If the local decoder throws [JwtException] (e.g. wrong algorithm or unknown issuer),
 *    try each active OIDC decoder in [OidcProviderRegistry.decoders] in registration order.
 *    OIDC tokens are **not** subject to local revocation checks.
 * 3. If no decoder succeeds, throw [JwtException] indicating the token is invalid.
 *
 * This design keeps the local token path fast (single decoder, cache-backed revocation check)
 * while transparently supporting OIDC tokens without changes to the filter chain.
 */
@Component
class DelegatingJwtDecoder(
    private val localDecoder: JwtDecoder,
    private val oidcProviderRegistry: OidcProviderRegistry,
    private val tokenRevocationService: TokenRevocationService,
) : JwtDecoder {

    private val log = LoggerFactory.getLogger(DelegatingJwtDecoder::class.java)

    override fun decode(token: String): Jwt {
        // Fast path: locally issued JWT
        runCatching { localDecoder.decode(token) }.onSuccess { jwt ->
            checkRevocation(jwt)
            return jwt
        }

        // Fallback: enabled OIDC providers (not subject to local revocation)
        for (decoder in oidcProviderRegistry.decoders()) {
            runCatching { decoder.decode(token) }.onSuccess { return it }
        }

        log.debug("Token was rejected by all decoders (local + {} OIDC)", oidcProviderRegistry.decoders().size)
        throw JwtException("Token is not valid for any configured issuer")
    }

    private fun checkRevocation(jwt: Jwt) {
        val jti = jwt.id ?: return
        val subject = jwt.subject ?: return
        val issuedAt = jwt.issuedAt ?: return
        if (tokenRevocationService.isRevoked(jti, subject, issuedAt)) {
            log.debug("Token jti={} for user={} has been revoked", jti, subject)
            throw JwtException("Token has been revoked")
        }
    }
}
