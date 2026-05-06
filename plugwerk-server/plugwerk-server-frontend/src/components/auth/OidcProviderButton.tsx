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
import { Box, Button, Link, Typography } from "@mui/material";
import type { OidcProviderLoginInfo } from "../../stores/configStore";
import { ProviderIcon } from "./ProviderIcon";

interface OidcProviderButtonProps {
  provider: OidcProviderLoginInfo;
}

/**
 * One provider button on the login page (issue #79) plus an optional
 * "Use a different account" affordance underneath (issue #410).
 *
 * Composition:
 *
 *   1. Primary `<Button component="a">` — silent SSO. Single click, the
 *      browser navigates to `/oauth2/authorization/{id}`, Spring's
 *      OAuth2 filter takes over. If the upstream session is still valid
 *      this re-uses the same account (the desired default for ~99 % of
 *      logins).
 *
 *   2. Secondary affordance: depends on `accountPickerLoginUrl`.
 *      - String → small "Use a different account" link that points at
 *        the same `/oauth2/authorization/{id}` URL but with `?prompt=…`
 *        appended. Clicking it pops the upstream account picker.
 *      - `null` → small textual hint pointing the operator at the
 *        upstream's logout URL (today only GitHub, which has no
 *        standard `prompt` parameter). Honest about the limitation
 *        instead of rendering a clickable link that does nothing.
 *
 * Both clickables are plain anchors (no XHR) so Spring Security can
 * intercept them — same model as the original login button.
 */
export function OidcProviderButton({ provider }: OidcProviderButtonProps) {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 0.5,
      }}
    >
      <Button
        component="a"
        href={provider.loginUrl}
        variant="outlined"
        size="large"
        fullWidth
        startIcon={<ProviderIcon kind={provider.iconKind} />}
      >
        {`Sign in with ${provider.name}`}
      </Button>
      <ProviderAccountSwitchAffordance provider={provider} />
    </Box>
  );
}

interface AffordanceProps {
  provider: OidcProviderLoginInfo;
}

/**
 * Renders the small caption underneath the primary button. Two branches:
 *
 *  - Provider supports OIDC `prompt` → clickable "Use a different account"
 *    link with `aria-label` carrying the provider name so screen readers
 *    have context outside the button group.
 *  - Provider does not (today only GitHub) → muted textual hint pointing
 *    at the upstream logout URL.
 *
 * The component renders nothing for an unknown / future shape — defensive
 * fallback so a backend that omits both fields cannot break the layout.
 */
function ProviderAccountSwitchAffordance({ provider }: AffordanceProps) {
  if (provider.accountPickerLoginUrl) {
    return (
      <Link
        component="a"
        href={provider.accountPickerLoginUrl}
        variant="caption"
        underline="hover"
        aria-label={`Sign in with a different ${provider.name} account`}
        sx={{
          color: "text.secondary",

          // Same `text.secondary` colour as the GitHub-hint host link
          // below, including on hover — keeps both affordances visually
          // identical so the difference between "click me" and "this is
          // here for context" lives in the wording, not the colour.
          fontWeight: 500,

          "&:hover": { color: "text.secondary" }
        }}>Use a different account
              </Link>
    );
  }
  if (provider.accountSwitchHintUrl) {
    // Backend gave us an upstream sign-out URL but no in-product picker
    // (today only GitHub). Render an honest textual hint with the
    // hostname inline — clicking the host link only terminates the
    // upstream session; the user still has to come back and use the
    // primary button afterwards.
    const hintHost = safeHostname(provider.accountSwitchHintUrl);
    return (
      <Typography
        variant="caption"
        sx={{
          color: "text.secondary",
          textAlign: "center",
          lineHeight: 1.4
        }}>To switch accounts, sign out at{" "}
        <Link
          component="a"
          href={provider.accountSwitchHintUrl}
          target="_blank"
          rel="noopener noreferrer"
          underline="hover"
          color="inherit"
          sx={{ fontFamily: "monospace" }}
        >
          {hintHost}
        </Link>{" "}first.
              </Typography>
    );
  }
  return null;
}

/**
 * Pulls the hostname out of an absolute URL for inline display. Returns
 * the original string if the URL fails to parse — defensive against a
 * misconfigured backend value rather than crashing the login page.
 */
function safeHostname(url: string): string {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}
