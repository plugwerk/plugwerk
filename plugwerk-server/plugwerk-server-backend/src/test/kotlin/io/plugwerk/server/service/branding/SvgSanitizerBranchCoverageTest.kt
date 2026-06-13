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
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Branch-coverage tests for [SvgSanitizer] (#254). Drives the decision arms of
 * `sanitize`/`cleanElement`/`cleanAttributes`/`isSafeHref`:
 *  - parse failure and non-`<svg>` root (both throw [SvgSanitizationException]),
 *  - forbidden element vs `<style>` vs recurse-into-child element arms,
 *  - comment / processing-instruction removal vs text-node retention,
 *  - `on*` event-handler stripping,
 *  - `href` / `:href` safe (`#…`, `data:…`, empty) vs unsafe (`javascript:`,
 *    `https://…`) arms.
 */
class SvgSanitizerBranchCoverageTest {

    private val sanitizer = SvgSanitizer()

    private val svgNs = "http://www.w3.org/2000/svg"

    private fun sanitizeToString(svg: String): String = String(sanitizer.sanitize(svg.toByteArray()))

    // -- parse / root arms ---------------------------------------------------

    @Test
    fun `unparseable input throws SvgSanitizationException`() {
        val ex = assertFailsWith<SvgSanitizationException> {
            sanitizer.sanitize("this <is> not >>> valid xml".toByteArray())
        }
        assertThat(ex.message).contains("not parseable")
    }

    @Test
    fun `a non-svg root element is rejected`() {
        val ex = assertFailsWith<SvgSanitizationException> {
            sanitizer.sanitize("<html><body/></html>".toByteArray())
        }
        assertThat(ex.message).contains("Root element must be <svg>")
    }

    @Test
    fun `a clean svg passes through and keeps allowed geometry`() {
        val out = sanitizeToString("""<svg xmlns="$svgNs"><rect width="10" height="10"/></svg>""")

        assertThat(out).contains("rect")
    }

    // -- cleanElement arms ---------------------------------------------------

    @Test
    fun `a forbidden element (script) is stripped`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><rect/><script>alert(1)</script></svg>""",
        )

        assertThat(out).contains("rect")
        assertThat(out).doesNotContain("script")
        assertThat(out).doesNotContain("alert")
    }

    @Test
    fun `a forbidden element (foreignObject) is stripped`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><foreignObject><div/></foreignObject><rect/></svg>""",
        )

        assertThat(out).doesNotContain("foreignObject")
        assertThat(out).contains("rect")
    }

    @Test
    fun `an inline style element is stripped`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><style>* { fill: url(javascript:alert(1)) }</style><rect/></svg>""",
        )

        assertThat(out).doesNotContain("style")
        assertThat(out).doesNotContain("javascript")
    }

    @Test
    fun `nested allowed elements are recursed into and their handlers stripped`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><g><rect onclick="evil()"/></g></svg>""",
        )

        assertThat(out).contains("rect")
        assertThat(out).doesNotContain("onclick")
    }

    @Test
    fun `comments and processing instructions are removed but text is kept`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><!-- secret --><?php echo 1?><text>Logo</text></svg>""",
        )

        assertThat(out).doesNotContain("secret")
        assertThat(out).doesNotContain("php")
        assertThat(out).contains("Logo")
    }

    // -- cleanAttributes / isSafeHref arms -----------------------------------

    @Test
    fun `on-star event handler attributes are stripped`() {
        val out = sanitizeToString("""<svg xmlns="$svgNs" onload="evil()"><rect/></svg>""")

        assertThat(out).doesNotContain("onload")
    }

    @Test
    fun `an unsafe javascript href is dropped`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><a href="javascript:alert(1)"><rect/></a></svg>""",
        )

        assertThat(out).doesNotContain("javascript")
    }

    @Test
    fun `an unsafe remote href is dropped`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><a href="https://evil.example.com/x"><rect/></a></svg>""",
        )

        assertThat(out).doesNotContain("evil.example.com")
    }

    @Test
    fun `a safe fragment href is preserved`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><use href="#icon"/></svg>""",
        )

        assertThat(out).contains("#icon")
    }

    @Test
    fun `a safe data uri href is preserved`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><image href="data:image/png;base64,AAAA"/></svg>""",
        )

        assertThat(out).contains("data:image/png")
    }

    @Test
    fun `an empty href is treated as safe and preserved`() {
        val out = sanitizeToString(
            """<svg xmlns="$svgNs"><a href=""><rect/></a></svg>""",
        )

        // The element survives; the empty href is not stripped (isSafeHref == true).
        assertThat(out).contains("rect")
    }
}
