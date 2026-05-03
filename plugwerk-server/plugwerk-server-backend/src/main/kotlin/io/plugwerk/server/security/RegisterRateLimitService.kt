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
 * Two-bucket rate limiter for `POST /api/v1/auth/register` (#420).
 *
 * The IP bucket fires first (in [RegisterRateLimitFilter] before any body
 * parsing), defends against bulk account creation from a single source.
 * The email bucket fires after body parsing inside the controller and
 * defends against email-enumeration probes — an attacker can't iterate
 * candidate addresses faster than [PlugwerkProperties.AuthProperties.RateLimitProperties.RegisterRateLimitProperties.emailMaxAttempts]
 * per [emailWindowSeconds] even from a botnet, because the email itself
 * is the bucket key (hashed so the address never sits in memory verbatim).
 */
@Component
class RegisterRateLimitService(private val bucketService: BucketRateLimitService, props: PlugwerkProperties) {
    private val ipMaxAttempts = props.auth.rateLimit.register.ipMaxAttempts
    private val ipWindowSeconds = props.auth.rateLimit.register.ipWindowSeconds
    private val emailMaxAttempts = props.auth.rateLimit.register.emailMaxAttempts
    private val emailWindowSeconds = props.auth.rateLimit.register.emailWindowSeconds

    fun tryConsumeIp(ipAddress: String): RateLimitResult =
        bucketService.tryConsume(IP_SCOPE, ipAddress, ipMaxAttempts, ipWindowSeconds)

    /**
     * Email-keyed consumption. The email is hashed so the bucket map never
     * stores the raw address — same posture as the verification token table.
     * Lower-cases first so `Alice@x` and `alice@x` share a bucket.
     */
    fun tryConsumeEmail(email: String): RateLimitResult {
        val key = sha256Hex(email.trim().lowercase())
        return bucketService.tryConsume(EMAIL_SCOPE, key, emailMaxAttempts, emailWindowSeconds)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private companion object {
        const val IP_SCOPE = "register:ip"
        const val EMAIL_SCOPE = "register:email"
    }
}
