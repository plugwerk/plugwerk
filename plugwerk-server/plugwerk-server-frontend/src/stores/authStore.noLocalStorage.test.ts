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
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * **Regression guard (ADR-0027 / #294 — TS-001).** Fails on any code path inside
 * the auth store (or anything it pulls in) that writes a token-like key to
 * `localStorage`. Detected at write time so the stack trace points at the
 * offending line.
 *
 * The pattern below flags keys with any of: `token`, `auth`, `jwt`, `bearer`,
 * `credential`, `refresh`, `access`. It explicitly allows the single namespace
 * preference key (`pw-namespace`), which is UI state, not a credential.
 */
const TOKEN_LIKE = /(token|auth|jwt|bearer|credential|refresh|access)/i;
const ALLOWED_KEYS = new Set(["pw-namespace", "pw-migrated-v294"]);

describe("authStore does not persist credentials in localStorage", () => {
  let setItemSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.resetModules();
    // Wrap the *real* setItem in a spy that fails fast on token-like keys.
    // Using `wraps` behaviour (call-through) keeps setNamespace etc. working.
    const realSetItem = Storage.prototype.setItem;
    setItemSpy = vi
      .spyOn(Storage.prototype, "setItem")
      .mockImplementation(function (this: Storage, key: string, value: string) {
        if (!ALLOWED_KEYS.has(key) && TOKEN_LIKE.test(key)) {
          throw new Error(
            `Regression: authStore wrote token-like key "${key}" to storage (ADR-0027 / #294)`,
          );
        }
        realSetItem.call(this, key, value);
      });
  });

  afterEach(() => {
    setItemSpy.mockRestore();
  });

  it("login does not write any auth-credential key to localStorage", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => ({
          accessToken: "test-token",
          userId: "11111111-2222-3333-4444-555555555555",
          displayName: "Alice",
          username: "alice",
          passwordChangeRequired: false,
          isSuperadmin: false,
        }),
      })),
    );
    const { useAuthStore } = await import("./authStore");

    await useAuthStore.getState().login("alice", "secret");

    expect(useAuthStore.getState().accessToken).toBe("test-token");
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    // If any credential key landed in localStorage the spy has thrown already.
  });

  it("logout does not write any auth-credential key to localStorage", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, status: 204 })),
    );
    const { useAuthStore } = await import("./authStore");
    useAuthStore.getState().setAuth({
      accessToken: "test",
      userId: "11111111-2222-3333-4444-555555555555",
      displayName: "Alice",
      username: "alice",
      email: "alice@example.test",
      source: "LOCAL",
      passwordChangeRequired: false,
      isSuperadmin: false,
    });

    await useAuthStore.getState().logout();

    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it("hydrate (success path) does not write any auth-credential key", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => ({
          accessToken: buildJwt("alice"),
          passwordChangeRequired: false,
          isSuperadmin: false,
        }),
      })),
    );
    const { useAuthStore } = await import("./authStore");

    await useAuthStore.getState().hydrate();

    expect(useAuthStore.getState().isHydrating).toBe(false);
  });

  it("hydrate (401 path) does not write any auth-credential key", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: false, status: 401 })),
    );
    const { useAuthStore } = await import("./authStore");

    await useAuthStore.getState().hydrate();

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().isHydrating).toBe(false);
  });

  it("setNamespace is still allowed (UI preference, not a credential)", async () => {
    const { useAuthStore } = await import("./authStore");
    expect(() => useAuthStore.getState().setNamespace("acme")).not.toThrow();
    expect(localStorage.getItem("pw-namespace")).toBe("acme");
  });
});

/** Builds a syntactically-valid JWT with a `sub` claim so refresh.ts can decode it. */
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
