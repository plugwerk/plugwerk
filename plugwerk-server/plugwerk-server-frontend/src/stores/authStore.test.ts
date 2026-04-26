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
import { describe, it, expect, beforeEach, vi } from "vitest";
import { act } from "react";
import { useAuthStore } from "./authStore";
import { useUiStore } from "./uiStore";
import { namespacesApi } from "../api/config";

describe("useAuthStore", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    useAuthStore.setState({
      accessToken: null,
      username: null,
      namespace: undefined,
      isAuthenticated: false,
      passwordChangeRequired: false,
      isSuperadmin: false,
      isHydrating: false,
    });
    useUiStore.setState({ toasts: [] });
  });

  describe("initial state (post-ADR-0027)", () => {
    it("is not authenticated on fresh load", () => {
      expect(useAuthStore.getState().isAuthenticated).toBe(false);
    });

    it("has null accessToken on fresh load", () => {
      expect(useAuthStore.getState().accessToken).toBeNull();
    });

    it("has undefined namespace before initialization", () => {
      expect(useAuthStore.getState().namespace).toBeUndefined();
    });
  });

  describe("login", () => {
    it("sets accessToken and isAuthenticated on success; NEVER persists to localStorage", async () => {
      const userId = "11111111-2222-3333-4444-555555555555";
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({
          ok: true,
          json: () =>
            Promise.resolve({
              accessToken: "tok_abc",
              userId,
              displayName: "Alice",
              username: "alice",
              expiresIn: 900,
              passwordChangeRequired: false,
              isSuperadmin: false,
            }),
        }),
      );

      await act(async () => {
        await useAuthStore.getState().login("alice", "secret");
      });

      expect(useAuthStore.getState().isAuthenticated).toBe(true);
      expect(useAuthStore.getState().accessToken).toBe("tok_abc");
      expect(useAuthStore.getState().userId).toBe(userId);
      expect(useAuthStore.getState().displayName).toBe("Alice");
      expect(useAuthStore.getState().username).toBe("alice");
      // The whole point of #294: no credential keys leak into localStorage.
      expect(localStorage.getItem("pw-access-token")).toBeNull();
      expect(localStorage.getItem("pw-username")).toBeNull();

      vi.unstubAllGlobals();
    });

    it("throws on invalid credentials", async () => {
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));

      await expect(
        act(async () => {
          await useAuthStore.getState().login("wrong", "wrong");
        }),
      ).rejects.toThrow("Invalid credentials");

      expect(useAuthStore.getState().isAuthenticated).toBe(false);

      vi.unstubAllGlobals();
    });
  });

  describe("logout", () => {
    it("clears accessToken and isAuthenticated", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({ ok: true, status: 204 }),
      );
      useAuthStore.setState({
        accessToken: "tok_xyz",
        isAuthenticated: true,
        username: "alice",
      });

      await act(async () => {
        await useAuthStore.getState().logout();
      });

      expect(useAuthStore.getState().isAuthenticated).toBe(false);
      expect(useAuthStore.getState().accessToken).toBeNull();
      vi.unstubAllGlobals();
    });

    it("clears namespace from state", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({ ok: true, status: 204 }),
      );
      useAuthStore.setState({
        accessToken: "tok_xyz",
        isAuthenticated: true,
        namespace: "my-org",
      });

      await act(async () => {
        await useAuthStore.getState().logout();
      });

      expect(useAuthStore.getState().namespace).toBeUndefined();
      vi.unstubAllGlobals();
    });

    it("navigates to endSessionUrl with post_logout_redirect_uri rewritten to current origin (#352)", async () => {
      // Backend hands us a URL whose post_logout_redirect_uri points at the
      // configured `plugwerk.server.base-url` (here: http://app/login). In
      // dev that is the Spring backend on :8080 while the browser actually
      // runs on :5173 — letting the IdP bounce to the backend would land
      // outside the SPA. The store rewrites the parameter to the current
      // browser origin before navigating.
      const backendUrl =
        "http://kc.local/realms/plugwerk/protocol/openid-connect/logout?id_token_hint=eyJ&post_logout_redirect_uri=http%3A%2F%2Fapp%2Flogin";
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ endSessionUrl: backendUrl }),
        }),
      );
      const assignSpy = vi.fn();
      const originalLocation = window.location;
      Object.defineProperty(window, "location", {
        configurable: true,
        value: {
          ...originalLocation,
          origin: "http://localhost:5173",
          assign: assignSpy,
        },
      });
      useAuthStore.setState({
        accessToken: "tok_oidc",
        isAuthenticated: true,
        username: "kc:alice-sub",
      });

      await act(async () => {
        await useAuthStore.getState().logout();
      });

      // Local cleanup STILL happens before the redirect — accessToken in memory
      // is dropped first so a racing tab does not see it.
      expect(useAuthStore.getState().isAuthenticated).toBe(false);
      expect(useAuthStore.getState().accessToken).toBeNull();
      expect(assignSpy).toHaveBeenCalledOnce();
      const navigatedTo = new URL(assignSpy.mock.calls[0][0] as string);
      expect(navigatedTo.origin + navigatedTo.pathname).toBe(
        "http://kc.local/realms/plugwerk/protocol/openid-connect/logout",
      );
      expect(navigatedTo.searchParams.get("id_token_hint")).toBe("eyJ");
      expect(navigatedTo.searchParams.get("post_logout_redirect_uri")).toBe(
        "http://localhost:5173/login",
      );

      Object.defineProperty(window, "location", {
        configurable: true,
        value: originalLocation,
      });
      vi.unstubAllGlobals();
    });

    it("does NOT navigate when server returns 200 with empty endSessionUrl (#352)", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ endSessionUrl: "" }),
        }),
      );
      const assignSpy = vi.fn();
      const originalLocation = window.location;
      Object.defineProperty(window, "location", {
        configurable: true,
        value: { ...originalLocation, assign: assignSpy },
      });
      useAuthStore.setState({
        accessToken: "tok",
        isAuthenticated: true,
        username: "alice",
      });

      await act(async () => {
        await useAuthStore.getState().logout();
      });

      expect(assignSpy).not.toHaveBeenCalled();

      Object.defineProperty(window, "location", {
        configurable: true,
        value: originalLocation,
      });
      vi.unstubAllGlobals();
    });

    it("does NOT navigate on 204 No Content (local-login flow, #352)", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({ ok: true, status: 204 }),
      );
      const assignSpy = vi.fn();
      const originalLocation = window.location;
      Object.defineProperty(window, "location", {
        configurable: true,
        value: { ...originalLocation, assign: assignSpy },
      });
      useAuthStore.setState({
        accessToken: "tok",
        isAuthenticated: true,
        username: "alice",
      });

      await act(async () => {
        await useAuthStore.getState().logout();
      });

      expect(assignSpy).not.toHaveBeenCalled();

      Object.defineProperty(window, "location", {
        configurable: true,
        value: originalLocation,
      });
      vi.unstubAllGlobals();
    });
  });

  describe("setNamespace", () => {
    it("updates namespace in state", () => {
      act(() => {
        useAuthStore.getState().setNamespace("acme");
      });
      expect(useAuthStore.getState().namespace).toBe("acme");
    });

    it("persists namespace to localStorage (UI preference, not a credential)", () => {
      act(() => {
        useAuthStore.getState().setNamespace("acme");
      });
      expect(localStorage.getItem("pw-namespace")).toBe("acme");
    });
  });

  describe("initNamespace", () => {
    it("sets namespace from API response and emits no toast on success", async () => {
      const spy = vi.spyOn(namespacesApi, "listNamespaces").mockResolvedValue({
        data: [{ slug: "acme" }, { slug: "globex" }],
      } as unknown as Awaited<ReturnType<typeof namespacesApi.listNamespaces>>);

      await act(async () => {
        await useAuthStore.getState().initNamespace();
      });

      expect(useAuthStore.getState().namespace).toBe("acme");
      expect(localStorage.getItem("pw-namespace")).toBe("acme");
      expect(useUiStore.getState().toasts).toHaveLength(0);

      spy.mockRestore();
    });

    it("surfaces a toast and clears state when listNamespaces fails (TS-015 / #279)", async () => {
      // Pre-fix: catch was empty — user landed on /onboarding with an empty
      // namespace list and no explanation. Now an error toast must be raised.
      const consoleSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      const apiSpy = vi
        .spyOn(namespacesApi, "listNamespaces")
        .mockRejectedValue(new Error("network down"));
      localStorage.setItem("pw-namespace", "stale");

      await act(async () => {
        await useAuthStore.getState().initNamespace();
      });

      expect(useAuthStore.getState().namespace).toBeNull();
      expect(localStorage.getItem("pw-namespace")).toBeNull();

      const toasts = useUiStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0].type).toBe("error");
      expect(toasts[0].title).toMatch(/namespace/i);

      // Console.error keeps the stack trace available to developers without
      // breaking the LoginPage-awaits-initNamespace flow.
      expect(consoleSpy).toHaveBeenCalledOnce();

      apiSpy.mockRestore();
      consoleSpy.mockRestore();
    });
  });

  describe("hydrate", () => {
    it("sets auth state when refresh cookie is valid", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({
          ok: true,
          json: () =>
            Promise.resolve({
              accessToken: "tok_abc",
              userId: "11111111-2222-3333-4444-555555555555",
              displayName: "Alice",
              username: "alice",
              passwordChangeRequired: false,
              isSuperadmin: true,
            }),
        }),
      );

      await act(async () => {
        await useAuthStore.getState().hydrate();
      });

      expect(useAuthStore.getState().isAuthenticated).toBe(true);
      expect(useAuthStore.getState().isSuperadmin).toBe(true);
      expect(useAuthStore.getState().displayName).toBe("Alice");
      expect(useAuthStore.getState().isHydrating).toBe(false);
      vi.unstubAllGlobals();
    });

    it("stays anonymous and settles isHydrating when refresh fails", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({ ok: false, status: 401 }),
      );
      useAuthStore.setState({ isHydrating: true });

      await act(async () => {
        await useAuthStore.getState().hydrate();
      });

      expect(useAuthStore.getState().isAuthenticated).toBe(false);
      expect(useAuthStore.getState().isHydrating).toBe(false);
      vi.unstubAllGlobals();
    });
  });
});
