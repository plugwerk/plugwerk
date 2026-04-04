/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.security

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.plugwerk.server.PlugwerkProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Result of a rate-limit check.
 */
sealed interface RateLimitResult {
    data class Allowed(val remainingTokens: Long) : RateLimitResult
    data class Rejected(val retryAfterSeconds: Long) : RateLimitResult
}

/**
 * IP-based rate limiting for the login endpoint using Bucket4j token buckets
 * backed by a Caffeine cache for automatic expiry.
 */
@Component
class LoginRateLimitService(props: PlugwerkProperties) {

    private val maxAttempts = props.auth.rateLimit.maxAttempts
    private val windowSeconds = props.auth.rateLimit.windowSeconds

    private val buckets = Caffeine.newBuilder()
        .expireAfterWrite(windowSeconds, TimeUnit.SECONDS)
        .maximumSize(10_000)
        .build<String, Bucket>()

    fun tryConsume(ipAddress: String): RateLimitResult {
        val bucket = buckets.get(ipAddress) { newBucket() }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        return if (probe.isConsumed) {
            RateLimitResult.Allowed(probe.remainingTokens)
        } else {
            val retryAfterSeconds = Duration.ofNanos(probe.nanosToWaitForRefill).toSeconds() + 1
            RateLimitResult.Rejected(retryAfterSeconds)
        }
    }

    private fun newBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(maxAttempts.toLong())
                .refillGreedy(maxAttempts.toLong(), Duration.ofSeconds(windowSeconds))
                .build(),
        )
        .build()
}
