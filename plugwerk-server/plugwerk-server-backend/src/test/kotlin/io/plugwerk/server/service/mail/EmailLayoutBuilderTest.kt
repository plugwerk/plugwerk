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
package io.plugwerk.server.service.mail

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-Kotlin unit tests for [EmailLayoutBuilder] (#449). Exercises the
 * editorial-minimal Carbon-Blue chrome the builder wraps around content,
 * and the anti-template guarantees the layout makes (no logo image, no
 * gradients, single CTA, etc.).
 *
 * No Spring context: the builder has zero collaborators, so a plain JUnit
 * test runs in milliseconds.
 */
class EmailLayoutBuilderTest {

    private val sampleContent = "<p>Hello {{username}},</p><p>Sample body.</p>"

    @Test
    fun `wraps content in 600px max-width card centred on a soft-grey page`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        // Page background is the Carbon gray10 token.
        assertThat(out).contains("background-color:#F4F4F4")
        // 600 px is the conventional transactional-email width that fits
        // every desktop client preview pane without horizontal scroll.
        assertThat(out).contains("width=\"600\"")
        assertThat(out).contains("max-width:600px")
    }

    @Test
    fun `renders the Plugwerk wordmark as text — no img tag, no logo asset`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        assertThat(out).contains(">Plugwerk<")
        assertThat(out).contains("class=\"pw-wordmark\"")
        // Anti-template guarantee: zero hosted assets means zero CDN
        // dependency, zero broken-image-icon failure mode in clients that
        // block remote images by default.
        assertThat(out).doesNotContain("<img")
    }

    @Test
    fun `renders a 2px Carbon-blue accent line under the wordmark`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        // The wordmark accent is a row with explicit height and the primary
        // colour as background. The full string fragment pins both at once.
        assertThat(out).contains("height:2px")
        assertThat(out).contains("background-color:#0F62FE")
    }

    @Test
    fun `renders a 4px Carbon-blue accent bar down the card's left edge`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        // The accent bar is the one editorial gesture in the layout — a 4 px
        // primary-colour column flush to the card's left edge. Presence of
        // the class plus the width attribute is the contract.
        assertThat(out).contains("class=\"pw-accent\"")
        assertThat(out).contains("width=\"4\"")
    }

    @Test
    fun `renders the bullet-proof CTA button as a nested table when ctaUrl and ctaText are provided`() {
        val out = EmailLayoutBuilder.wrap(
            sampleContent,
            ctaUrl = "https://example.test/verify?token=abc",
            ctaText = "Verify email",
            footerLine2 = "Footer.",
        )

        // role="presentation" + cellspacing/cellpadding=0 + border=0 is the
        // canonical bullet-proof-button signature: Outlook's Word renderer
        // paints the bgcolor cell without inheriting cell defaults.
        assertThat(out).contains("role=\"presentation\"")
        assertThat(out).contains("bgcolor=\"#0F62FE\"")
        // The anchor itself is display:inline-block so the click region
        // covers the padded box, not just the glyph row.
        assertThat(out).contains("display:inline-block")
        assertThat(out).contains(">Verify email<")
        assertThat(out).contains("href=\"https://example.test/verify?token=abc\"")
    }

    @Test
    fun `omits CTA block entirely when ctaUrl is null`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        // The pw-cta class only renders inside the CTA block; absent ⇒ omitted.
        assertThat(out).doesNotContain("pw-cta")
    }

    @Test
    fun `renders the dark-mode media query block in the head`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        // The dark-mode block is the only stylesheet entry in the document,
        // intentionally — every other rule is inlined for Outlook desktop
        // compatibility. Apple Mail / iOS / Outlook 365 honour @media here.
        assertThat(out).contains("@media (prefers-color-scheme: dark)")
        assertThat(out).contains("background-color: #1E1E1E !important")
        assertThat(out).contains("background-color: #262626 !important")
        assertThat(out).contains("color: #FFFFFF !important")
    }

    @Test
    fun `footer carries siteName as a Mustache placeholder, not a literal`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Custom footer.")

        // The builder must NOT pre-render Mustache — siteName is supplied
        // by the caller's vars map at MailTemplateService.render time.
        assertThat(out).contains("Sent by Plugwerk &middot; {{siteName}}")
        assertThat(out).contains("Custom footer.")
    }

    @Test
    fun `body text colour is the Carbon gray100 token in light mode`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        // gray100 (#161616) is the high-contrast body-text token. Anything
        // else here would collapse the 16:1 contrast ratio against the
        // white card.
        assertThat(out).contains("color:#161616")
    }

    @Test
    fun `does not contain any gradient or background-image — editorial discipline`() {
        val out = EmailLayoutBuilder.wrap(sampleContent, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        // Editorial-minimal direction. Any future change that introduces a
        // gradient or hosted image needs a deliberate test update — and a
        // good reason in the PR description.
        assertThat(out).doesNotContain("linear-gradient")
        assertThat(out).doesNotContain("radial-gradient")
        assertThat(out).doesNotContain("background-image")
    }

    @Test
    fun `passes content through verbatim including Mustache variables`() {
        val content = "<p>Hi {{username}}, click {{verificationLink}}.</p>"
        val out = EmailLayoutBuilder.wrap(content, ctaUrl = null, ctaText = null, footerLine2 = "Footer.")

        // The builder is purely structural — Mustache rendering happens
        // downstream, so the placeholders must survive untouched.
        assertThat(out).contains("{{username}}")
        assertThat(out).contains("{{verificationLink}}")
    }
}
