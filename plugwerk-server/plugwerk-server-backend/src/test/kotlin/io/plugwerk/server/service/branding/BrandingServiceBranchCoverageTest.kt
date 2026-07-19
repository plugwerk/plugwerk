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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

class BrandingServiceBranchCoverageTest {

    private lateinit var repository: ApplicationAssetRepository
    private lateinit var service: BrandingService

    @BeforeEach
    fun setUp() {
        repository = mock()
        whenever(repository.save(any<ApplicationAssetEntity>())).thenAnswer { it.arguments[0] }
        service = BrandingService(repository)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    // -- SVG sanitiser failure branch ----------------------------------------

    @Test
    fun `upload wraps a sanitiser failure as a ConflictException`() {
        // Not valid XML → SvgSanitizer throws SvgSanitizationException, caught
        // and re-thrown as ConflictException("SVG rejected: ...").
        val notXml = "this is not <svg".toByteArray()
        assertThatThrownBy {
            service.upload(BrandingSlot.LOGO_LIGHT, "image/svg+xml", notXml, null)
        }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("SVG rejected")
    }

    // -- raster (non-SVG) storedBytes branch + dimension validation ----------

    @Test
    fun `upload accepts a well-formed png that satisfies the slot envelope`() {
        // LOGOMARK: 1:1, min 256, max 1024. 512x512 passes every raster check.
        val saved = service.upload(BrandingSlot.LOGOMARK, "image/png", png(512, 512), UUID.randomUUID())

        assertThat(saved.slot).isEqualTo("logomark")
        assertThat(saved.contentType).isEqualTo("image/png")
        // Non-SVG path stores rawBytes verbatim (no sanitiser).
        assertThat(saved.sizeBytes).isEqualTo(png(512, 512).size.toLong())
        verify(repository).deleteBySlot("logomark")
        verify(repository).flush()
    }

    @Test
    fun `upload rejects a png smaller than the slot minimum`() {
        // 64x64 is below LOGOMARK min 256x256.
        assertThatThrownBy {
            service.upload(BrandingSlot.LOGOMARK, "image/png", png(64, 64), null)
        }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("smaller than the minimum")
        verify(repository, never()).save(any())
    }

    @Test
    fun `upload rejects a png larger than the slot maximum`() {
        // 2000x2000 exceeds LOGOMARK max 1024x1024 (still under the 256 KB cap
        // because a blank ARGB PNG compresses to a few KB).
        assertThatThrownBy {
            service.upload(BrandingSlot.LOGOMARK, "image/png", png(2000, 2000), null)
        }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("exceeds the maximum")
    }

    @Test
    fun `upload rejects a png whose aspect ratio is outside the slot tolerance`() {
        // 512x256 = 2:1, inside the LOGOMARK pixel envelope but far from 1:1.
        assertThatThrownBy {
            service.upload(BrandingSlot.LOGOMARK, "image/png", png(512, 256), null)
        }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("aspect ratio")
    }

    @Test
    fun `upload accepts a wide png for the full-logo 4 to 1 slot`() {
        // LOGO_DARK uses FULL_LOGO: 4:1, min 320x80, max 1600x400. 800x200 fits.
        val saved = service.upload(BrandingSlot.LOGO_DARK, "image/png", png(800, 200), null)

        assertThat(saved.slot).isEqualTo("logo_dark")
    }

    @Test
    fun `upload accepts a png whose dimensions cannot be parsed and relies on the size cap`() {
        // Bytes that are not a decodable image → ImageDimensionsReader.read
        // returns null → validateDimensions early-returns, no dimension checks.
        val unparseable = ByteArray(64) { 0x7 }
        val saved = service.upload(BrandingSlot.LOGOMARK, "image/png", unparseable, null)

        assertThat(saved.contentType).isEqualTo("image/png")
        assertThat(saved.sizeBytes).isEqualTo(64)
    }

    // -- delete (removed > 0 branch) -----------------------------------------

    @Test
    fun `delete logs and completes when a custom row was actually removed`() {
        whenever(repository.deleteBySlot(eq("logomark"))).thenReturn(1L)

        // removed > 0 → the info-log branch; method still returns Unit cleanly.
        service.delete(BrandingSlot.LOGOMARK)

        verify(repository).deleteBySlot("logomark")
    }
}
