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
      namespaceRole: null,
      isHydrating: false,
    });
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
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({
          ok: true,
          json: () =>
            Promise.resolve({
              accessToken: "tok_abc",
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
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
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
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
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

  describe("hydrate", () => {
    it("sets auth state when refresh cookie is valid", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({
          ok: true,
          json: () =>
            Promise.resolve({
              accessToken: buildJwt("alice"),
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

function buildJwt(subject: string): string {
  const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" }))
    .replace(/=+$/, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  const payload = btoa(JSON.stringify({ sub: subject, exp: 9999999999 }))
    .replace(/=+$/, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  return `${header}.${payload}.fake-signature`;
}
