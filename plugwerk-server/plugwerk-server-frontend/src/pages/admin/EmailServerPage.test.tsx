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
import { EmailServerPage } from "./EmailServerPage";
import { renderWithTheme } from "../../test/renderWithTheme";
import { useSettingsStore } from "../../stores/settingsStore";
import { useUiStore } from "../../stores/uiStore";
import { useAuthStore } from "../../stores/authStore";
import type { ApplicationSettingDto } from "../../api/generated/model/application-setting-dto";
import * as apiConfig from "../../api/config";

vi.mock("../../api/config", () => ({
  adminSettingsApi: {
    listApplicationSettings: vi.fn(),
    updateApplicationSettings: vi.fn(),
  },
  adminEmailApi: {
    sendTestEmail: vi.fn(),
  },
}));

function dto(overrides: Partial<ApplicationSettingDto>): ApplicationSettingDto {
  return {
    key: "smtp.host",
    value: "",
    valueType: "STRING",
    source: "DATABASE",
    requiresRestart: false,
    restartPending: false,
    ...overrides,
  } as ApplicationSettingDto;
}

const SMTP_SETTINGS: ApplicationSettingDto[] = [
  dto({
    key: "smtp.enabled",
    value: "true",
    valueType: "BOOLEAN",
    description: "Master switch.",
  }),
  dto({
    key: "smtp.host",
    value: "smtp.example.com",
    description: "SMTP server hostname.",
  }),
  dto({
    key: "smtp.port",
    value: "587",
    valueType: "INTEGER",
    minInt: 1,
    maxInt: 65535,
  }),
  dto({ key: "smtp.username", value: "" }),
  dto({
    key: "smtp.password",
    value: "***",
    valueType: "PASSWORD",
    description: "Stored encrypted at rest.",
  }),
  dto({
    key: "smtp.encryption",
    value: "starttls",
    valueType: "ENUM",
    allowedValues: ["none", "starttls", "tls"],
  }),
  dto({ key: "smtp.from_address", value: "noreply@plugwerk.test" }),
  dto({ key: "smtp.from_name", value: "Plugwerk" }),
];

describe("EmailServerPage", () => {
  beforeEach(() => {
    useSettingsStore.setState({
      settings: SMTP_SETTINGS,
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    useUiStore.setState({ toasts: [] });
    useAuthStore.setState({
      isAuthenticated: true,
      email: "admin@plugwerk.test",
      username: "admin",
      isSuperadmin: true,
    } as Partial<ReturnType<typeof useAuthStore.getState>> as ReturnType<
      typeof useAuthStore.getState
    >);
    vi.mocked(
      apiConfig.adminSettingsApi.updateApplicationSettings,
    ).mockResolvedValue({
      data: { settings: SMTP_SETTINGS },
    } as Awaited<
      ReturnType<typeof apiConfig.adminSettingsApi.updateApplicationSettings>
    >);
    vi.clearAllMocks();
  });

  it("renders the password field as type=password and shows the masked value (#253)", () => {
    renderWithTheme(<EmailServerPage />);
    const passwordField = screen.getByLabelText("Password") as HTMLInputElement;
    expect(passwordField.type).toBe("password");
    expect(passwordField.value).toBe("***");
  });

  it("toggles password visibility when the show/hide button is clicked", async () => {
    const user = userEvent.setup();
    renderWithTheme(<EmailServerPage />);
    const passwordField = screen.getByLabelText("Password") as HTMLInputElement;
    expect(passwordField.type).toBe("password");

    await user.click(screen.getByRole("button", { name: /show password/i }));
    expect(passwordField.type).toBe("text");

    await user.click(screen.getByRole("button", { name: /hide password/i }));
    expect(passwordField.type).toBe("password");
  });

  it("disables the Test button when SMTP is disabled", async () => {
    useSettingsStore.setState({
      settings: SMTP_SETTINGS.map((s) =>
        s.key === "smtp.enabled" ? { ...s, value: "false" } : s,
      ),
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    renderWithTheme(<EmailServerPage />);

    const testButton = screen.getByRole("button", { name: /send test email/i });
    expect(testButton).toBeDisabled();
  });

  it("calls adminEmailApi.sendTestEmail with the recipient and surfaces success", async () => {
    const user = userEvent.setup();
    vi.mocked(apiConfig.adminEmailApi.sendTestEmail).mockResolvedValue({
      data: { message: "Test email sent to ada@example.test" },
    } as Awaited<ReturnType<typeof apiConfig.adminEmailApi.sendTestEmail>>);

    renderWithTheme(<EmailServerPage />);

    const recipient = screen.getByLabelText(
      "Test recipient",
    ) as HTMLInputElement;
    // userEmail default may have populated the field; clear and re-type so
    // we exercise the click path explicitly.
    await user.clear(recipient);
    await user.type(recipient, "ada@example.test");

    await user.click(screen.getByRole("button", { name: /send test email/i }));

    await waitFor(() => {
      expect(apiConfig.adminEmailApi.sendTestEmail).toHaveBeenCalledWith({
        sendTestEmailRequest: { target: "ada@example.test" },
      });
    });
    expect(
      await screen.findByText(/test email sent to ada@example\.test/i),
    ).toBeInTheDocument();
  });

  it("surfaces server error message when sendTestEmail fails", async () => {
    const user = userEvent.setup();
    const error = Object.assign(new Error("Network Error"), {
      isAxiosError: true,
      response: { data: { message: "SMTP server rejected: auth failed" } },
    });
    vi.mocked(apiConfig.adminEmailApi.sendTestEmail).mockRejectedValue(error);

    renderWithTheme(<EmailServerPage />);

    const recipient = screen.getByLabelText(
      "Test recipient",
    ) as HTMLInputElement;
    await user.clear(recipient);
    await user.type(recipient, "ada@example.test");

    await user.click(screen.getByRole("button", { name: /send test email/i }));

    expect(
      await screen.findByText(/smtp server rejected: auth failed/i),
    ).toBeInTheDocument();
  });

  it("renders the encryption select with human-readable labels", () => {
    renderWithTheme(<EmailServerPage />);
    // The currently-selected option's text should be the friendly label,
    // not the raw enum value — STARTTLS-on-587 is what the operator reads.
    expect(screen.getByText(/STARTTLS \(port 587\)/)).toBeInTheDocument();
  });
});
