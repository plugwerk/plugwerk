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
package io.plugwerk.server.util

import org.springframework.stereotype.Component

/**
 * Pauses the current thread for [millis] milliseconds.
 *
 * Wraps `Thread.sleep` behind a tiny interface so consumers that need to
 * stall on the request thread for security reasons (e.g. constant-time
 * response padding in `AuthPasswordResetController`, see #477) stay
 * deterministically testable — the production [RealSleeper] runs the real
 * sleep, while tests inject a mock and verify the call without burning
 * wall-clock time.
 *
 * Negative or zero values are no-ops, matching `Thread.sleep`'s undefined
 * behaviour for negatives but guarded explicitly so callers can pass the
 * raw `MIN - elapsed` arithmetic without a guard at every call site.
 */
fun interface Sleeper {
    fun sleep(millis: Long)
}

/**
 * Default production [Sleeper] backed by `Thread.sleep`. The interruption
 * model is intentionally pass-through: an `InterruptedException` from a
 * request-thread shutdown propagates so Spring's request lifecycle can
 * cancel the request rather than swallowing the signal.
 */
@Component
class RealSleeper : Sleeper {
    override fun sleep(millis: Long) {
        if (millis > 0) Thread.sleep(millis)
    }
}
