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

import io.plugwerk.server.domain.ApplicationAssetEntity
import io.plugwerk.server.domain.BrandingSlot
import io.plugwerk.server.repository.ApplicationAssetRepository
import io.plugwerk.server.service.ConflictException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.Optional
import java.util.UUID
import kotlin.math.abs

/**
 * Branding-asset upload / fetch / delete (#254). Single source of
 * truth for the three-slot model — endpoints stay thin and only
 * concern themselves with the HTTP envelope.
 */
@Service
class BrandingService(
    private val repository: ApplicationAssetRepository,
    private val sanitizer: SvgSanitizer = SvgSanitizer(),
) {
    private val log = LoggerFactory.getLogger(BrandingService::class.java)

    /**
     * Persist a fresh asset for [slot]. Replaces any existing row for
     * the same slot in a single transaction so the GET endpoint can
     * never observe a half-state. Throws [ConflictException] when the
     * payload fails any per-slot rule.
     */
    @Transactional
    fun upload(
        slot: BrandingSlot,
        contentType: String,
        rawBytes: ByteArray,
        uploadedBy: UUID?,
    ): ApplicationAssetEntity {
        validateContentType(contentType)
        val limits = BrandingLimits.forSlot(slot)
        if (rawBytes.size.toLong() > limits.maxBytes) {
            throw ConflictException(
                "Asset is ${rawBytes.size} bytes, exceeds the ${limits.maxBytes}-byte cap for ${slot.urlValue}",
            )
        }

        val storedBytes = if (contentType == "image/svg+xml") {
            try {
                sanitizer.sanitize(rawBytes)
            } catch (ex: SvgSanitizationException) {
                throw ConflictException("SVG rejected: ${ex.message}")
            }
        } else {
            rawBytes
        }

        validateDimensions(slot, contentType, storedBytes, limits)

        // Delete-then-insert in the same TX keeps the UNIQUE(slot)
        // constraint honoured without resorting to native upsert SQL.
        repository.deleteBySlot(slot.dbValue)
        // Flush the delete before inserting to avoid Hibernate batching
        // it after the save — the unique constraint would then trip on
        // the still-present old row.
        repository.flush()
        val entity = ApplicationAssetEntity(
            slot = slot.dbValue,
            contentType = contentType,
            content = storedBytes,
            sha256 = sha256Hex(storedBytes),
            sizeBytes = storedBytes.size.toLong(),
            uploadedBy = uploadedBy,
        )
        val saved = repository.save(entity)
        log.info(
            "branding upload slot={} bytes={} sha256={} uploadedBy={}",
            slot.dbValue,
            saved.sizeBytes,
            saved.sha256,
            uploadedBy,
        )
        return saved
    }

    @Transactional
    fun delete(slot: BrandingSlot) {
        val removed = repository.deleteBySlot(slot.dbValue)
        if (removed > 0) {
            log.info("branding reset slot={} (custom row removed)", slot.dbValue)
        }
    }

    @Transactional(readOnly = true)
    fun find(slot: BrandingSlot): Optional<ApplicationAssetEntity> = repository.findBySlot(slot.dbValue)

    private fun validateContentType(contentType: String) {
        if (contentType !in ALLOWED_CONTENT_TYPES) {
            throw ConflictException(
                "Unsupported content type '$contentType'. Allowed: $ALLOWED_CONTENT_TYPES",
            )
        }
    }

    private fun validateDimensions(slot: BrandingSlot, contentType: String, bytes: ByteArray, limits: BrandingLimits) {
        val dims = ImageDimensionsReader.read(contentType, bytes)
            ?: return // Dimension reader could not parse — skip; size cap stays.

        when (contentType) {
            "image/svg+xml" -> {
                // SVGs are vector — only enforce aspect, the pixel
                // envelope governs raster.
                val ratio = dims.width.toDouble() / dims.height
                if (abs(ratio - limits.aspectRatio) > limits.aspectTolerance * limits.aspectRatio) {
                    throw ConflictException(
                        "SVG aspect ratio ${"%.2f".format(ratio)} is outside the " +
                            "expected ${limits.aspectRatio}±${limits.aspectTolerance * 100}% for ${slot.urlValue}",
                    )
                }
            }

            else -> {
                if (dims.width < limits.minWidth || dims.height < limits.minHeight) {
                    throw ConflictException(
                        "Asset ${dims.width}x${dims.height} is smaller than the minimum " +
                            "${limits.minWidth}x${limits.minHeight} for ${slot.urlValue}",
                    )
                }
                if (dims.width > limits.maxWidth || dims.height > limits.maxHeight) {
                    throw ConflictException(
                        "Asset ${dims.width}x${dims.height} exceeds the maximum " +
                            "${limits.maxWidth}x${limits.maxHeight} for ${slot.urlValue}",
                    )
                }
                val ratio = dims.width.toDouble() / dims.height
                if (abs(ratio - limits.aspectRatio) > limits.aspectTolerance * limits.aspectRatio) {
                    throw ConflictException(
                        "Asset aspect ratio ${"%.2f".format(ratio)} is outside the " +
                            "expected ${limits.aspectRatio}±${limits.aspectTolerance * 100}% for ${slot.urlValue}",
                    )
                }
            }
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { append("%02x".format(it)) }
        }
    }
}
