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
import { namespacesApi } from "../api/config";
import { refreshAccessToken } from "../api/refresh";
import { useUiStore } from "./uiStore";

/**
 * Auth state — **memory only** (ADR-0027 / #294).
 *
 * The access token and all authenticated-user metadata live here, in React memory.
 * Nothing auth-related is persisted to `localStorage` — XSS cannot read what isn't
 * there. Session continuity across page reloads comes from the httpOnly refresh
 * cookie; [hydrate] calls `/api/v1/auth/refresh` once on app mount to restore the
 * access token if a valid cookie is present.
 */
interface AuthState {
  accessToken: string | null;
  /**
   * Stable Plugwerk user identifier (`plugwerk_user.id`). Authoritative reference
   * after the identity-hub split (#351) — every audit log, FK, and JWT `sub` keys
   * off this. UI display goes through [displayName].
   */
  userId: string | null;
  /** Human-readable label shown in the UI. Defaults to username for LOCAL users. */
  displayName: string | null;
  /**
   * Local-login username. `null` for OIDC-sourced sessions — those identities live
   * with the upstream provider and Plugwerk has no local username for them. The
   * profile page falls back to [displayName] when this is `null`.
   */
  username: string | null;
  email: string | null;
  /**
   * `LOCAL` for password-based accounts, `OIDC` for accounts sourced from an
   * upstream identity provider. Used by the UI to disable password-change
   * affordances for OIDC users.
   */
  source: "LOCAL" | "OIDC" | null;
  namespace: string | null | undefined;
  isAuthenticated: boolean;
  passwordChangeRequired: boolean;
  isSuperadmin: boolean;
  /** `true` until hydrate() has resolved (success or 401). Blocks initial API calls. */
  isHydrating: boolean;

  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hydrate: () => Promise<void>;
  setAuth: (fields: {
    accessToken: string;
    userId: string;
    displayName: string;
    username?: string | null;
    email: string;
    source: "LOCAL" | "OIDC";
    passwordChangeRequired: boolean;
    isSuperadmin: boolean;
  }) => void;
  clearAuth: () => void;
  setNamespace: (ns: string) => void;
  initNamespace: () => Promise<void>;
  clearPasswordChangeRequired: () => void;

  // Legacy alias kept for ProfileSettingsPage compatibility
  apiKey: string | null;
}

const NAMESPACE_KEY = "pw-namespace";
const LEGACY_AUTH_KEYS = [
  "pw-access-token",
  "pw-username",
  "pw-password-change-required",
  "pw-is-superadmin",
];
const LEGACY_MIGRATION_FLAG = "pw-migrated-v294";

/**
 * Strips the legacy localStorage keys we used to persist auth state (pre-ADR-0027).
 * Called exactly once per browser profile via a sessionStorage guard so the Vitest
 * regression guard (which flags any token-like setItem) does not fire on every load.
 */
function migrateLegacyAuthKeys(): void {
  try {
    if (sessionStorage.getItem(LEGACY_MIGRATION_FLAG) === "1") return;
    for (const key of LEGACY_AUTH_KEYS) {
      localStorage.removeItem(key);
    }
    sessionStorage.setItem(LEGACY_MIGRATION_FLAG, "1");
  } catch {
    // sessionStorage unavailable (private mode etc.) — migration becomes best-effort,
    // removeItem is still safe to call on every load.
    for (const key of LEGACY_AUTH_KEYS) {
      try {
        localStorage.removeItem(key);
      } catch {
        /* ignore */
      }
    }
  }
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  userId: null,
  displayName: null,
  username: null,
  email: null,
  source: null,
  namespace: undefined,
  isAuthenticated: false,
  passwordChangeRequired: false,
  isSuperadmin: false,
  isHydrating: true,

  get apiKey() {
    return get().accessToken;
  },

  setAuth({
    accessToken,
    userId,
    displayName,
    username,
    email,
    source,
    passwordChangeRequired,
    isSuperadmin,
  }) {
    set({
      accessToken,
      userId,
      displayName,
      username: username ?? null,
      email,
      source,
      isAuthenticated: true,
      passwordChangeRequired,
      isSuperadmin,
    });
  },

  clearAuth() {
    set({
      accessToken: null,
      userId: null,
      displayName: null,
      username: null,
      email: null,
      source: null,
      namespace: undefined,
      isAuthenticated: false,
      passwordChangeRequired: false,
      isSuperadmin: false,
    });
  },

  async login(username: string, password: string) {
    const response = await fetch("/api/v1/auth/login", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    if (!response.ok) {
      throw new Error("Invalid credentials");
    }
    const data = await response.json();
    get().setAuth({
      accessToken: data.accessToken,
      userId: data.userId,
      displayName: data.displayName,
      username: typeof data.username === "string" ? data.username : username,
      email: data.email,
      source: data.source === "OIDC" ? "OIDC" : "LOCAL",
      passwordChangeRequired: data.passwordChangeRequired === true,
      isSuperadmin: data.isSuperadmin === true,
    });
  },

  async logout() {
    const token = get().accessToken;
    let endSessionUrl: string | null = null;
    if (token) {
      try {
        const response = await fetch("/api/v1/auth/logout", {
          method: "POST",
          credentials: "include",
          headers: { Authorization: `Bearer ${token}` },
        });
        // OIDC sessions get a 200 + LogoutResponse body; local-login sessions get
        // 204. Anything else (a network blip, server error) we silently ignore —
        // the local cleanup below still runs.
        if (response.ok && response.status === 200) {
          try {
            const body = await response.json();
            if (typeof body?.endSessionUrl === "string" && body.endSessionUrl) {
              endSessionUrl = body.endSessionUrl;
            }
          } catch {
            // Body was not JSON / was empty — fall through to local cleanup.
          }
        }
      } catch {
        // Network failure on logout is non-fatal — the server-side revocation is
        // best-effort; the client still clears its state.
      }
    }
    // Namespace is a UI preference (not credential material) and is intentionally
    // retained across logout/login on the same browser.
    get().clearAuth();
    if (endSessionUrl) {
      // Hand control to the IdP for RP-Initiated Logout (#352). The IdP destroys
      // its session cookies and bounces us back to `post_logout_redirect_uri`,
      // at which point the SPA boots fresh and the next "Login with provider"
      // click shows the credential prompt instead of silent SSO.
      //
      // Override `post_logout_redirect_uri` with the actual browser origin: the
      // backend builds it from `plugwerk.server.base-url` (typically the
      // production server), but in the Vite dev setup the browser runs on
      // :5173 while the backend thinks it lives on :8080. Letting Keycloak
      // bounce to :8080 lands the user outside the SPA. The browser's own
      // `window.location.origin` is always the correct destination — both the
      // dev :5173 and the prod hostname must be in the IdP allow-list anyway
      // (the OAuth2 redirect URI is whitelisted on the same wildcard).
      window.location.assign(rewritePostLogoutRedirect(endSessionUrl));
    }
  },

  async hydrate() {
    migrateLegacyAuthKeys();
    try {
      const result = await refreshAccessToken();
      if (result) {
        get().setAuth(result);
      }
    } catch {
      // Refresh failed — stay anonymous. Not an error to log; common case for
      // first-time visitors and after session expiry.
    } finally {
      set({ isHydrating: false });
    }
  },

  setNamespace(ns) {
    localStorage.setItem(NAMESPACE_KEY, ns);
    set({ namespace: ns });
  },

  async initNamespace() {
    try {
      const res = await namespacesApi.listNamespaces();
      const slugs = res.data.map((ns) => ns.slug);
      const stored = localStorage.getItem(NAMESPACE_KEY);
      if (stored && slugs.includes(stored)) {
        set({ namespace: stored });
      } else if (slugs.length > 0) {
        localStorage.setItem(NAMESPACE_KEY, slugs[0]);
        set({ namespace: slugs[0] });
      } else {
        localStorage.removeItem(NAMESPACE_KEY);
        set({ namespace: null });
      }
    } catch (err) {
      // TS-015 / #279: surface the failure instead of silently clearing the
      // namespace. Pre-fix, a network error here landed the user on /onboarding
      // with an empty namespace list and no explanation. We do NOT re-throw —
      // LoginPage.handleSubmit awaits initNamespace() and assumes it settles
      // either way; a throw would break the post-login navigation flow.
      localStorage.removeItem(NAMESPACE_KEY);
      set({ namespace: null });
      useUiStore.getState().addToast({
        type: "error",
        title: "Could not load namespaces",
        message: "Check your connection and try again.",
      });
      console.error(
        "[authStore.initNamespace] namespacesApi.listNamespaces failed",
        err,
      );
    }
  },

  clearPasswordChangeRequired() {
    set({ passwordChangeRequired: false });
  },
}));

/**
 * Replaces the `post_logout_redirect_uri` query parameter with
 * `${window.location.origin}/login`. Falls back to the original URL when the
 * input is not parseable — RP-Initiated Logout still works against the IdP,
 * the bounce target just stays at whatever the backend produced.
 */
function rewritePostLogoutRedirect(endSessionUrl: string): string {
  try {
    const url = new URL(endSessionUrl);
    url.searchParams.set(
      "post_logout_redirect_uri",
      `${window.location.origin}/login`,
    );
    return url.toString();
  } catch {
    return endSessionUrl;
  }
}
