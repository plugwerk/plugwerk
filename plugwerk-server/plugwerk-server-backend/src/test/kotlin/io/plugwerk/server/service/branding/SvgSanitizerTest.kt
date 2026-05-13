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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SvgSanitizerTest {

    private val sanitizer = SvgSanitizer()

    @Test
    fun `strips inline script element`() {
        val input = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <script>alert('xss')</script>
              <circle cx="50" cy="50" r="40"/>
            </svg>
        """.trimIndent()
        val out = sanitizer.sanitize(input.toByteArray()).decodeToString()
        assertThat(out).doesNotContain("script")
        assertThat(out).doesNotContain("alert")
        assertThat(out).contains("<circle")
    }

    @Test
    fun `strips on event-handler attributes`() {
        val input = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" onclick="alert('xss')" onmouseover="evil()"/>
            </svg>
        """.trimIndent()
        val out = sanitizer.sanitize(input.toByteArray()).decodeToString()
        assertThat(out).doesNotContain("onclick")
        assertThat(out).doesNotContain("onmouseover")
        assertThat(out).contains("<circle")
    }

    @Test
    fun `strips foreignObject containers`() {
        val input = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <foreignObject><body><script>x()</script></body></foreignObject>
            </svg>
        """.trimIndent()
        val out = sanitizer.sanitize(input.toByteArray()).decodeToString()
        assertThat(out.lowercase()).doesNotContain("foreignobject")
    }

    @Test
    fun `rejects javascript hrefs but keeps internal references`() {
        val input = """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 100 100">
              <a xlink:href="javascript:alert(1)"><circle r="10"/></a>
              <use xlink:href="#myGlyph"/>
            </svg>
        """.trimIndent()
        val out = sanitizer.sanitize(input.toByteArray()).decodeToString()
        assertThat(out).doesNotContain("javascript:")
        assertThat(out).contains("#myGlyph")
    }

    @Test
    fun `strips inline style elements`() {
        val input = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <style>circle { fill: url('javascript:alert(1)'); }</style>
              <circle cx="50" cy="50" r="40"/>
            </svg>
        """.trimIndent()
        val out = sanitizer.sanitize(input.toByteArray()).decodeToString()
        assertThat(out).doesNotContain("javascript:")
        assertThat(out).doesNotContain("<style")
    }

    @Test
    fun `rejects input with a non-svg root`() {
        val input = "<html><body>not svg</body></html>"
        assertThatThrownBy { sanitizer.sanitize(input.toByteArray()) }
            .isInstanceOf(SvgSanitizationException::class.java)
    }

    @Test
    fun `rejects DOCTYPE declarations to prevent XXE`() {
        val input = """
            <?xml version="1.0"?>
            <!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">&xxe;</svg>
        """.trimIndent()
        assertThatThrownBy { sanitizer.sanitize(input.toByteArray()) }
            .isInstanceOf(SvgSanitizationException::class.java)
    }
}
