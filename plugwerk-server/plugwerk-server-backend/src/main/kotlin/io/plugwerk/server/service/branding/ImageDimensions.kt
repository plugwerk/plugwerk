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

import javax.imageio.ImageIO
import kotlin.io.path.Path

data class ImageDimensions(val width: Int, val height: Int)

/**
 * Reads pixel dimensions from PNG / WebP / SVG bytes for the per-slot
 * upload validation (#254).
 *
 * - PNG and WebP go through `ImageIO`. WebP support depends on the
 *   `imageio-webp` plugin shipped with the JDK image stack; if no
 *   reader is registered we return `null` and the validator can
 *   accept the file at face value (size cap still applies) — losing
 *   dimension validation for WebP is preferable to rejecting valid
 *   WebPs because of a JDK quirk.
 * - SVG is parsed for the `<svg viewBox>` or `<svg width height>`
 *   attributes. SVGs can be infinitely scaled, so the dimensions
 *   here are advisory; we use them to enforce the aspect ratio, not
 *   the absolute pixel envelope.
 */
object ImageDimensionsReader {

    fun read(contentType: String, bytes: ByteArray): ImageDimensions? = when (contentType) {
        "image/svg+xml" -> readSvgDimensions(bytes)
        "image/png", "image/webp" -> readRasterDimensions(bytes)
        else -> null
    }

    private fun readRasterDimensions(bytes: ByteArray): ImageDimensions? {
        return try {
            val image = ImageIO.read(bytes.inputStream()) ?: return null
            ImageDimensions(image.width, image.height)
        } catch (_: Exception) {
            null
        }
    }

    private fun readSvgDimensions(bytes: ByteArray): ImageDimensions? {
        // Pull the dimensions out of the svg root attributes without a
        // full DOM parse — the sanitiser already DOM-parses for the
        // structural pass and we want this method to run before that
        // (or instead of, for trusted code paths).
        val text = bytes.decodeToString(throwOnInvalidSequence = false)
        val rootMatch = Regex("<\\s*svg\\b[^>]*>", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        val rootAttrs = rootMatch.value
        val viewBox = Regex("viewBox\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(rootAttrs)?.groupValues?.getOrNull(1)
        if (viewBox != null) {
            val parts = viewBox.trim().split(Regex("[\\s,]+"))
            if (parts.size == 4) {
                val w = parts[2].toDoubleOrNull()
                val h = parts[3].toDoubleOrNull()
                if (w != null && h != null && w > 0 && h > 0) {
                    return ImageDimensions(w.toInt(), h.toInt())
                }
            }
        }
        val width = Regex("\\bwidth\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(rootAttrs)?.groupValues?.getOrNull(1)?.let(::stripUnit)
        val height = Regex("\\bheight\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(rootAttrs)?.groupValues?.getOrNull(1)?.let(::stripUnit)
        if (width != null && height != null && width > 0 && height > 0) {
            return ImageDimensions(width.toInt(), height.toInt())
        }
        return null
    }

    private fun stripUnit(value: String): Double? {
        val numeric = value.trim().replace(Regex("[a-zA-Z%]+$"), "")
        return numeric.toDoubleOrNull()
    }

    /** Used in tests as a clarity helper. */
    @Suppress("unused")
    fun pathHint(): String = Path("not-used").toString()
}
