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
import type { AxiosError } from "axios";

const getAccessToken = vi.fn<() => string | null>();
const setAuth = vi.fn();
const clearAuth = vi.fn();
const refreshAccessToken = vi.fn();

vi.mock("../stores/authStore", () => ({
  useAuthStore: {
    getState: () => ({
      accessToken: getAccessToken(),
      setAuth,
      clearAuth,
    }),
  },
}));

vi.mock("./refresh", () => ({
  refreshAccessToken: () => refreshAccessToken(),
}));

import { axiosInstance } from "./config";

type RequestFulfilled = (config: {
  headers: Record<string, string>;
}) => Promise<{ headers: Record<string, string> }>;

type ResponseRejected = (error: unknown) => Promise<unknown>;

function requestInterceptor(): RequestFulfilled {
  return (
    axiosInstance.interceptors.request as unknown as {
      handlers: { fulfilled: RequestFulfilled }[];
    }
  ).handlers[0].fulfilled;
}

function responseRejected(): ResponseRejected {
  return (
    axiosInstance.interceptors.response as unknown as {
      handlers: { rejected: ResponseRejected }[];
    }
  ).handlers[0].rejected;
}

function axiosError(
  status: number | undefined,
  config: Record<string, unknown> | undefined,
): AxiosError {
  return {
    isAxiosError: true,
    name: "AxiosError",
    message: "boom",
    config,
    response: status === undefined ? undefined : { status },
  } as unknown as AxiosError;
}

describe("axiosInstance request interceptor", () => {
  beforeEach(() => {
    getAccessToken.mockReset();
    setAuth.mockReset();
    clearAuth.mockReset();
    refreshAccessToken.mockReset();
  });

  it("attaches a Bearer header when a token is present", async () => {
    getAccessToken.mockReturnValue("tok-123");
    const out = await requestInterceptor()({ headers: {} });
    expect(out.headers["Authorization"]).toBe("Bearer tok-123");
  });

  it("leaves headers untouched when no token is present", async () => {
    getAccessToken.mockReturnValue(null);
    const out = await requestInterceptor()({ headers: {} });
    expect(out.headers["Authorization"]).toBeUndefined();
  });
});

describe("axiosInstance response interceptor (401 refresh-retry)", () => {
  const reject = () => responseRejected();

  beforeEach(() => {
    getAccessToken.mockReset();
    setAuth.mockReset();
    clearAuth.mockReset();
    refreshAccessToken.mockReset();
    // A plain, mutable stand-in: the redirect path assigns a *relative* href
    // ("/login"), which a real `URL` instance would reject as invalid.
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { pathname: "/catalog", href: "http://localhost:3000/catalog" },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("rejects non-401 errors without refreshing", async () => {
    const err = axiosError(500, { url: "/x" });
    await expect(reject()(err)).rejects.toBe(err);
    expect(refreshAccessToken).not.toHaveBeenCalled();
  });

  it("rejects when there is no original config", async () => {
    const err = axiosError(401, undefined);
    await expect(reject()(err)).rejects.toBe(err);
    expect(refreshAccessToken).not.toHaveBeenCalled();
  });

  it("rejects when a retry was already attempted", async () => {
    const err = axiosError(401, { url: "/x", _retryAttempted: true });
    await expect(reject()(err)).rejects.toBe(err);
    expect(refreshAccessToken).not.toHaveBeenCalled();
  });

  it("does not retry the refresh endpoint itself", async () => {
    const err = axiosError(401, { url: "/auth/refresh" });
    await expect(reject()(err)).rejects.toBe(err);
    expect(refreshAccessToken).not.toHaveBeenCalled();
  });

  it("does not retry the login endpoint", async () => {
    const err = axiosError(401, { url: "/auth/login" });
    await expect(reject()(err)).rejects.toBe(err);
    expect(refreshAccessToken).not.toHaveBeenCalled();
  });

  it("refreshes then replays the original request on success", async () => {
    refreshAccessToken.mockResolvedValue({
      accessToken: "fresh",
      userId: "u1",
      displayName: "U",
      email: "u@e.test",
      source: "INTERNAL",
      passwordChangeRequired: false,
      isSuperadmin: false,
    });
    const replay = vi
      .spyOn(axiosInstance, "request")
      .mockResolvedValue({ data: "ok" });
    const err = axiosError(401, { url: "/catalog/plugins", headers: {} });

    const result = await reject()(err);

    expect(setAuth).toHaveBeenCalledOnce();
    expect(replay).toHaveBeenCalledOnce();
    expect((result as { data: string }).data).toBe("ok");
    const replayed = replay.mock.calls[0][0] as {
      _retryAttempted?: boolean;
      headers: Record<string, string>;
    };
    expect(replayed._retryAttempted).toBe(true);
    expect(replayed.headers.Authorization).toBe("Bearer fresh");
  });

  it("clears auth and redirects to /login when refresh yields nothing", async () => {
    refreshAccessToken.mockResolvedValue(null);
    const err = axiosError(401, { url: "/catalog/plugins", headers: {} });

    await expect(reject()(err)).rejects.toBe(err);

    expect(clearAuth).toHaveBeenCalledOnce();
    expect(window.location.href).toBe("/login");
    expect(setAuth).not.toHaveBeenCalled();
  });

  it("clears auth without redirect when already on /login", async () => {
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { pathname: "/login", href: "http://localhost:3000/login" },
    });
    refreshAccessToken.mockResolvedValue(null);
    const err = axiosError(401, { url: "/catalog/plugins", headers: {} });

    await expect(reject()(err)).rejects.toBe(err);

    expect(clearAuth).toHaveBeenCalledOnce();
    expect(window.location.pathname).toBe("/login");
  });

  it("clears auth and rejects when the refresh call throws", async () => {
    const boom = new Error("refresh exploded");
    refreshAccessToken.mockRejectedValue(boom);
    const err = axiosError(401, { url: "/catalog/plugins", headers: {} });

    await expect(reject()(err)).rejects.toBe(boom);
    expect(clearAuth).toHaveBeenCalledOnce();
  });
});
