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

import io.plugwerk.server.PlugwerkProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class LoginRateLimitServiceTest {

    private fun createService(maxAttempts: Int = 5, windowSeconds: Long = 60): LoginRateLimitService {
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "a".repeat(32),
                encryptionKey = "b".repeat(16),
                rateLimit = PlugwerkProperties.AuthProperties.RateLimitProperties(
                    maxAttempts = maxAttempts,
                    windowSeconds = windowSeconds,
                ),
            ),
        )
        return LoginRateLimitService(props)
    }

    @Test
    fun `requests within limit are allowed`() {
        val service = createService(maxAttempts = 5)
        repeat(5) { i ->
            val result = service.tryConsume("192.168.1.1")
            assertIs<RateLimitResult.Allowed>(result, "Request ${i + 1} should be allowed")
        }
    }

    @Test
    fun `request exceeding limit is rejected`() {
        val service = createService(maxAttempts = 3)
        repeat(3) { service.tryConsume("10.0.0.1") }
        val result = service.tryConsume("10.0.0.1")
        assertIs<RateLimitResult.Rejected>(result)
        assert(result.retryAfterSeconds > 0) { "retryAfterSeconds should be positive" }
    }

    @Test
    fun `different IPs have independent limits`() {
        val service = createService(maxAttempts = 2)
        repeat(2) { service.tryConsume("10.0.0.1") }
        assertIs<RateLimitResult.Rejected>(service.tryConsume("10.0.0.1"))
        assertIs<RateLimitResult.Allowed>(service.tryConsume("10.0.0.2"))
    }

    @Test
    fun `bucket refills after window expires`() {
        val service = createService(maxAttempts = 2, windowSeconds = 1)
        repeat(2) { service.tryConsume("10.0.0.1") }
        assertIs<RateLimitResult.Rejected>(service.tryConsume("10.0.0.1"))
        Thread.sleep(1_100)
        assertIs<RateLimitResult.Allowed>(service.tryConsume("10.0.0.1"))
    }

    @Test
    fun `remaining tokens decrease with each request`() {
        val service = createService(maxAttempts = 5)
        val first = service.tryConsume("10.0.0.1")
        assertIs<RateLimitResult.Allowed>(first)
        assert(first.remainingTokens == 4L) { "Expected 4 remaining, got ${first.remainingTokens}" }

        val second = service.tryConsume("10.0.0.1")
        assertIs<RateLimitResult.Allowed>(second)
        assert(second.remainingTokens == 3L) { "Expected 3 remaining, got ${second.remainingTokens}" }
    }
}
