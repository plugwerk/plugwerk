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
  dto({
    key: "auth.self_registration_enabled",
    value: "false",
    valueType: "BOOLEAN",
    description:
      "Master switch for the public /auth/register endpoint. When false the endpoint and the corresponding 'Sign up' link on the login page are hidden (404).",
  }),
  dto({
    key: "auth.self_registration_email_verification_required",
    value: "true",
    valueType: "BOOLEAN",
    description:
      "When true (recommended) the new account stays disabled until the user clicks the link in the verification email.",
  }),
  dto({
    key: "auth.password_reset_enabled",
    value: "false",
    valueType: "BOOLEAN",
    description:
      "Master switch for the public /auth/forgot-password and /auth/reset-password endpoints. When false the endpoints and the corresponding 'Forgot password?' link on the login page are hidden (404).",
  }),
  dto({
    key: "auth.password_reset_token_ttl_minutes",
    value: "60",
    valueType: "INTEGER",
    minInt: 5,
    maxInt: 1440,
    description:
      "How long a password-reset link stays valid, in minutes. Range 5..1440.",
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
    expect(screen.getByLabelText("Default Language")).toHaveTextContent(
      "English",
    );
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

  it("renders both self-registration toggles in the dedicated section", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({
      data: { settings: SAMPLE_SETTINGS },
    } as never);

    renderWithTheme(<GeneralSection />);

    await waitFor(() => {
      expect(screen.getByText("Self-Registration")).toBeInTheDocument();
    });
    const enableToggle = screen.getByLabelText("Self Registration Enabled");
    expect(enableToggle).not.toBeChecked();
    const verifyToggle = screen.getByLabelText(
      "Self Registration Email Verification Required",
    );
    expect(verifyToggle).toBeChecked();
  });

  it("sends the self-registration toggle change to updateApplicationSettings", async () => {
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
          s.key === "auth.self_registration_enabled"
            ? { ...s, value: "true" }
            : s,
        ),
      },
    } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);

    await waitFor(() =>
      expect(
        screen.getByLabelText("Self Registration Enabled"),
      ).toBeInTheDocument(),
    );

    await user.click(screen.getByLabelText("Self Registration Enabled"));
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => {
      expect(
        vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings),
      ).toHaveBeenCalledWith({
        applicationSettingsUpdateRequest: {
          settings: { "auth.self_registration_enabled": "true" },
        },
      });
    });
  });

  it("renders both password-reset settings in the dedicated section (#421)", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({
      data: { settings: SAMPLE_SETTINGS },
    } as never);

    renderWithTheme(<GeneralSection />);

    await waitFor(() => {
      expect(screen.getByText("Password Reset")).toBeInTheDocument();
    });
    const enableToggle = screen.getByLabelText("Password Reset Enabled");
    expect(enableToggle).not.toBeChecked();
    const ttlInput = screen.getByLabelText("Password Reset Token Ttl Minutes");
    expect(ttlInput).toHaveValue(60);
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

  it("shows a loading spinner while settings are being fetched", () => {
    useSettingsStore.setState({ loaded: false, loading: true });
    // load() guard requires !loaded && !loading to fire, so the mock isn't hit.
    renderWithTheme(<GeneralSection />);
    expect(screen.getByText("Loading settings…")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("shows an info alert when no settings are available", () => {
    useSettingsStore.setState({ loaded: true, loading: false, settings: [] });
    renderWithTheme(<GeneralSection />);
    expect(
      screen.getByText("No application settings are available."),
    ).toBeInTheDocument();
  });

  it("shows an error toast when loading settings fails", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockRejectedValue(new Error("network"));

    renderWithTheme(<GeneralSection />);

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) =>
            /Failed to load application settings/i.test(t.message ?? ""),
          ),
      ).toBe(true);
    });
  });

  it("discards edits and re-disables the Save button", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);
    await waitFor(() =>
      expect(screen.getByLabelText("Site Name")).toBeInTheDocument(),
    );

    const siteName = screen.getByLabelText("Site Name");
    await user.clear(siteName);
    await user.type(siteName, "Edited");
    expect(screen.getByRole("button", { name: /save changes/i })).toBeEnabled();

    await user.click(screen.getByRole("button", { name: /discard/i }));

    expect(screen.getByLabelText("Site Name")).toHaveValue("Plugwerk");
    expect(
      screen.getByRole("button", { name: /save changes/i }),
    ).toBeDisabled();
  });

  it("validates a blank string field and blocks save", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);
    await waitFor(() =>
      expect(screen.getByLabelText("Site Name")).toBeInTheDocument(),
    );

    const siteName = screen.getByLabelText("Site Name");
    await user.clear(siteName);
    await user.type(siteName, "   ");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(screen.getByText("Must not be blank")).toBeInTheDocument();
    expect(
      vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings),
    ).not.toHaveBeenCalled();
  });

  it("validates a non-integer value and shows the integer error", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);
    await waitFor(() =>
      expect(screen.getByLabelText("Max File Size Mb")).toBeInTheDocument(),
    );

    const maxSize = screen.getByLabelText("Max File Size Mb");
    await user.clear(maxSize);
    // A number input keeps "1.5" as "1.5"; parseInt → 1, String(1) !== "1.5".
    await user.type(maxSize, "1.5");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(screen.getByText("Must be an integer")).toBeInTheDocument();
  });

  it("validates the integer minimum bound", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);
    await waitFor(() =>
      expect(
        screen.getByLabelText("Password Reset Token Ttl Minutes"),
      ).toBeInTheDocument(),
    );

    const ttl = screen.getByLabelText("Password Reset Token Ttl Minutes");
    await user.clear(ttl);
    await user.type(ttl, "1");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(screen.getByText("Must be >= 5")).toBeInTheDocument();
  });

  it("changes the default language select and sends the change", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
    vi.mocked(
      apiConfig.adminSettingsApi.updateApplicationSettings,
    ).mockResolvedValue({
      data: {
        settings: SAMPLE_SETTINGS.map((s) =>
          s.key === "general.default_language" ? { ...s, value: "de" } : s,
        ),
      },
    } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);
    await waitFor(() =>
      expect(screen.getByLabelText("Default Language")).toBeInTheDocument(),
    );

    await user.click(screen.getByLabelText("Default Language"));
    await user.click(screen.getByRole("option", { name: "de" }));
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => {
      expect(
        vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings),
      ).toHaveBeenCalledWith({
        applicationSettingsUpdateRequest: {
          settings: { "general.default_language": "de" },
        },
      });
    });
  });

  it("renders the default timezone field when the setting is present", async () => {
    // SAMPLE_SETTINGS has no timezone key (so renderField returns null for it);
    // adding it exercises the general.default_timezone branch of renderField.
    const withTz: ApplicationSettingDto[] = [
      ...SAMPLE_SETTINGS,
      dto({
        key: "general.default_timezone",
        value: "Europe/Berlin",
        valueType: "STRING",
        description: "Default display timezone.",
      }),
    ];
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: withTz } } as never);

    renderWithTheme(<GeneralSection />);

    await waitFor(() =>
      expect(screen.getByLabelText("Default Timezone")).toBeInTheDocument(),
    );
    // The TimezoneSelect autocomplete is seeded from the setting value.
    expect(screen.getByDisplayValue(/Europe\/Berlin/i)).toBeInTheDocument();
  });

  it("renders the default language fallback options when allowedValues is absent", async () => {
    const noAllowed = SAMPLE_SETTINGS.map((s) =>
      s.key === "general.default_language"
        ? { ...s, allowedValues: undefined }
        : s,
    );
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: noAllowed } } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);
    await waitFor(() =>
      expect(screen.getByLabelText("Default Language")).toBeInTheDocument(),
    );

    await user.click(screen.getByLabelText("Default Language"));
    // Fallback ["en", "de"] — "de" has no LANGUAGE_LABELS entry so renders raw.
    expect(screen.getByRole("option", { name: "de" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "English" })).toBeInTheDocument();
  });

  it("clears a field-level error when the field is edited again", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
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

    // Editing the same field again must drop the existing error entry
    // (handleFieldChange's key-in-prev branch).
    await user.clear(maxSize);
    await user.type(maxSize, "500");
    expect(screen.queryByText("Must be <= 1024")).not.toBeInTheDocument();
  });

  it("saves valid edits across string, integer, boolean and enum fields", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
    vi.mocked(
      apiConfig.adminSettingsApi.updateApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);
    await waitFor(() =>
      expect(screen.getByLabelText("Site Name")).toBeInTheDocument(),
    );

    // STRING (valid), INTEGER (in range), BOOLEAN, ENUM — walks every
    // valid branch of validateLocally.
    await user.clear(screen.getByLabelText("Site Name"));
    await user.type(screen.getByLabelText("Site Name"), "Acme");
    await user.clear(screen.getByLabelText("Max File Size Mb"));
    await user.type(screen.getByLabelText("Max File Size Mb"), "200");
    await user.click(screen.getByLabelText("Enabled"));
    await user.click(screen.getByLabelText("Default Language"));
    await user.click(screen.getByRole("option", { name: "de" }));

    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => {
      const settings = (
        vi.mocked(apiConfig.adminSettingsApi.updateApplicationSettings).mock
          .calls[0]?.[0] as {
          applicationSettingsUpdateRequest: {
            settings: Record<string, string>;
          };
        }
      ).applicationSettingsUpdateRequest.settings;
      expect(settings["general.site_name"]).toBe("Acme");
      expect(settings["upload.max_file_size_mb"]).toBe("200");
      expect(settings["general.default_language"]).toBe("de");
      expect(settings["tracking.enabled"]).toBe("false");
    });
    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => /Settings saved/i.test(t.message ?? "")),
      ).toBe(true);
    });
  });

  it("surfaces a save failure as an error toast", async () => {
    vi.mocked(
      apiConfig.adminSettingsApi.listApplicationSettings,
    ).mockResolvedValue({ data: { settings: SAMPLE_SETTINGS } } as never);
    vi.mocked(
      apiConfig.adminSettingsApi.updateApplicationSettings,
    ).mockRejectedValue(new Error("Conflict: stale value"));
    const user = userEvent.setup();

    renderWithTheme(<GeneralSection />);
    await waitFor(() =>
      expect(screen.getByLabelText("Site Name")).toBeInTheDocument(),
    );

    const siteName = screen.getByLabelText("Site Name");
    await user.clear(siteName);
    await user.type(siteName, "Acme");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => /Conflict: stale value/i.test(t.message ?? "")),
      ).toBe(true);
    });
  });
});
