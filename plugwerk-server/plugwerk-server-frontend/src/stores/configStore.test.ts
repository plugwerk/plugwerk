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
import { useConfigStore } from "./configStore";
import * as apiConfig from "../api/config";

vi.mock("../api/config", () => ({
  axiosInstance: {
    get: vi.fn(),
  },
}));

describe("configStore", () => {
  beforeEach(() => {
    // Reset store to initial state
    useConfigStore.setState({
      version: "…",
      maxFileSizeMb: 100,
      defaultTimezone: "UTC",
      siteName: "Plugwerk",
      oidcProviders: [],
      loaded: false,
    });
    vi.mocked(apiConfig.axiosInstance.get).mockReset();
  });

  it("has correct initial state", () => {
    const { version, maxFileSizeMb, defaultTimezone, loaded } =
      useConfigStore.getState();
    expect(version).toBe("…");
    expect(maxFileSizeMb).toBe(100);
    expect(defaultTimezone).toBe("UTC");
    expect(loaded).toBe(false);
  });

  it("fetches config and sets version, maxFileSizeMb, defaultTimezone", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: {
        version: "0.1.0-SNAPSHOT",
        upload: { maxFileSizeMb: 200 },
        general: { defaultTimezone: "Europe/Berlin" },
      },
    });

    await useConfigStore.getState().fetchConfig();

    const { version, maxFileSizeMb, defaultTimezone, loaded } =
      useConfigStore.getState();
    expect(version).toBe("0.1.0-SNAPSHOT");
    expect(maxFileSizeMb).toBe(200);
    expect(defaultTimezone).toBe("Europe/Berlin");
    expect(loaded).toBe(true);
  });

  it("falls back to UTC when defaultTimezone is missing from response", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: { version: "1.0.0", upload: { maxFileSizeMb: 100 } },
    });

    await useConfigStore.getState().fetchConfig();

    expect(useConfigStore.getState().defaultTimezone).toBe("UTC");
  });

  it("parses siteName from response when present (#234)", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: {
        version: "1.0.0",
        upload: { maxFileSizeMb: 100 },
        general: { defaultTimezone: "UTC", siteName: "Acme Plugin Hub" },
      },
    });

    await useConfigStore.getState().fetchConfig();

    expect(useConfigStore.getState().siteName).toBe("Acme Plugin Hub");
  });

  it("falls back to Plugwerk when siteName is empty (#234)", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: {
        version: "1.0.0",
        upload: { maxFileSizeMb: 100 },
        general: { defaultTimezone: "UTC", siteName: "" },
      },
    });

    await useConfigStore.getState().fetchConfig();

    expect(useConfigStore.getState().siteName).toBe("Plugwerk");
  });

  it("falls back to Plugwerk when siteName is missing from response (#234)", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: {
        version: "1.0.0",
        upload: { maxFileSizeMb: 100 },
        general: { defaultTimezone: "UTC" },
      },
    });

    await useConfigStore.getState().fetchConfig();

    expect(useConfigStore.getState().siteName).toBe("Plugwerk");
  });

  it("sets version to unknown on fetch error", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockRejectedValue(
      new Error("Network error"),
    );

    await useConfigStore.getState().fetchConfig();

    const { version, loaded } = useConfigStore.getState();
    expect(version).toBe("unknown");
    expect(loaded).toBe(true);
  });

  it("does not fetch again when already loaded", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: { version: "1.0.0", upload: { maxFileSizeMb: 50 } },
    });

    await useConfigStore.getState().fetchConfig();
    await useConfigStore.getState().fetchConfig();

    expect(vi.mocked(apiConfig.axiosInstance.get)).toHaveBeenCalledTimes(1);
  });

  it("falls back to unknown when version is missing from response", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: { upload: { maxFileSizeMb: 100 } },
    });

    await useConfigStore.getState().fetchConfig();

    expect(useConfigStore.getState().version).toBe("unknown");
  });

  it("re-fetches when force is passed even though already loaded", async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: { version: "1.0.0", upload: { maxFileSizeMb: 50 } },
    });

    await useConfigStore.getState().fetchConfig();
    await useConfigStore.getState().fetchConfig({ force: true });

    expect(vi.mocked(apiConfig.axiosInstance.get)).toHaveBeenCalledTimes(2);
  });
});

describe("configStore — OIDC provider parsing", () => {
  beforeEach(() => {
    useConfigStore.setState({ oidcProviders: [], loaded: false });
    vi.mocked(apiConfig.axiosInstance.get).mockReset();
  });

  async function fetchWith(oidcProviders: unknown) {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: {
        version: "1.0.0",
        upload: { maxFileSizeMb: 100 },
        auth: { oidcProviders },
      },
    });
    await useConfigStore.getState().fetchConfig({ force: true });
    return useConfigStore.getState().oidcProviders;
  }

  it("returns an empty list when the payload is not an array", async () => {
    expect(await fetchWith(undefined)).toEqual([]);
    expect(await fetchWith({ not: "an array" })).toEqual([]);
  });

  it("parses a fully-populated provider entry", async () => {
    const providers = await fetchWith([
      {
        id: "gh",
        name: "GitHub",
        loginUrl: "/oauth/github",
        accountPickerLoginUrl: "/oauth/github?prompt=select_account",
        accountSwitchHintUrl: "https://github.com/logout",
        iconKind: "github",
      },
    ]);
    expect(providers).toEqual([
      {
        id: "gh",
        name: "GitHub",
        loginUrl: "/oauth/github",
        accountPickerLoginUrl: "/oauth/github?prompt=select_account",
        accountSwitchHintUrl: "https://github.com/logout",
        iconKind: "github",
      },
    ]);
  });

  it("nulls optional URLs and falls back to oidc icon on unknown/missing fields", async () => {
    const providers = await fetchWith([
      {
        id: "corp",
        name: "Corp SSO",
        loginUrl: "/oauth/corp",
        accountPickerLoginUrl: 42, // non-string -> null
        // accountSwitchHintUrl missing -> null
        iconKind: "totally-unknown", // not a known kind -> "oidc"
      },
    ]);
    expect(providers[0].accountPickerLoginUrl).toBeNull();
    expect(providers[0].accountSwitchHintUrl).toBeNull();
    expect(providers[0].iconKind).toBe("oidc");
  });

  it("skips non-object entries and entries missing required string fields", async () => {
    const providers = await fetchWith([
      null,
      "nope",
      { id: "x", name: "X" }, // missing loginUrl
      { id: 1, name: "Y", loginUrl: "/y" }, // id not a string
      {
        id: "ok",
        name: "OK",
        loginUrl: "/ok",
        iconKind: "google",
      },
    ]);
    expect(providers).toHaveLength(1);
    expect(providers[0].id).toBe("ok");
    expect(providers[0].iconKind).toBe("google");
  });
});
