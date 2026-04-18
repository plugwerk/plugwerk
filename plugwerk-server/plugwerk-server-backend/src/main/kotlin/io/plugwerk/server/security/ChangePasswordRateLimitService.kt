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

/**
 * Subject-keyed rate limiting for the change-password endpoint. Thin facade over
 * [BucketRateLimitService] scoped with `"changePassword"` — independent of the
 * login bucket so neither endpoint can drain the other.
 */
@Component
class ChangePasswordRateLimitService(private val bucketService: BucketRateLimitService, props: PlugwerkProperties) {

    private val maxAttempts = props.auth.rateLimit.changePassword.maxAttempts
    private val windowSeconds = props.auth.rateLimit.changePassword.windowSeconds

    fun tryConsume(subject: String): RateLimitResult =
        bucketService.tryConsume(SCOPE, subject, maxAttempts, windowSeconds)

    private companion object {
        const val SCOPE = "changePassword"
    }
}
