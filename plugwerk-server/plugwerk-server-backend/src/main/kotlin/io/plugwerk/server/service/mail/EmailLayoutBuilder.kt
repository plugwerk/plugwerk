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

/**
 * Builds the editorial-minimal Carbon-Blue layout used by all transactional
 * Plugwerk emails (#449).
 *
 * **Direction.** One container, one CTA, one accent gesture. The visual point
 * of view is "publication, not advertisement": typography-first, generous
 * whitespace, a single 4 px Carbon-Primary accent line down the card's left
 * edge. No gradients, no illustrations, no logo image — the wordmark is set
 * type, not a hosted asset, so the email needs zero CDN reachability and
 * scales perfectly on every retina display.
 *
 * **Why a Kotlin builder, not a Mustache partial.** Layout is a brand asset
 * that changes every two or three years; content is admin-editable per
 * template row. Pulling layout into a partial would force jmustache's loader
 * machinery, doubling the render path with admin-controllable strings — for
 * a value the operator should not casually rewrite. If a future iteration
 * actually needs editable layout, this builder's output IS a partial body —
 * lift it, register it via `Mustache.Compiler.withLoader`, and the call
 * sites stay identical. See [MailTemplate]'s migration discussion in #449.
 *
 * **Token mapping.** Email CSS cannot import the frontend `tokens.ts` at
 * build time, so the literals below reproduce the design system. The table
 * keeps a future brand refresh anchored:
 *
 * | CSS literal           | tokens.ts key            | role                     |
 * |-----------------------|--------------------------|--------------------------|
 * | `#0F62FE`             | `color.primary`          | accent, CTA, hyperlink   |
 * | `#F4F4F4`             | `color.gray10`           | page background (light)  |
 * | `#FFFFFF`             | `color.white`            | card background (light)  |
 * | `#E0E0E0`             | `color.gray20`           | card border (light)      |
 * | `#161616`             | `color.gray100`          | body text (light)        |
 * | `#6F6F6F`             | `color.gray60`           | footer text (light)      |
 * | `#1E1E1E`             | (email-only)             | page background (dark)   |
 * | `#262626`             | (email-only)             | card background (dark)   |
 * | `#393939`             | `color.gray80`           | card border (dark)       |
 * | `#A8A8A8`             | `color.gray40`           | footer text (dark)       |
 * | `8 px`                | `radius.card`            | card corner              |
 * | `6 px`                | `radius.btn`             | CTA corner               |
 * | `32 px`               | `space.7`                | card inner padding       |
 * | `0 1px 3px rgba…`     | `shadow.card`            | card lift (light only)   |
 *
 * **Output is itself a Mustache template.** The builder does not pre-render
 * Mustache placeholders — `contentHtml` carries `{{username}}`,
 * `{{verificationLink}}` etc. and the footer carries `{{siteName}}`. Both
 * are rendered downstream by `MailTemplateService` against the same `vars`
 * map a caller passes to `MailService.sendMailFromTemplate`. Strict-mode
 * enforcement therefore extends across layout + content uniformly: a
 * missing `{{siteName}}` from a caller crashes the render, which is the
 * desired behaviour for transactional mails.
 *
 * **Outlook desktop posture.** The Word renderer does not understand
 * `box-shadow`, modern selectors, flexbox or grid — so layout is built
 * exclusively from `<table role="presentation">` nesting, every CSS rule
 * is inlined on the element that needs it, and the dark-mode `<media>`
 * block is the only stylesheet entry. `box-shadow` on the card degrades
 * gracefully to a flat-but-bordered card, accepted as the trade-off for
 * a single-source layout.
 */
internal object EmailLayoutBuilder {

    /**
     * Wraps [contentHtml] in the Plugwerk transactional-email layout.
     *
     * @param contentHtml raw HTML for the message body, potentially carrying
     *   Mustache placeholders. Inserted verbatim into the card body — caller
     *   is responsible for any `{{var}}` references that match the
     *   template's `placeholders` set.
     * @param ctaUrl absolute URL the call-to-action button points at, or
     *   `null` to omit the CTA block entirely. May itself be a Mustache
     *   placeholder (e.g. `{{verificationLink}}`).
     * @param ctaText label for the CTA button, e.g. "Verify email". Ignored
     *   when [ctaUrl] is null.
     * @param footerLine2 second line of the footer ("You're receiving this
     *   because…"). Plain text; HTML is **not** escaped here, so callers
     *   should not pass user-controlled strings.
     * @return a complete `<!doctype html>` document ready to be persisted as
     *   `mail_template.body_html` or returned from
     *   `MailTemplate.defaultBodyHtmlTemplate`.
     */
    fun wrap(contentHtml: String, ctaUrl: String?, ctaText: String?, footerLine2: String): String {
        val cta = if (ctaUrl != null && ctaText != null) renderCta(ctaUrl, ctaText) else ""
        return """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light dark">
  <meta name="supported-color-schemes" content="light dark">
  <title>Plugwerk</title>
  <style>
    @media (prefers-color-scheme: dark) {
      body, .pw-page { background-color: #1E1E1E !important; }
      .pw-card { background-color: #262626 !important; border-color: #393939 !important; }
      .pw-card-body, .pw-wordmark { color: #FFFFFF !important; }
      .pw-footer { color: #A8A8A8 !important; }
      .pw-fallback-link { color: #4589FF !important; }
    }
  </style>
</head>
<body class="pw-page" style="margin:0;padding:32px 16px;background-color:#F4F4F4;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
    <tr>
      <td align="center">
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="margin:0 auto;max-width:600px;">
          <tr>
            <td style="padding:0 4px 24px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td class="pw-wordmark" style="padding:0 0 6px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:22px;font-weight:700;letter-spacing:-0.01em;color:#161616;">Plugwerk</td>
                </tr>
                <tr>
                  <td style="height:2px;line-height:2px;font-size:0;background-color:#0F62FE;">&nbsp;</td>
                </tr>
              </table>
            </td>
          </tr>
        </table>
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="pw-card" style="margin:0 auto;max-width:600px;background-color:#FFFFFF;border:1px solid #E0E0E0;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);overflow:hidden;">
          <tr>
            <td width="4" class="pw-accent" style="width:4px;background-color:#0F62FE;line-height:0;font-size:0;">&nbsp;</td>
            <td class="pw-card-body" style="padding:32px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:15px;line-height:1.6;color:#161616;">
$contentHtml
$cta
            </td>
          </tr>
        </table>
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="margin:24px auto 0;max-width:600px;">
          <tr>
            <td class="pw-footer" style="padding:0 4px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:12px;line-height:1.5;color:#6F6F6F;">
              Sent by Plugwerk &middot; {{siteName}}<br>
              $footerLine2
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>
"""
    }

    /**
     * Bullet-proof CTA: nested `<table>` with `bgcolor` on the cell so even
     * Outlook's Word renderer paints the background; rounded corners via
     * inline `border-radius` (lost in Outlook desktop, accepted). The `<a>`
     * is `display:inline-block` so the click area covers the full padded
     * box, not just the text glyphs.
     */
    private fun renderCta(ctaUrl: String, ctaText: String): String {
        // Two newlines and indentation match the surrounding card-body
        // styling so a manual diff against the persisted DB body stays
        // visually clean.
        return """
              <table role="presentation" border="0" cellspacing="0" cellpadding="0" style="margin:24px 0 0;">
                <tr>
                  <td align="left" bgcolor="#0F62FE" style="border-radius:6px;">
                    <a href="$ctaUrl" target="_blank" class="pw-cta" style="display:inline-block;padding:12px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:15px;font-weight:600;line-height:1;color:#FFFFFF;text-decoration:none;border-radius:6px;">$ctaText</a>
                  </td>
                </tr>
              </table>"""
    }
}
