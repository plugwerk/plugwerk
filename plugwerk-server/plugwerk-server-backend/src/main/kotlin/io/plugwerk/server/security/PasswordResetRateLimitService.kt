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
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * Two-bucket rate limiter for the password-reset flow (#421).
 *
 * Mirrors [RegisterRateLimitService] but keyed differently: the
 * `forgot-password` endpoint uses the **IP** bucket (no email-keyed
 * bucket here, because that would leak enumeration via timing — an
 * existing-account bucket would empty faster than a non-existing one),
 * and the `reset-password` endpoint additionally consults a **token**
 * bucket so a leaked link cannot be brute-forced for its TTL window.
 */
@Component
class PasswordResetRateLimitService(private val bucketService: BucketRateLimitService, props: PlugwerkProperties) {
    private val ipMaxAttempts = props.auth.rateLimit.passwordReset.ipMaxAttempts
    private val ipWindowSeconds = props.auth.rateLimit.passwordReset.ipWindowSeconds
    private val tokenMaxAttempts = props.auth.rateLimit.passwordReset.tokenMaxAttempts
    private val tokenWindowSeconds = props.auth.rateLimit.passwordReset.tokenWindowSeconds

    fun tryConsumeIp(ipAddress: String): RateLimitResult =
        bucketService.tryConsume(IP_SCOPE, ipAddress, ipMaxAttempts, ipWindowSeconds)

    /**
     * Token-keyed consumption. The submitted plaintext token is hashed so
     * the bucket map never holds it verbatim — same posture as the
     * `password_reset_token` table itself.
     */
    fun tryConsumeToken(rawToken: String): RateLimitResult {
        val key = sha256Hex(rawToken)
        return bucketService.tryConsume(TOKEN_SCOPE, key, tokenMaxAttempts, tokenWindowSeconds)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private companion object {
        const val IP_SCOPE = "password-reset:ip"
        const val TOKEN_SCOPE = "password-reset:token"
    }
}
