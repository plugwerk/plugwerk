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
import { KeyRound } from "lucide-react";
import type { ProviderIconKind } from "../../stores/configStore";

interface ProviderIconProps {
  kind: ProviderIconKind;
  size?: number;
}

/**
 * Brand glyph for one OIDC / OAuth2 provider button on the login page.
 *
 * Two sources:
 *
 *   - **lucide-react** for the generic `oidc` / `oauth2` fallback
 *     (`KeyRound`). Lucide is already in the bundle for the rest of
 *     the admin UI, so this is the cheapest path.
 *   - **Inline SVG** for `github`, `google`, and `facebook`. Lucide
 *     v1 removed the Brand Icons set (Github included) for trademark
 *     reasons; the simplified single-colour glyphs below are the
 *     versions each vendor's design policy permits in third-party UIs
 *     without a brand-approval process. Rendered in the surrounding
 *     text colour via `currentColor` so they inherit the button's
 *     variant contrast.
 *
 * Sized at 18px by default — matches the lucide convention for inline
 * action-button glyphs and the stroke-width vs. fill weight balances
 * across both SVG sources at this size.
 */
export function ProviderIcon({ kind, size = 18 }: ProviderIconProps) {
  switch (kind) {
    case "github":
      return <GithubMark size={size} />;
    case "google":
      return <GoogleMark size={size} />;
    case "facebook":
      return <FacebookMark size={size} />;
    case "oidc":
    case "oauth2":
      return <KeyRound size={size} aria-hidden />;
    default:
      // Exhaustiveness: TS narrows the switch above, but a backend that
      // adds a new kind without a frontend update would otherwise crash.
      // Render the key glyph as the safe fallback.
      return <KeyRound size={size} aria-hidden />;
  }
}

/**
 * GitHub Octocat silhouette — single-colour. Replaced lucide-react's
 * `Github` icon, which v1 dropped from the Brand Icons set for
 * trademark reasons. The path below is the standard GitHub mark
 * commonly used in OAuth login buttons; renders in the surrounding
 * text colour via `currentColor`.
 */
function GithubMark({ size }: { size: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden
    >
      <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61-.546-1.385-1.335-1.755-1.335-1.755-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.4 3-.405 1.02.005 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
    </svg>
  );
}

/**
 * Google "G" mark — single-colour version. The official multi-colour
 * mark is brand-restricted; the single-colour silhouette is the version
 * Google's own design system permits in third-party UIs that do not
 * have a brand-approval process. Inherits the surrounding text colour
 * via `currentColor`.
 */
function GoogleMark({ size }: { size: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden
    >
      <path d="M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z" />
    </svg>
  );
}

/**
 * Facebook "f" mark — single-colour. Same brand-policy reasoning as
 * GoogleMark above.
 */
function FacebookMark({ size }: { size: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden
    >
      <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
    </svg>
  );
}
