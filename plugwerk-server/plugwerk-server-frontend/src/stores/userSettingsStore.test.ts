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
import { AxiosError } from "axios";
import { useUserSettingsStore } from "./userSettingsStore";
import * as apiConfig from "../api/config";

vi.mock("../api/config", () => ({
  userSettingsApi: {
    getUserSettings: vi.fn(),
    updateUserSettings: vi.fn(),
  },
}));

const getUserSettings = () =>
  vi.mocked(apiConfig.userSettingsApi.getUserSettings);
const updateUserSettings = () =>
  vi.mocked(apiConfig.userSettingsApi.updateUserSettings);

describe("userSettingsStore", () => {
  beforeEach(() => {
    useUserSettingsStore.getState().reset();
    getUserSettings().mockReset();
    updateUserSettings().mockReset();
  });

  it("has the expected initial state", () => {
    const s = useUserSettingsStore.getState();
    expect(s.settings).toEqual({});
    expect(s.loaded).toBe(false);
    expect(s.loading).toBe(false);
    expect(s.saving).toBe(false);
    expect(s.error).toBeNull();
  });

  describe("load", () => {
    it("stores settings and marks loaded on success", async () => {
      getUserSettings().mockResolvedValue({
        data: { settings: { theme: "dark", locale: "de" } },
      } as never);

      await useUserSettingsStore.getState().load();

      const s = useUserSettingsStore.getState();
      expect(s.settings).toEqual({ theme: "dark", locale: "de" });
      expect(s.loaded).toBe(true);
      expect(s.loading).toBe(false);
      expect(s.error).toBeNull();
    });

    it("records the server error message and rethrows on failure", async () => {
      const err = new AxiosError("Request failed");
      err.response = {
        data: { message: "settings unavailable" },
        status: 500,
        statusText: "Internal Server Error",
        headers: {},
        config: {} as never,
      };
      getUserSettings().mockRejectedValue(err);

      await expect(useUserSettingsStore.getState().load()).rejects.toBe(err);

      const s = useUserSettingsStore.getState();
      expect(s.error).toBe("settings unavailable");
      expect(s.loaded).toBe(true);
      expect(s.loading).toBe(false);
    });

    it("falls back to the axios error message when the body has no message", async () => {
      const err = new AxiosError("network down");
      getUserSettings().mockRejectedValue(err);

      await expect(useUserSettingsStore.getState().load()).rejects.toBe(err);
      expect(useUserSettingsStore.getState().error).toBe("network down");
    });

    it("uses a plain Error message for non-axios failures", async () => {
      getUserSettings().mockRejectedValue(new Error("boom"));

      await expect(useUserSettingsStore.getState().load()).rejects.toThrow(
        "boom",
      );
      expect(useUserSettingsStore.getState().error).toBe("boom");
    });

    it("reports Unknown error for a non-Error rejection", async () => {
      getUserSettings().mockRejectedValue("just a string");

      await expect(useUserSettingsStore.getState().load()).rejects.toBe(
        "just a string",
      );
      expect(useUserSettingsStore.getState().error).toBe("Unknown error");
    });
  });

  describe("update", () => {
    it("stores the returned settings and clears saving on success", async () => {
      updateUserSettings().mockResolvedValue({
        data: { settings: { theme: "light" } },
      } as never);

      await useUserSettingsStore.getState().update({ theme: "light" });

      const s = useUserSettingsStore.getState();
      expect(s.settings).toEqual({ theme: "light" });
      expect(s.saving).toBe(false);
      expect(s.error).toBeNull();
      expect(updateUserSettings()).toHaveBeenCalledWith({
        userSettingsUpdateRequest: { settings: { theme: "light" } },
      });
    });

    it("records the error and rethrows on failure", async () => {
      updateUserSettings().mockRejectedValue(new Error("save failed"));

      await expect(
        useUserSettingsStore.getState().update({ theme: "x" }),
      ).rejects.toThrow("save failed");

      const s = useUserSettingsStore.getState();
      expect(s.error).toBe("save failed");
      expect(s.saving).toBe(false);
    });
  });

  it("reset returns the store to its initial state", async () => {
    getUserSettings().mockResolvedValue({
      data: { settings: { a: "b" } },
    } as never);
    await useUserSettingsStore.getState().load();

    useUserSettingsStore.getState().reset();

    const s = useUserSettingsStore.getState();
    expect(s.settings).toEqual({});
    expect(s.loaded).toBe(false);
    expect(s.error).toBeNull();
  });
});
