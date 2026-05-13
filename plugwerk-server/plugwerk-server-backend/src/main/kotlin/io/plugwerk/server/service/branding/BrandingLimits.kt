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
package io.plugwerk.server.service.branding

import io.plugwerk.server.domain.BrandingSlot

/**
 * Per-slot upload constraints (#254). Hard-coded so the operator
 * cannot accidentally allow a 50 MB hero image into the database — the
 * point of the slot model is exactly to keep the assets small enough
 * to live in PostgreSQL.
 *
 * The full-logo slots share their pixel envelope (4:1 aspect, max
 * 1600×400) — that fits the rendered top-bar / login / email widths
 * with comfortable headroom. The logomark uses the square envelope.
 */
data class BrandingLimits(
    val maxBytes: Long,
    val minWidth: Int,
    val minHeight: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    /** Allowed aspect ratio (`width / height`); raster uploads outside
     *  the tolerance below are rejected. */
    val aspectRatio: Double,
    val aspectTolerance: Double = 0.05,
) {
    companion object {
        private val FULL_LOGO = BrandingLimits(
            maxBytes = 512 * 1024,
            minWidth = 320,
            minHeight = 80,
            maxWidth = 1600,
            maxHeight = 400,
            aspectRatio = 4.0,
        )
        private val LOGOMARK = BrandingLimits(
            maxBytes = 256 * 1024,
            minWidth = 256,
            minHeight = 256,
            maxWidth = 1024,
            maxHeight = 1024,
            aspectRatio = 1.0,
        )

        fun forSlot(slot: BrandingSlot): BrandingLimits = when (slot) {
            BrandingSlot.LOGO_LIGHT, BrandingSlot.LOGO_DARK -> FULL_LOGO
            BrandingSlot.LOGOMARK -> LOGOMARK
        }
    }
}

val ALLOWED_CONTENT_TYPES: Set<String> = setOf(
    "image/svg+xml",
    "image/png",
    "image/webp",
)
