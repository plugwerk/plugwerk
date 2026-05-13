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
package io.plugwerk.server.controller

import io.plugwerk.api.AdminBrandingApi
import io.plugwerk.api.model.BrandingAssetDto
import io.plugwerk.server.domain.BrandingSlot
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.ConflictException
import io.plugwerk.server.service.branding.BrandingService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.ZoneOffset
import java.util.UUID

/**
 * Admin branding endpoints (#254). Upload accepts SVG / PNG / WebP,
 * runs per-slot validation, sanitises SVGs. Reset is idempotent.
 * Both are superadmin-only.
 */
@RestController
@RequestMapping("/api/v1")
class AdminBrandingController(
    private val brandingService: BrandingService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AdminBrandingApi {

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun uploadBrandingAsset(slot: String, file: MultipartFile): ResponseEntity<BrandingAssetDto> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        val parsedSlot = BrandingSlot.fromUrl(slot)
            ?: throw ConflictException("Unknown branding slot '$slot'")
        val contentType = file.contentType
            ?: throw ConflictException("Upload is missing a Content-Type header")
        val saved = brandingService.upload(
            slot = parsedSlot,
            contentType = contentType,
            rawBytes = file.bytes,
            uploadedBy = (auth.principal as? Jwt)?.subject?.let(::safeUuid),
        )
        return ResponseEntity.ok(
            BrandingAssetDto(
                slot = BrandingAssetDto.Slot.entries.first { it.value == parsedSlot.urlValue },
                contentType = saved.contentType,
                sizeBytes = saved.sizeBytes,
                sha256 = saved.sha256,
                uploadedAt = saved.uploadedAt.atZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
            ),
        )
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun resetBrandingAsset(slot: String): ResponseEntity<Unit> {
        namespaceAuthorizationService.requireSuperadmin(currentAuthentication())
        val parsedSlot = BrandingSlot.fromUrl(slot)
            ?: throw ConflictException("Unknown branding slot '$slot'")
        brandingService.delete(parsedSlot)
        return ResponseEntity.noContent().build()
    }

    private fun safeUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
