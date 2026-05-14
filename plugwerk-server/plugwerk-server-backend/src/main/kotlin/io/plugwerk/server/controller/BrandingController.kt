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

import io.plugwerk.server.domain.BrandingSlot
import io.plugwerk.server.service.branding.BrandingService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public read-only endpoint that serves the bytes of an
 * operator-uploaded branding asset (#254). When the slot is at its
 * default the endpoint returns 404 and the frontend falls back to the
 * bundled SVG.
 *
 * `Cache-Control: no-cache` paired with a SHA-256 ETag means the
 * browser may cache the bytes but MUST revalidate via
 * `If-None-Match` on every request. Unchanged slots get a cheap
 * `304 Not Modified`; deleted slots get a fresh 404 instead of a
 * stale cached 200 (#530).
 *
 * Implemented directly with `@GetMapping` rather than implementing
 * the OpenAPI-generated interface because the contract returns
 * `ResponseEntity<Unit>` and we need a real byte payload.
 */
@RestController
@RequestMapping("/api/v1")
class BrandingController(private val brandingService: BrandingService) {

    @GetMapping("/branding/{slot}")
    fun serve(@PathVariable slot: String): ResponseEntity<ByteArray> {
        val parsedSlot = BrandingSlot.fromUrl(slot)
            ?: return notFound()
        val asset = brandingService.find(parsedSlot).orElse(null)
            ?: return notFound()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(asset.contentType))
            .eTag("\"${asset.sha256}\"")
            .cacheControl(CacheControl.noCache())
            .header("Content-Length", asset.sizeBytes.toString())
            .body(asset.content)
    }

    /**
     * 404 must explicitly forbid caching. Otherwise a browser that
     * recorded the "no asset yet" 404 keeps serving it from cache
     * after the operator uploads, breaking the dashboard's "see the
     * new logo immediately" UX.
     */
    private fun notFound(): ResponseEntity<ByteArray> = ResponseEntity
        .status(404)
        .cacheControl(CacheControl.noStore())
        .build()
}
