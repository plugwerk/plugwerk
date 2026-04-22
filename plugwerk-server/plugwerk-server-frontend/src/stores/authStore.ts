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
import { namespaceMembersApi, namespacesApi } from "../api/config";
import type { NamespaceRole } from "../api/generated/model";
import { refreshAccessToken } from "../api/refresh";

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
  username: string | null;
  namespace: string | null | undefined;
  isAuthenticated: boolean;
  passwordChangeRequired: boolean;
  isSuperadmin: boolean;
  namespaceRole: NamespaceRole | null;
  /** `true` until hydrate() has resolved (success or 401). Blocks initial API calls. */
  isHydrating: boolean;

  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hydrate: () => Promise<void>;
  setAuth: (fields: {
    accessToken: string;
    username: string;
    passwordChangeRequired: boolean;
    isSuperadmin: boolean;
  }) => void;
  clearAuth: () => void;
  setNamespace: (ns: string) => void;
  initNamespace: () => Promise<void>;
  clearPasswordChangeRequired: () => void;
  fetchNamespaceRole: (ns: string) => Promise<void>;

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
  username: null,
  namespace: undefined,
  isAuthenticated: false,
  passwordChangeRequired: false,
  isSuperadmin: false,
  namespaceRole: null,
  isHydrating: true,

  get apiKey() {
    return get().accessToken;
  },

  setAuth({ accessToken, username, passwordChangeRequired, isSuperadmin }) {
    set({
      accessToken,
      username,
      isAuthenticated: true,
      passwordChangeRequired,
      isSuperadmin,
    });
  },

  clearAuth() {
    set({
      accessToken: null,
      username: null,
      namespace: undefined,
      isAuthenticated: false,
      passwordChangeRequired: false,
      isSuperadmin: false,
      namespaceRole: null,
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
      username,
      passwordChangeRequired: data.passwordChangeRequired === true,
      isSuperadmin: data.isSuperadmin === true,
    });
  },

  async logout() {
    const token = get().accessToken;
    if (token) {
      try {
        await fetch("/api/v1/auth/logout", {
          method: "POST",
          credentials: "include",
          headers: { Authorization: `Bearer ${token}` },
        });
      } catch {
        // Network failure on logout is non-fatal — the server-side revocation is
        // best-effort; the client still clears its state.
      }
    }
    // Namespace is a UI preference (not credential material) and is intentionally
    // retained across logout/login on the same browser.
    get().clearAuth();
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
    set({ namespace: ns, namespaceRole: null });
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
    } catch {
      localStorage.removeItem(NAMESPACE_KEY);
      set({ namespace: null });
    }
  },

  clearPasswordChangeRequired() {
    set({ passwordChangeRequired: false });
  },

  async fetchNamespaceRole(ns: string) {
    if (!get().isAuthenticated) {
      set({ namespaceRole: null });
      return;
    }
    try {
      const response = await namespaceMembersApi.getMyMembership({ ns });
      set({ namespaceRole: response.data.role });
    } catch {
      set({ namespaceRole: null });
    }
  },
}));
