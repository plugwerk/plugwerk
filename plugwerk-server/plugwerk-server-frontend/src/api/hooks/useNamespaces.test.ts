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
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { createQueryWrapper } from "../../test/queryWrapper";
import { useNamespace, useNamespaces } from "./useNamespaces";

const { listNamespaces, authState } = vi.hoisted(() => ({
  listNamespaces: vi.fn(),
  authState: { isAuthenticated: true, isHydrating: false },
}));

vi.mock("../config", () => ({
  namespacesApi: { listNamespaces: () => listNamespaces() },
}));

vi.mock("../../stores/authStore", () => ({
  useAuthStore: (selector: (s: typeof authState) => unknown) =>
    selector(authState),
}));

const NAMESPACES = [
  { slug: "default", displayName: "Default" },
  { slug: "acme", displayName: "Acme" },
];

describe("useNamespaces", () => {
  beforeEach(() => {
    listNamespaces.mockReset();
    authState.isAuthenticated = true;
    authState.isHydrating = false;
  });

  it("fetches the namespace list when authenticated and hydrated", async () => {
    listNamespaces.mockResolvedValue({ data: NAMESPACES });
    const { result } = renderHook(() => useNamespaces(), {
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(NAMESPACES);
    expect(listNamespaces).toHaveBeenCalledOnce();
  });

  it("does not fetch while the auth store is still hydrating", async () => {
    authState.isHydrating = true;
    const { result } = renderHook(() => useNamespaces(), {
      wrapper: createQueryWrapper(),
    });
    // Disabled query stays idle — give React a tick, then assert no call.
    await waitFor(() => expect(result.current.fetchStatus).toBe("idle"));
    expect(listNamespaces).not.toHaveBeenCalled();
  });

  it("does not fetch when the principal is unauthenticated", async () => {
    authState.isAuthenticated = false;
    renderHook(() => useNamespaces(), { wrapper: createQueryWrapper() });
    await waitFor(() => expect(listNamespaces).not.toHaveBeenCalled());
  });
});

describe("useNamespace", () => {
  beforeEach(() => {
    listNamespaces.mockReset();
    authState.isAuthenticated = true;
    authState.isHydrating = false;
    listNamespaces.mockResolvedValue({ data: NAMESPACES });
  });

  it("resolves a namespace by slug once the list has loaded", async () => {
    const { result } = renderHook(() => useNamespace("acme"), {
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.namespace).toBeDefined());
    expect(result.current.namespace?.slug).toBe("acme");
  });

  it("returns undefined for an unknown slug", async () => {
    const { result } = renderHook(() => useNamespace("ghost"), {
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.namespace).toBeUndefined();
  });

  it("returns undefined when no slug is provided", async () => {
    const { result } = renderHook(() => useNamespace(null), {
      wrapper: createQueryWrapper(),
    });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.namespace).toBeUndefined();
  });
});
