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
package io.plugwerk.server.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Three slots that the branding system supports (#254). Stable IDs are
 * the kebab-case variants used in URLs (`/api/v1/branding/{slot}`) and
 * the snake_case variants used as DB values, both expressed by the
 * enum's [dbValue].
 */
enum class BrandingSlot(val dbValue: String, val urlValue: String) {
    LOGO_LIGHT("logo_light", "logo-light"),
    LOGO_DARK("logo_dark", "logo-dark"),
    LOGOMARK("logomark", "logomark"),
    ;

    companion object {
        fun fromUrl(value: String): BrandingSlot? = entries.firstOrNull { it.urlValue == value }
    }
}

/**
 * One row per branding slot (`logo_light`, `logo_dark`, `logomark`).
 *
 * The bytes live in the same database as the rest of the application
 * configuration so a DB backup is a complete restore — see ADR-0037
 * for why the existing `ArtifactStorageService` was rejected for this
 * use case (it would invite the orphan reaper to delete the assets).
 */
@Entity
@Table(name = "application_asset")
class ApplicationAssetEntity(
    @Id
    @Column(name = "id", updatable = false)
    var id: UUID = UUID.randomUUID(),

    /** Stored as the snake_case [BrandingSlot.dbValue]. */
    @Column(name = "slot", nullable = false, unique = true, length = 32)
    var slot: String,

    @Column(name = "content_type", nullable = false, length = 64)
    var contentType: String,

    @Column(name = "content", nullable = false)
    var content: ByteArray,

    /**
     * Hex-encoded SHA-256 of [content]. Used as the HTTP ETag and as
     * the cache-busting query string from the frontend so an asset
     * change reflects immediately even with `Cache-Control: immutable`.
     */
    @Column(name = "sha256", nullable = false, length = 64)
    var sha256: String,

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,

    @Column(name = "uploaded_at", nullable = false)
    var uploadedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "uploaded_by")
    var uploadedBy: UUID? = null,
)
