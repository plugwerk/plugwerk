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

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Reusable Bucket4j-backed rate limiter. Callers pass a scoped key and the capacity /
 * refill-window for that scope at consume-time.
 *
 * Keys are namespaced by scope so the same literal subject string used in two different
 * rate-limit scopes (e.g. login vs change-password) never collides. The scope is not
 * carried in the [RateLimitResult] — callers are expected to pass a consistent scope
 * label for a given rate-limit context.
 */
@Component
class BucketRateLimitService {

    /**
     * Caffeine cache keyed on `"scope:key"`. Entries auto-expire after the caller's
     * window so unused buckets free their memory. Capped at 10k entries to bound
     * memory under high-cardinality workloads (e.g. many subjects).
     */
    private val buckets = Caffeine.newBuilder()
        .expireAfterAccess(MAX_BUCKET_TTL, TimeUnit.SECONDS)
        .maximumSize(MAX_BUCKETS)
        .build<String, Bucket>()

    /**
     * Attempts to consume one token from the bucket identified by `scope + key`.
     *
     * @param scope rate-limit context label (e.g. `"login"`, `"changePassword"`)
     * @param key the in-scope bucket identifier (IP address, subject, etc.)
     * @param maxAttempts bucket capacity / greedy-refill size
     * @param windowSeconds refill window in seconds
     */
    fun tryConsume(scope: String, key: String, maxAttempts: Int, windowSeconds: Long): RateLimitResult {
        val bucket = buckets.get("$scope:$key") { newBucket(maxAttempts, windowSeconds) }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        return if (probe.isConsumed) {
            RateLimitResult.Allowed(probe.remainingTokens)
        } else {
            val retryAfterSeconds = Duration.ofNanos(probe.nanosToWaitForRefill).toSeconds() + 1
            RateLimitResult.Rejected(retryAfterSeconds)
        }
    }

    private fun newBucket(maxAttempts: Int, windowSeconds: Long): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(maxAttempts.toLong())
                .refillGreedy(maxAttempts.toLong(), Duration.ofSeconds(windowSeconds))
                .build(),
        )
        .build()

    private companion object {
        /** Upper bound on how long an idle bucket stays in memory, independent of caller window. */
        const val MAX_BUCKET_TTL: Long = 3600
        const val MAX_BUCKETS: Long = 10_000
    }
}
