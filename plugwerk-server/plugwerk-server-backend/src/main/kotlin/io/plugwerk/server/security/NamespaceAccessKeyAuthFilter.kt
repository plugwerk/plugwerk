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

import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Lazy
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.OffsetDateTime

/**
 * Authenticates requests that carry an `X-Api-Key` header.
 *
 * The lookup path is designed to be timing-invariant over hit vs. miss
 * (ADR-0024, audit row SBS-008 / #291):
 *
 * 1. Compute the deterministic HMAC-SHA256 of the full presented key.
 * 2. Single indexed equality probe against `namespace_access_key.key_lookup_hash`.
 * 3. If a row is found: verify the BCrypt hash as a defense-in-depth second
 *    check, then authenticate. If not found: run a BCrypt comparison against
 *    a fixed dummy hash so the total request time does not reveal whether a
 *    match occurred. Both branches end with exactly one BCrypt `matches()` call.
 */
@Component
class NamespaceAccessKeyAuthFilter(
    private val apiKeyRepository: NamespaceAccessKeyRepository,
    @Lazy private val passwordEncoder: PasswordEncoder,
    private val accessKeyHmac: AccessKeyHmac,
) : OncePerRequestFilter() {

    companion object {
        const val HEADER_NAME = "X-Api-Key"
        private const val MIN_KEY_LENGTH = 8

        /**
         * Valid BCrypt ciphertext that no real password produces. Used to keep
         * the miss-path identical in cost to the hit-path (a single BCrypt
         * verification on the received key). The hash below was generated with
         * BCrypt strength 10 for the UTF-8 string `\u0000` — a byte nul that
         * is not a legal generated key (keys are alphanumeric, 44 chars).
         */
        private const val DUMMY_BCRYPT_HASH =
            "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val apiKey = request.getHeader(HEADER_NAME)
        val existingAuth = SecurityContextHolder.getContext().authentication
        val shouldAuthenticate = apiKey != null &&
            apiKey.length >= MIN_KEY_LENGTH &&
            (existingAuth == null || existingAuth is AnonymousAuthenticationToken)

        if (shouldAuthenticate && apiKey != null) {
            authenticateConstantTime(apiKey)
        }
        filterChain.doFilter(request, response)
    }

    private fun authenticateConstantTime(apiKey: String) {
        val lookupHash = accessKeyHmac.compute(apiKey)
        val candidate = apiKeyRepository.findByKeyLookupHashAndRevokedFalse(lookupHash).orElse(null)
        val expiredAt = candidate?.expiresAt
        val isExpired = expiredAt != null && expiredAt.isBefore(OffsetDateTime.now())
        // Always run exactly one BCrypt verification. When no candidate matched, verify
        // against a fixed dummy hash so the miss-path costs the same ~100ms as the hit-path.
        val hashToVerify = candidate?.keyHash ?: DUMMY_BCRYPT_HASH
        val matches = passwordEncoder.matches(apiKey, hashToVerify)

        if (candidate != null && !isExpired && matches) {
            val auth = UsernamePasswordAuthenticationToken(
                "key:${candidate.namespace.slug}",
                null,
                listOf(SimpleGrantedAuthority("ROLE_API_CLIENT")),
            )
            SecurityContextHolder.getContext().authentication = auth
        }
    }
}
