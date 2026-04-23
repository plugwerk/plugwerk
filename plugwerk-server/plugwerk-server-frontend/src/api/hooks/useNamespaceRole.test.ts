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
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { AxiosError, AxiosHeaders } from "axios";
import { createQueryWrapper } from "../../test/queryWrapper";

vi.mock("../config", () => ({
  namespaceMembersApi: { getMyMembership: vi.fn() },
  catalogApi: {},
  managementApi: {},
  reviewsApi: {},
  namespacesApi: {},
  axiosInstance: {},
}));

import { namespaceMembersApi } from "../config";
import { useAuthStore } from "../../stores/authStore";
import { namespaceRoleKeys, useNamespaceRole } from "./useNamespaceRole";

const mockGetMyMembership =
  namespaceMembersApi.getMyMembership as unknown as ReturnType<typeof vi.fn>;

function setAuth(
  partial: Partial<{ isAuthenticated: boolean; isHydrating: boolean }>,
) {
  useAuthStore.setState({
    isAuthenticated: true,
    isHydrating: false,
    ...partial,
  });
}

function makeAxiosError(status: number): AxiosError {
  const err = new AxiosError("request failed");
  err.response = {
    status,
    statusText: "",
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: undefined,
  };
  return err;
}

describe("namespaceRoleKeys", () => {
  it("byNamespace embeds the slug for cache isolation", () => {
    expect(namespaceRoleKeys.byNamespace("acme")).toEqual([
      "namespace-role",
      "acme",
    ]);
  });

  it("produces different keys for different slugs", () => {
    expect(namespaceRoleKeys.byNamespace("a")).not.toEqual(
      namespaceRoleKeys.byNamespace("b"),
    );
  });
});

describe("useNamespaceRole", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setAuth({});
  });

  it("returns membership on 200", async () => {
    mockGetMyMembership.mockResolvedValue({ data: { role: "ADMIN" } });

    const { result } = renderHook(() => useNamespaceRole("acme"), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.role).toBe("ADMIN");
    expect(mockGetMyMembership).toHaveBeenCalledWith({ ns: "acme" });
  });

  it("returns null (not error) on 404 — user is not a member", async () => {
    mockGetMyMembership.mockRejectedValue(makeAxiosError(404));

    const { result } = renderHook(() => useNamespaceRole("acme"), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBeNull();
    expect(result.current.isError).toBe(false);
  });

  it("surfaces the error on 500", async () => {
    mockGetMyMembership.mockRejectedValue(makeAxiosError(500));

    const { result } = renderHook(() => useNamespaceRole("acme"), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.data).toBeUndefined();
  });

  it("does not fetch when slug is null", () => {
    renderHook(() => useNamespaceRole(null), {
      wrapper: createQueryWrapper(),
    });
    expect(mockGetMyMembership).not.toHaveBeenCalled();
  });

  it("does not fetch when slug is an empty string", () => {
    renderHook(() => useNamespaceRole(""), {
      wrapper: createQueryWrapper(),
    });
    expect(mockGetMyMembership).not.toHaveBeenCalled();
  });

  it("does not fetch when not authenticated", () => {
    setAuth({ isAuthenticated: false });

    renderHook(() => useNamespaceRole("acme"), {
      wrapper: createQueryWrapper(),
    });
    expect(mockGetMyMembership).not.toHaveBeenCalled();
  });

  it("does not fetch while hydrating (refresh-cookie call in flight)", () => {
    setAuth({ isHydrating: true });

    renderHook(() => useNamespaceRole("acme"), {
      wrapper: createQueryWrapper(),
    });
    expect(mockGetMyMembership).not.toHaveBeenCalled();
  });

  it("isolates cache entries by namespace", async () => {
    mockGetMyMembership
      .mockResolvedValueOnce({ data: { role: "ADMIN" } })
      .mockResolvedValueOnce({ data: { role: "MEMBER" } });

    const wrapper = createQueryWrapper();
    const { result: acmeResult } = renderHook(() => useNamespaceRole("acme"), {
      wrapper,
    });
    const { result: betaResult } = renderHook(() => useNamespaceRole("beta"), {
      wrapper,
    });

    await waitFor(() => {
      expect(acmeResult.current.data?.role).toBe("ADMIN");
      expect(betaResult.current.data?.role).toBe("MEMBER");
    });
    expect(mockGetMyMembership).toHaveBeenCalledTimes(2);
  });
});
