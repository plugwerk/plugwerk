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

import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Renders an absolute expiry timestamp into the short human phrase used in
 * transactional emails: "in 30 minutes" for short windows, "in 12 hours" for
 * the same-day band, and an absolute UTC stamp for anything beyond 24 hours.
 *
 * Extracted in #450 to deduplicate the same logic that lived inline in
 * `AuthRegistrationController`, `AuthPasswordResetController`, and now also
 * `AdminPasswordResetService`. Operator-localisation lands with #436's i18n
 * iteration.
 */
internal object ExpiryFormatter {

    private val ABSOLUTE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm 'UTC'")

    fun formatHuman(expiresAt: OffsetDateTime, now: OffsetDateTime = OffsetDateTime.now()): String {
        val secondsLeft = Duration.between(now, expiresAt).seconds
        return when {
            secondsLeft <= 0 -> "now (link already expired)"
            secondsLeft < 3600 -> "in ${(secondsLeft / 60).coerceAtLeast(1)} minutes"
            secondsLeft < 86_400 -> "in ${(secondsLeft / 3600).coerceAtLeast(1)} hours"
            else -> "on ${expiresAt.format(ABSOLUTE_FMT)}"
        }
    }
}
