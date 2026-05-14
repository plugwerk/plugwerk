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

import io.plugwerk.server.domain.ApplicationAssetEntity
import io.plugwerk.server.domain.BrandingSlot
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PasswordResetRateLimitFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.security.RegisterRateLimitFilter
import io.plugwerk.server.service.branding.BrandingService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Optional

/**
 * Cache-header regression guard for #530. `BrandingController` previously
 * advertised `immutable, max-age=1y`, which made browsers serve stale
 * bytes from disk cache after the operator deleted the slot. We now use
 * `no-cache` + ETag — the browser may keep the bytes but MUST revalidate.
 */
@WebMvcTest(
    BrandingController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ChangePasswordRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RegisterRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordResetRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RefreshRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class BrandingControllerTest {

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @MockitoBean lateinit var brandingService: BrandingService

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `serve returns Cache-Control no-cache plus ETag on 200 (#530)`() {
        val asset = ApplicationAssetEntity(
            slot = "logomark",
            contentType = "image/png",
            content = ByteArray(8) { it.toByte() },
            sha256 = "a".repeat(64),
            sizeBytes = 8,
        )
        whenever(brandingService.find(BrandingSlot.LOGOMARK)).thenReturn(Optional.of(asset))

        mockMvc.get("/api/v1/branding/logomark")
            .andExpect {
                status { isOk() }
                header { string("Cache-Control", "no-cache") }
                header { string("ETag", "\"${asset.sha256}\"") }
                header { string("Content-Length", "8") }
            }
    }

    @Test
    fun `serve returns 404 with no-store when slot is empty (#530)`() {
        whenever(brandingService.find(BrandingSlot.LOGOMARK)).thenReturn(Optional.empty())

        mockMvc.get("/api/v1/branding/logomark")
            .andExpect {
                status { isNotFound() }
                header { string("Cache-Control", "no-store") }
            }
    }
}
