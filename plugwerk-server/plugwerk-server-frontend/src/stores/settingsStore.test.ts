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
import { useSettingsStore } from "./settingsStore";
import * as apiConfig from "../api/config";
import type { ApplicationSettingDto } from "../api/generated/model/application-setting-dto";

vi.mock("../api/config", () => ({
  adminSettingsApi: {
    listApplicationSettings: vi.fn(),
    updateApplicationSettings: vi.fn(),
  },
}));

function buildDto(
  overrides: Partial<ApplicationSettingDto>,
): ApplicationSettingDto {
  return {
    key: "general.site_name",
    value: "Plugwerk",
    valueType: "STRING",
    source: "DATABASE",
    requiresRestart: false,
    restartPending: false,
    ...overrides,
  } as ApplicationSettingDto;
}

describe("settingsStore", () => {
  beforeEach(() => {
    useSettingsStore.setState({
      settings: [],
      loaded: false,
      loading: false,
      saving: false,
      error: null,
    });
    vi.mocked(apiConfig.adminSettingsApi.listApplicationSettings).mockReset();
    vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings).mockReset();
  });

  it("starts with empty unloaded state", () => {
    const state = useSettingsStore.getState();
    expect(state.settings).toEqual([]);
    expect(state.loaded).toBe(false);
    expect(state.loading).toBe(false);
  });

  it("load populates settings from the API", async () => {
    const dto = buildDto({});
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: [dto] } } as never);

    await useSettingsStore.getState().load();

    const state = useSettingsStore.getState();
    expect(state.loaded).toBe(true);
    expect(state.loading).toBe(false);
    expect(state.settings).toEqual([dto]);
    expect(state.error).toBeNull();
  });

  it("load records error and re-throws on failure", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockRejectedValue(new Error("boom"));

    await expect(useSettingsStore.getState().load()).rejects.toThrow("boom");

    const state = useSettingsStore.getState();
    expect(state.loaded).toBe(true);
    expect(state.loading).toBe(false);
    expect(state.error).toBe("boom");
  });

  it("update sends patch and replaces settings with the response", async () => {
    const updated = buildDto({ value: "New Name" });
    vi.mocked(
      apiConfig.adminSettingsApi.updateApplicationSettings,
    ).mockResolvedValue({ data: { settings: [updated] } } as never);

    await useSettingsStore
      .getState()
      .update({ "general.site_name": "New Name" });

    expect(
      vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings),
    ).toHaveBeenCalledWith({
      applicationSettingsUpdateRequest: {
        settings: { "general.site_name": "New Name" },
      },
    });
    expect(useSettingsStore.getState().settings).toEqual([updated]);
    expect(useSettingsStore.getState().saving).toBe(false);
  });

  it("update records error and re-throws", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.updateApplicationSettings,
    ).mockRejectedValue(new Error("validation failed"));

    await expect(
      useSettingsStore.getState().update({ "tracking.enabled": "false" }),
    ).rejects.toThrow("validation failed");

    const state = useSettingsStore.getState();
    expect(state.saving).toBe(false);
    expect(state.error).toBe("validation failed");
  });

  it("reset clears all state back to initial", () => {
    useSettingsStore.setState({
      settings: [buildDto({})],
      loaded: true,
      loading: false,
      saving: false,
      error: "stale",
    });

    useSettingsStore.getState().reset();

    const state = useSettingsStore.getState();
    expect(state.settings).toEqual([]);
    expect(state.loaded).toBe(false);
    expect(state.error).toBeNull();
  });
});
