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
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GeneralSection } from "./GeneralSection";
import { renderWithTheme } from "../../test/renderWithTheme";
import { useSettingsStore } from "../../stores/settingsStore";
import { useUiStore } from "../../stores/uiStore";
import type { ApplicationSettingDto } from "../../api/generated/model/application-setting-dto";
import * as apiConfig from "../../api/config";

vi.mock("../../api/config", () => ({
  adminSettingsApi: {
    listApplicationSettings: vi.fn(),
    updateApplicationSettings: vi.fn(),
  },
}));

function dto(overrides: Partial<ApplicationSettingDto>): ApplicationSettingDto {
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

const SAMPLE_SETTINGS: ApplicationSettingDto[] = [
  dto({
    key: "general.site_name",
    value: "Plugwerk",
    description: "Display name of this Plugwerk instance.",
  }),
  dto({
    key: "general.default_language",
    value: "en",
    valueType: "ENUM",
    allowedValues: ["en", "de"],
    description: "Default UI language.",
  }),
  dto({
    key: "upload.max_file_size_mb",
    value: "100",
    valueType: "INTEGER",
    minInt: 1,
    maxInt: 1024,
    requiresRestart: true,
    description: "Maximum plugin artifact upload size in MB.",
  }),
  dto({
    key: "tracking.enabled",
    value: "true",
    valueType: "BOOLEAN",
    description: "Master switch for download tracking.",
  }),
];

describe("GeneralSection", () => {
  beforeEach(() => {
    useSettingsStore.setState({
      settings: [],
      loaded: false,
      loading: false,
      saving: false,
      error: null,
    });
    useUiStore.setState({ toasts: [] });
    vi.mocked(apiConfig.adminSettingsApi.listApplicationSettings).mockReset();
    vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings).mockReset();
  });

  it("loads settings on mount and renders description as helper text", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({
      data: { settings: SAMPLE_SETTINGS },
    } as never);

    renderWithTheme(<GeneralSection />);

    await waitFor(() => {
      expect(screen.getByLabelText("Site Name")).toBeInTheDocument();
    });
    expect(
      screen.getByText("Display name of this Plugwerk instance."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Max File Size Mb")).toHaveValue(100);
    expect(screen.getByLabelText("Enabled")).toBeChecked();
    expect(screen.getByLabelText("Default Language")).toHaveTextContent("en");
  });

  it("shows a 'Requires restart' chip on fields with requiresRestart=true", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({
      data: { settings: SAMPLE_SETTINGS },
    } as never);

    renderWithTheme(<GeneralSection />);

    await waitFor(() => {
      expect(screen.getByLabelText("Max File Size Mb")).toBeInTheDocument();
    });
    const chips = screen.getAllByText(/Requires restart/i);
    expect(chips.length).toBeGreaterThanOrEqual(1);
  });

  it("disables Save Changes until at least one field is edited", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({
      data: { settings: SAMPLE_SETTINGS },
    } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);

    await waitFor(() =>
      expect(screen.getByLabelText("Site Name")).toBeInTheDocument(),
    );
    const saveButton = screen.getByRole("button", { name: /save changes/i });
    expect(saveButton).toBeDisabled();

    const siteName = screen.getByLabelText("Site Name");
    await user.clear(siteName);
    await user.type(siteName, "Acme Plugins");

    expect(saveButton).toBeEnabled();
  });

  it("sends only dirty fields to updateApplicationSettings", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({
      data: { settings: SAMPLE_SETTINGS },
    } as never);
    vi.mocked(
      apiConfig.adminSettingsApi.updateApplicationSettings,
    ).mockResolvedValue({
      data: {
        settings: SAMPLE_SETTINGS.map((s) =>
          s.key === "general.site_name" ? { ...s, value: "Acme Plugins" } : s,
        ),
      },
    } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);

    await waitFor(() =>
      expect(screen.getByLabelText("Site Name")).toBeInTheDocument(),
    );

    const siteName = screen.getByLabelText("Site Name");
    await user.clear(siteName);
    await user.type(siteName, "Acme Plugins");

    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => {
      expect(
        vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings),
      ).toHaveBeenCalledWith({
        applicationSettingsUpdateRequest: {
          settings: { "general.site_name": "Acme Plugins" },
        },
      });
    });
  });

  it("shows a validation error for out-of-range integer and blocks save", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({
      data: { settings: SAMPLE_SETTINGS },
    } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);

    await waitFor(() =>
      expect(screen.getByLabelText("Max File Size Mb")).toBeInTheDocument(),
    );

    const maxSize = screen.getByLabelText("Max File Size Mb");
    await user.clear(maxSize);
    await user.type(maxSize, "9999");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(screen.getByText("Must be <= 1024")).toBeInTheDocument();
    expect(
      vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings),
    ).not.toHaveBeenCalled();
  });

  it("renders a restart-pending alert when any setting has restartPending=true", async () => {
    const settingsWithRestart = SAMPLE_SETTINGS.map((s) =>
      s.key === "upload.max_file_size_mb" ? { ...s, restartPending: true } : s,
    );
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: settingsWithRestart } } as never);

    renderWithTheme(<GeneralSection />);

    await waitFor(() => {
      const alert = screen.getByRole("alert");
      expect(alert).toHaveTextContent("upload.max_file_size_mb");
      expect(alert).toHaveTextContent(/restart/i);
    });
  });
});
