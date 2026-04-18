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
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class ChangePasswordRateLimitServiceTest {

    private fun createService(
        bucketService: BucketRateLimitService = BucketRateLimitService(),
        maxAttempts: Int = 3,
        windowSeconds: Long = 60,
    ): ChangePasswordRateLimitService {
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "a".repeat(32),
                encryptionKey = "b".repeat(16),
                rateLimit = PlugwerkProperties.AuthProperties.RateLimitProperties(
                    changePassword = PlugwerkProperties.AuthProperties.ChangePasswordRateLimitProperties(
                        maxAttempts = maxAttempts,
                        windowSeconds = windowSeconds,
                    ),
                ),
            ),
        )
        return ChangePasswordRateLimitService(bucketService, props)
    }

    @Test
    fun `attempts within limit are allowed`() {
        val service = createService(maxAttempts = 3)

        repeat(3) { i ->
            val result = service.tryConsume("alice")
            assertIs<RateLimitResult.Allowed>(result, "Attempt ${i + 1} should be allowed")
        }
    }

    @Test
    fun `attempt exceeding limit is rejected with retry-after`() {
        val service = createService(maxAttempts = 2)

        repeat(2) { service.tryConsume("alice") }
        val rejected = service.tryConsume("alice")

        assertIs<RateLimitResult.Rejected>(rejected)
        assert(rejected.retryAfterSeconds > 0)
    }

    @Test
    fun `different subjects have independent buckets`() {
        val service = createService(maxAttempts = 1)

        assertIs<RateLimitResult.Allowed>(service.tryConsume("alice"))
        assertIs<RateLimitResult.Rejected>(service.tryConsume("alice"))
        // Bob still has a full bucket.
        assertIs<RateLimitResult.Allowed>(service.tryConsume("bob"))
    }

    @Test
    fun `login and change-password share the literal key without colliding`() {
        // Both services funnel through the same BucketRateLimitService. The different
        // scope prefixes ("login" vs "changePassword") must keep their buckets separate.
        val bucketService = BucketRateLimitService()

        val loginProps = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "a".repeat(32),
                encryptionKey = "b".repeat(16),
                rateLimit = PlugwerkProperties.AuthProperties.RateLimitProperties(
                    maxAttempts = 1,
                    windowSeconds = 60,
                ),
            ),
        )
        val login = LoginRateLimitService(bucketService, loginProps)
        val changePassword = createService(bucketService, maxAttempts = 1)

        assertIs<RateLimitResult.Allowed>(login.tryConsume("10.0.0.1"))
        assertIs<RateLimitResult.Rejected>(login.tryConsume("10.0.0.1"))
        // Same literal key — but in the change-password scope, still a fresh bucket.
        assertIs<RateLimitResult.Allowed>(changePassword.tryConsume("10.0.0.1"))
    }

    @Test
    fun `bucket refills after window expires`() {
        val service = createService(maxAttempts = 2, windowSeconds = 1)

        repeat(2) { service.tryConsume("alice") }
        assertIs<RateLimitResult.Rejected>(service.tryConsume("alice"))
        Thread.sleep(1_100)
        assertIs<RateLimitResult.Allowed>(service.tryConsume("alice"))
    }

    @Test
    fun `remaining tokens decrease with each attempt`() {
        val service = createService(maxAttempts = 3)

        val first = service.tryConsume("alice")
        assertIs<RateLimitResult.Allowed>(first)
        assert(first.remainingTokens == 2L)

        val second = service.tryConsume("alice")
        assertIs<RateLimitResult.Allowed>(second)
        assert(second.remainingTokens == 1L)
    }
}
