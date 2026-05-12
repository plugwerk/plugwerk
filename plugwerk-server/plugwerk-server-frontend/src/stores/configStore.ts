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
import { create } from "zustand";
import { axiosInstance } from "../api/config";

/**
 * One OIDC provider button shown on the login page (issue #79).
 *
 * Mirrors the `OidcProviderLoginInfo` schema from the backend OpenAPI spec.
 * `loginUrl` is always a same-origin relative path of the form
 * `/oauth2/authorization/{id}` — Spring Security's OAuth2 client filter
 * intercepts it and starts the Authorization Code Flow with PKCE. The
 * frontend just navigates the browser there.
 */
export interface OidcProviderLoginInfo {
  readonly id: string;
  readonly name: string;
  readonly loginUrl: string;
  /**
   * Same shape as `loginUrl` but with the OIDC `prompt` query parameter
   * appended so the upstream re-displays its account-picker / re-auth
   * screen instead of silently re-using the existing IdP session
   * (issue #410).
   *
   * `null` for provider types that do not honour `prompt` (today only
   * GitHub) — see `accountSwitchHintUrl` for the textual fallback.
   */
  readonly accountPickerLoginUrl: string | null;
  /**
   * Absolute upstream URL the user follows to terminate their session
   * at the IdP, when the provider does not honour OIDC `prompt`. Today
   * only set for GitHub (`https://github.com/logout`); `null` for every
   * other provider type.
   *
   * Frontend renders this as a small textual hint underneath the
   * primary button — never as a clickable button — and the user still
   * has to come back to Plugwerk and click the primary button to
   * actually sign in with a different account.
   */
  readonly accountSwitchHintUrl: string | null;
  /**
   * Brand-agnostic icon identifier the login-page button uses to pick
   * the right glyph. Stable enum from the backend — see
   * `OidcProviderLoginInfo.iconKind` in the OpenAPI spec for the full
   * mapping. Defaults to `"oidc"` (generic key glyph) on any unknown /
   * missing value so a backend regression cannot remove the icon
   * silently.
   */
  readonly iconKind: ProviderIconKind;
}

/**
 * Closed set of icon glyphs the frontend can render for a provider
 * button. Mirrors the OpenAPI enum at `OidcProviderLoginInfo.iconKind`.
 */
export type ProviderIconKind =
  | "github"
  | "google"
  | "facebook"
  | "oidc"
  | "oauth2";

const KNOWN_ICON_KINDS: ReadonlySet<ProviderIconKind> = new Set([
  "github",
  "google",
  "facebook",
  "oidc",
  "oauth2",
]);

interface ConfigState {
  readonly version: string;
  readonly maxFileSizeMb: number;
  readonly defaultTimezone: string;
  /**
   * Operator-branded display name surfaced by the UI in the browser-tab
   * title, top bar, footer, and onboarding (#234). Backed by the
   * `general.site_name` admin setting (ADR-0016). Falls back to
   * `"Plugwerk"` when the response is missing or empty so user-visible
   * surfaces never render an empty string.
   */
  readonly siteName: string;
  /** OIDC providers currently enabled by the operator. Empty when none. */
  readonly oidcProviders: readonly OidcProviderLoginInfo[];
  /**
   * Whether the operator has enabled the public self-registration flow
   * (#420). The login page renders the "Don't have an account? Sign up"
   * link conditionally on this; the backend enforces the gate
   * independently — flipping this client-side cannot bypass the
   * controller's 404 disguise.
   */
  readonly selfRegistrationEnabled: boolean;
  /**
   * Whether the operator has enabled the public forgot-password flow
   * (#421). The login page renders the "Forgot password?" link only
   * when this is true; visiting `/forgot-password` or `/reset-password`
   * directly with the setting off yields a NotFound shell. The backend
   * 404s both endpoints in that state, so flipping this client-side
   * cannot bypass the gate.
   */
  readonly passwordResetEnabled: boolean;
  readonly loaded: boolean;
  /**
   * Fetches `/api/v1/config`. Skips the request when the store is already
   * loaded unless `{ force: true }` is passed — call with `force` after any
   * mutation that changes `application_setting` so cached values
   * (defaultTimezone, maxFileSizeMb) stay in sync with the DB. Also call
   * with `force` after an admin enables/disables an OIDC provider so the
   * login-page button list reflects the change.
   */
  fetchConfig: (options?: { force?: boolean }) => Promise<void>;
}

export const useConfigStore = create<ConfigState>((set, get) => ({
  version: "…",
  maxFileSizeMb: 100,
  defaultTimezone: "UTC",
  siteName: "Plugwerk",
  oidcProviders: [],
  selfRegistrationEnabled: false,
  passwordResetEnabled: false,
  loaded: false,

  async fetchConfig(options) {
    if (get().loaded && !options?.force) return;
    try {
      const res = await axiosInstance.get("/config");
      set({
        version: res.data?.version ?? "unknown",
        maxFileSizeMb:
          typeof res.data?.upload?.maxFileSizeMb === "number"
            ? res.data.upload.maxFileSizeMb
            : 100,
        defaultTimezone:
          typeof res.data?.general?.defaultTimezone === "string" &&
          res.data.general.defaultTimezone.length > 0
            ? res.data.general.defaultTimezone
            : "UTC",
        siteName:
          typeof res.data?.general?.siteName === "string" &&
          res.data.general.siteName.length > 0
            ? res.data.general.siteName
            : "Plugwerk",
        oidcProviders: parseOidcProviders(res.data?.auth?.oidcProviders),
        selfRegistrationEnabled:
          res.data?.auth?.selfRegistrationEnabled === true,
        passwordResetEnabled: res.data?.auth?.passwordResetEnabled === true,
        loaded: true,
      });
    } catch {
      set({
        version: "unknown",
        siteName: "Plugwerk",
        oidcProviders: [],
        selfRegistrationEnabled: false,
        passwordResetEnabled: false,
        loaded: true,
      });
    }
  },
}));

/**
 * Defensive parser — `/config` is public and the response shape is owned by
 * the backend, but keeping the runtime narrowing local prevents one bad
 * response from rendering broken `<a href={undefined}>` buttons.
 */
function parseOidcProviders(raw: unknown): readonly OidcProviderLoginInfo[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((entry: unknown) => {
    if (typeof entry !== "object" || entry === null) return [];
    const e = entry as Record<string, unknown>;
    if (
      typeof e.id !== "string" ||
      typeof e.name !== "string" ||
      typeof e.loginUrl !== "string"
    ) {
      return [];
    }
    // Defensive null on unexpected shapes — a missing field is treated
    // as "provider does not support account-switching" rather than
    // crashing. Backend always sends the field (string or null) per
    // OpenAPI, but the runtime narrowing keeps the store honest.
    const accountPickerLoginUrl =
      typeof e.accountPickerLoginUrl === "string"
        ? e.accountPickerLoginUrl
        : null;
    const accountSwitchHintUrl =
      typeof e.accountSwitchHintUrl === "string"
        ? e.accountSwitchHintUrl
        : null;
    // Fall through to "oidc" (generic key glyph) on any unknown / missing
    // value so a backend regression cannot remove the provider icon
    // silently — the operator still sees a button, just without brand
    // colour.
    const iconKind: ProviderIconKind =
      typeof e.iconKind === "string" &&
      KNOWN_ICON_KINDS.has(e.iconKind as ProviderIconKind)
        ? (e.iconKind as ProviderIconKind)
        : "oidc";
    return [
      {
        id: e.id,
        name: e.name,
        loginUrl: e.loginUrl,
        accountPickerLoginUrl,
        accountSwitchHintUrl,
        iconKind,
      },
    ];
  });
}
