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
import { createQueryWrapper } from "../../test/queryWrapper";

vi.mock("../config", () => ({
  catalogApi: { listPlugins: vi.fn() },
  axiosInstance: { get: vi.fn() },
  managementApi: {},
  reviewsApi: {},
  namespacesApi: {},
}));

import { catalogApi, axiosInstance } from "../config";
import {
  pluginsKeys,
  usePlugins,
  usePluginTags,
  usePluginPendingReviewCount,
  type PluginFilters,
} from "./usePlugins";

const mockCatalogApi = catalogApi as unknown as {
  listPlugins: ReturnType<typeof vi.fn>;
};
const mockAxios = axiosInstance as unknown as {
  get: ReturnType<typeof vi.fn>;
};

const defaultFilters: PluginFilters = {
  search: "",
  tag: "",
  status: "",
  version: "",
  sort: "name,asc",
  page: 0,
  size: 24,
};

describe("pluginsKeys", () => {
  it("namespace key includes the slug", () => {
    expect(pluginsKeys.namespace("acme")).toEqual(["plugins", "ns", "acme"]);
  });

  it("list key embeds the full filters object for cache isolation", () => {
    const filters = { ...defaultFilters, search: "auth", page: 2 };
    expect(pluginsKeys.list("acme", filters)).toEqual([
      "plugins",
      "ns",
      "acme",
      "list",
      filters,
    ]);
  });

  it("different filter objects produce different keys", () => {
    const a = pluginsKeys.list("acme", defaultFilters);
    const b = pluginsKeys.list("acme", { ...defaultFilters, search: "x" });
    expect(a).not.toEqual(b);
  });

  it("tags key is scoped to the namespace", () => {
    expect(pluginsKeys.tags("acme")).toEqual(["plugins", "ns", "acme", "tags"]);
  });
});

describe("usePlugins", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns paged plugin data on success", async () => {
    mockCatalogApi.listPlugins.mockResolvedValue({
      data: {
        content: [{ pluginId: "auth-plugin", name: "Auth" }],
        totalElements: 1,
        totalPages: 1,
      },
    });

    const { result } = renderHook(() => usePlugins("acme", defaultFilters), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.totalElements).toBe(1);
    expect(result.current.data?.content).toHaveLength(1);
  });

  it("propagates filters to listPlugins", async () => {
    mockCatalogApi.listPlugins.mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0 },
    });

    const filters: PluginFilters = {
      ...defaultFilters,
      search: "cache",
      tag: "storage",
      page: 1,
      size: 12,
    };

    const { result } = renderHook(() => usePlugins("acme", filters), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockCatalogApi.listPlugins).toHaveBeenCalledWith(
      expect.objectContaining({
        ns: "acme",
        q: "cache",
        tag: "storage",
        page: 1,
        size: 12,
      }),
    );
  });

  it("omits empty filter values from the API call", async () => {
    mockCatalogApi.listPlugins.mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0 },
    });

    const { result } = renderHook(() => usePlugins("acme", defaultFilters), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const callArgs = mockCatalogApi.listPlugins.mock.calls[0][0];
    expect(callArgs.q).toBeUndefined();
    expect(callArgs.tag).toBeUndefined();
    expect(callArgs.status).toBeUndefined();
    expect(callArgs.version).toBeUndefined();
  });

  it("does not fetch when namespace is empty", () => {
    renderHook(() => usePlugins("", defaultFilters), {
      wrapper: createQueryWrapper(),
    });
    expect(mockCatalogApi.listPlugins).not.toHaveBeenCalled();
  });

  it("exposes the error when the request fails", async () => {
    mockCatalogApi.listPlugins.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => usePlugins("acme", defaultFilters), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect((result.current.error as Error).message).toBe("Network error");
  });
});

describe("usePluginTags", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns the tag list on success", async () => {
    mockAxios.get.mockResolvedValue({ data: ["auth", "cache", "security"] });

    const { result } = renderHook(() => usePluginTags("acme"), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockAxios.get).toHaveBeenCalledWith("/namespaces/acme/tags");
    expect(result.current.data).toEqual(["auth", "cache", "security"]);
  });

  it("does not fetch when namespace is empty", () => {
    renderHook(() => usePluginTags(""), { wrapper: createQueryWrapper() });
    expect(mockAxios.get).not.toHaveBeenCalled();
  });

  it("surfaces the error — consumers fall back to [] via data ?? []", async () => {
    mockAxios.get.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => usePluginTags("acme"), {
      wrapper: createQueryWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.data).toBeUndefined();
  });
});

describe("usePluginPendingReviewCount", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("extracts counts from the list response", async () => {
    mockCatalogApi.listPlugins.mockResolvedValue({
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        pendingReviewPluginCount: 3,
        pendingReviewReleaseCount: 7,
      },
    });

    const { result } = renderHook(
      () => usePluginPendingReviewCount("acme", defaultFilters),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(result.current.pluginCount).toBe(3));
    expect(result.current.releaseCount).toBe(7);
  });

  it("returns null counts while the list query is pending", () => {
    mockCatalogApi.listPlugins.mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(
      () => usePluginPendingReviewCount("acme", defaultFilters),
      { wrapper: createQueryWrapper() },
    );

    expect(result.current.pluginCount).toBeNull();
    expect(result.current.releaseCount).toBeNull();
  });

  it("returns null counts when backend omits them (unauthenticated)", async () => {
    mockCatalogApi.listPlugins.mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0 },
    });

    const { result } = renderHook(
      () => usePluginPendingReviewCount("acme", defaultFilters),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(mockCatalogApi.listPlugins).toHaveBeenCalled());
    expect(result.current.pluginCount).toBeNull();
    expect(result.current.releaseCount).toBeNull();
  });
});
