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
package io.plugwerk.server.service.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExpiryFormatterTest {

    private val now: OffsetDateTime = OffsetDateTime.of(2026, 5, 9, 12, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `expired window returns explicit expired phrase`() {
        val past = now.minusMinutes(5)
        assertThat(ExpiryFormatter.formatHuman(past, now = now)).isEqualTo("now (link already expired)")
    }

    @Test
    fun `sub-hour window returns minutes phrase rounded down with floor of one minute`() {
        // 90 seconds → "in 1 minutes" (the coerceAtLeast(1) floor)
        val ninetySeconds = now.plusSeconds(90)
        assertThat(ExpiryFormatter.formatHuman(ninetySeconds, now = now)).isEqualTo("in 1 minutes")

        // 30 minutes
        val thirtyMin = now.plusMinutes(30)
        assertThat(ExpiryFormatter.formatHuman(thirtyMin, now = now)).isEqualTo("in 30 minutes")
    }

    @Test
    fun `sub-day window returns hours phrase`() {
        val twelveHours = now.plusHours(12)
        assertThat(ExpiryFormatter.formatHuman(twelveHours, now = now)).isEqualTo("in 12 hours")
    }

    @Test
    fun `multi-day window returns absolute UTC timestamp`() {
        val twoDays = now.plusDays(2)
        assertThat(ExpiryFormatter.formatHuman(twoDays, now = now))
            .isEqualTo("on 11 May 2026 at 12:00 UTC")
    }
}
