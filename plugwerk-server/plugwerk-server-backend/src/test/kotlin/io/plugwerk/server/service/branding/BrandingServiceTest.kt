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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class BrandingServiceTest {

    private lateinit var repository: ApplicationAssetRepository
    private lateinit var service: BrandingService

    @BeforeEach
    fun setUp() {
        repository = mock()
        whenever(repository.save(any<ApplicationAssetEntity>())).thenAnswer { it.arguments[0] }
        service = BrandingService(repository)
    }

    @Test
    fun `rejects unsupported content type`() {
        assertThatThrownBy {
            service.upload(BrandingSlot.LOGOMARK, "image/gif", ByteArray(10), null)
        }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("Unsupported content type")
    }

    @Test
    fun `rejects payload larger than the slot cap`() {
        val tooBig = ByteArray(BrandingLimits.forSlot(BrandingSlot.LOGOMARK).maxBytes.toInt() + 1)
        assertThatThrownBy {
            service.upload(BrandingSlot.LOGOMARK, "image/png", tooBig, null)
        }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("exceeds")
    }

    @Test
    fun `accepts a sanitised svg with a viewBox matching the slot ratio`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 200">
              <rect width="800" height="200" fill="black"/>
            </svg>
        """.trimIndent().toByteArray()

        val saved = service.upload(BrandingSlot.LOGO_LIGHT, "image/svg+xml", svg, null)

        assertThat(saved.slot).isEqualTo("logo_light")
        assertThat(saved.contentType).isEqualTo("image/svg+xml")
        assertThat(saved.sha256).hasSize(64)
        // The stored bytes are the sanitised form — must not contain forbidden content,
        // and must still parse as the same logical shape.
        assertThat(saved.content.decodeToString()).contains("<rect")
    }

    @Test
    fun `rejects svg whose viewBox aspect does not match the slot`() {
        // 2:1 instead of 4:1.
        val badSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 200">
              <rect width="400" height="200" fill="black"/>
            </svg>
        """.trimIndent().toByteArray()
        assertThatThrownBy {
            service.upload(BrandingSlot.LOGO_LIGHT, "image/svg+xml", badSvg, null)
        }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("aspect")
    }

    @Test
    fun `delete is idempotent when the slot has no row`() {
        whenever(repository.deleteBySlot(eq("logomark"))).thenReturn(0L)
        // Should not throw, even though nothing existed.
        service.delete(BrandingSlot.LOGOMARK)
    }

    @Test
    fun `find returns the row from the repository`() {
        val row = ApplicationAssetEntity(
            slot = "logomark",
            contentType = "image/png",
            content = ByteArray(8),
            sha256 = "aa".repeat(32),
            sizeBytes = 8,
        )
        whenever(repository.findBySlot("logomark")).thenReturn(Optional.of(row))
        assertThat(service.find(BrandingSlot.LOGOMARK)).hasValue(row)
    }

    @Test
    fun `upload replaces the previous row in one transaction`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 200">
              <rect width="800" height="200" fill="black"/>
            </svg>
        """.trimIndent().toByteArray()

        service.upload(BrandingSlot.LOGO_LIGHT, "image/svg+xml", svg, null)

        // The delete-then-flush-then-save sequence happens regardless of
        // whether an old row existed, so the second upload follows the
        // same path — verify by triggering it.
        service.upload(BrandingSlot.LOGO_LIGHT, "image/svg+xml", svg, null)
        // Repository mock recorded two saves and two deleteBySlot calls.
        verify(repository, times(2)).deleteBySlot("logo_light")
        verify(repository, times(2)).save(any<ApplicationAssetEntity>())
    }
}
