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
import { useMemo, useState } from "react";
import {
  Box,
  Button,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from "@mui/material";
import { Check, Copy, ExternalLink } from "lucide-react";
import type { OidcProviderType } from "../../api/generated/model";

interface ProviderHelp {
  /** Where in the provider's admin UI the operator finds the redirect-URI setting. */
  whereToFind: string;
  /** What that field is labelled at the upstream provider. */
  fieldLabel: string;
  /** Optional direct link to the provider's console / developer settings. */
  consoleUrl?: string;
  /** Human-readable label for the console link. */
  consoleLabel?: string;
}

/**
 * Per-provider hints for *where* the callback URL needs to be pasted at the
 * upstream IdP. Sourced from each provider's most recent public docs as of the
 * implementation date — the wording stays vendor-agnostic enough to survive
 * minor UI redesigns at the upstream.
 *
 * For OIDC and OAUTH2 we deliberately stay generic: those types cover any
 * conformant IdP, so naming a specific console (e.g. Keycloak) would be
 * misleading.
 */
const PROVIDER_HELP: Record<OidcProviderType, ProviderHelp> = {
  GOOGLE: {
    whereToFind:
      "APIs & Services → Credentials → OAuth 2.0 Client IDs → your client",
    fieldLabel: "Authorized redirect URIs",
    consoleUrl: "https://console.cloud.google.com/apis/credentials",
    consoleLabel: "Open Google Cloud Console",
  },
  GITHUB: {
    whereToFind:
      "GitHub → Settings → Developer settings → OAuth Apps → your app",
    fieldLabel: "Authorization callback URL",
    consoleUrl: "https://github.com/settings/developers",
    consoleLabel: "Open GitHub developer settings",
  },
  FACEBOOK: {
    whereToFind: "Meta for Developers → your app → Facebook Login → Settings",
    fieldLabel: "Valid OAuth Redirect URIs",
    consoleUrl: "https://developers.facebook.com/apps",
    consoleLabel: "Open Meta for Developers",
  },
  OIDC: {
    whereToFind:
      "Your IdP's client configuration (Keycloak, Authentik, Auth0, Dex, …)",
    fieldLabel: "Valid Redirect URIs",
  },
  OAUTH2: {
    whereToFind: "Your provider's OAuth2 application settings",
    fieldLabel: "Redirect URI / Callback URL",
  },
};

/**
 * Builds the OAuth2 callback URL Plugwerk listens on for [providerId]. Mirrors
 * Spring's `{baseUrl}/login/oauth2/code/{registrationId}` template, with
 * `registrationId` sourced from the provider's UUID (see `OidcRegistrationIds`
 * on the server). We use `window.location.origin` because that is what the
 * browser will hit when the upstream redirects back; if Plugwerk sits behind
 * a reverse proxy with a different external host, the same origin is what
 * the IdP will use.
 */
function buildCallbackUrl(providerId: string): string {
  return `${window.location.origin}/login/oauth2/code/${providerId}`;
}

interface ProviderCallbackInstructionsProps {
  providerId: string;
  providerType: OidcProviderType;
  /**
   * Visual emphasis of the panel.
   *  - `success`   — fresh-create success step inside the form dialog.
   *  - `inline`    — neutral edit-mode reference card embedded in the form.
   */
  variant?: "success" | "inline";
}

/**
 * Self-contained "register this URL with your IdP" panel. Renders the callback
 * URL in monospace, lets the operator copy it with one click, and points them
 * at the right setting on their provider's side.
 *
 * The panel is the *one* thing the operator needs out of this dialog after
 * a fresh create — so it is composed as the hero of its container, not as a
 * footnote: large URL, prominent copy affordance, vendor-specific hint, and
 * an optional direct link to the upstream console.
 */
export function ProviderCallbackInstructions({
  providerId,
  providerType,
  variant = "inline",
}: ProviderCallbackInstructionsProps) {
  const callbackUrl = useMemo(() => buildCallbackUrl(providerId), [providerId]);
  const help = PROVIDER_HELP[providerType];
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(callbackUrl);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // clipboard write can fail in headless / non-secure contexts. Fall
      // back to selecting the text by focusing the URL element so the user
      // can still ⌘C manually — handled by the readable selectable Box.
    }
  }

  const isSuccess = variant === "success";

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 1.5,
        p: 2.25,
        borderRadius: 1.5,
        border: 1,
        borderColor: isSuccess ? "success.light" : "divider",
        // The success variant uses a tinted green background; the inline
        // variant uses a near-paper neutral so it reads as a reference card
        // inside the form rather than as a status banner.
        bgcolor: isSuccess ? "rgba(46, 125, 50, 0.06)" : "rgba(0, 0, 0, 0.02)",
      }}
    >
      <Stack direction="row" spacing={1} sx={{
        alignItems: "baseline"
      }}>
        <Typography
          variant="overline"
          sx={{
            color: isSuccess ? "success.main" : "text.secondary",
            letterSpacing: 0.8,
            fontWeight: 600,
            lineHeight: 1,
          }}
        >
          Callback URL
        </Typography>
        <Typography variant="caption" sx={{
          color: "text.secondary"
        }}>
          register this at your provider
        </Typography>
      </Stack>
      <Stack direction="row" spacing={1} sx={{
        alignItems: "stretch"
      }}>
        <Box
          component="code"
          tabIndex={0}
          aria-label="OAuth2 callback URL"
          sx={{
            flex: 1,
            minWidth: 0,
            px: 1.5,
            py: 1.25,
            borderRadius: 1,
            border: 1,
            borderColor: "divider",
            bgcolor: "background.paper",
            fontFamily:
              '"JetBrains Mono", "SF Mono", Menlo, ui-monospace, monospace',
            fontSize: "0.85rem",
            color: "text.primary",
            wordBreak: "break-all",
            userSelect: "all",
            outline: "none",
            transition: "border-color 120ms ease",
            "&:focus-visible": {
              borderColor: "primary.main",
              boxShadow: (theme) => `0 0 0 2px ${theme.palette.primary.main}33`,
            },
          }}
        >
          {callbackUrl}
        </Box>
        <Tooltip
          title={copied ? "Copied" : "Copy to clipboard"}
          placement="top"
          arrow
          // Force a re-render of the tooltip when `copied` flips so the label
          // change is announced.
          key={copied ? "copied" : "idle"}
        >
          <IconButton
            onClick={handleCopy}
            aria-label="Copy callback URL"
            color={copied ? "success" : "default"}
            sx={{
              border: 1,
              borderColor: copied ? "success.light" : "divider",
              borderRadius: 1,
              alignSelf: "stretch",
              px: 1.5,
              transition: "all 160ms ease",
              "&:hover": {
                borderColor: copied ? "success.main" : "primary.main",
                color: copied ? "success.main" : "primary.main",
              },
            }}
          >
            {copied ? <Check size={18} /> : <Copy size={18} />}
          </IconButton>
        </Tooltip>
      </Stack>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1.25}
        sx={{
          alignItems: { xs: "flex-start", sm: "center" },
          justifyContent: "space-between",
          mt: 0.25
        }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="body2" sx={{ lineHeight: 1.45 }}>
            <Typography
              component="span"
              variant="body2"
              sx={{ color: "text.secondary" }}
            >
              Paste into:{" "}
            </Typography>
            <Typography
              component="span"
              variant="body2"
              sx={{ fontWeight: 600 }}
            >
              {help.fieldLabel}
            </Typography>
          </Typography>
          <Typography
            variant="caption"
            sx={{
              color: "text.secondary",
              display: "block",
              lineHeight: 1.4,
              mt: 0.25,
            }}
          >
            {help.whereToFind}
          </Typography>
        </Box>
        {help.consoleUrl && (
          <Button
            component="a"
            href={help.consoleUrl}
            target="_blank"
            rel="noopener noreferrer"
            size="small"
            variant="text"
            endIcon={<ExternalLink size={14} />}
            sx={{ flexShrink: 0, whiteSpace: "nowrap" }}
          >
            {help.consoleLabel ?? "Open provider console"}
          </Button>
        )}
      </Stack>
    </Box>
  );
}
