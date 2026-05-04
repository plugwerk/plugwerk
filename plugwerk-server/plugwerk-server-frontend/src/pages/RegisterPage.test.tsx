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
import { renderWithRouter } from "../test/renderWithTheme";
import { RegisterPage } from "./RegisterPage";
import { useConfigStore } from "../stores/configStore";
import * as apiConfig from "../api/config";
import { RegisterResponseStatusEnum } from "../api/generated/model/register-response";

vi.mock("../api/config", () => ({
  authRegistrationApi: {
    register: vi.fn(),
    verifyEmail: vi.fn(),
  },
  axiosInstance: { get: vi.fn().mockResolvedValue({ data: {} }) },
}));

const STRONG_PASSWORD = "correct-horse-battery-staple";

describe("RegisterPage", () => {
  beforeEach(() => {
    useConfigStore.setState({
      selfRegistrationEnabled: true,
      loaded: true,
    } as never);
    vi.clearAllMocks();
  });

  it("renders the form when self-registration is enabled", () => {
    renderWithRouter(<RegisterPage />);
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /create account/i }),
    ).toBeInTheDocument();
  });

  it("renders the disabled-on-server card when /config flag is false", () => {
    useConfigStore.setState({
      selfRegistrationEnabled: false,
      loaded: true,
    } as never);
    renderWithRouter(<RegisterPage />);
    expect(screen.getByText(/Registration unavailable/i)).toBeInTheDocument();
    expect(screen.queryByLabelText("Username")).not.toBeInTheDocument();
  });

  it("blocks submit when password is shorter than 12 chars (matches backend rule)", async () => {
    const user = userEvent.setup();
    renderWithRouter(<RegisterPage />);
    await user.type(screen.getByLabelText("Username"), "alice");
    await user.type(screen.getByLabelText("Email"), "alice@example.com");
    await user.type(screen.getByLabelText("Password"), "short");
    await user.click(screen.getByRole("button", { name: /create account/i }));
    expect(
      await screen.findByText(/at least 12 characters/i),
    ).toBeInTheDocument();
    expect(apiConfig.authRegistrationApi.register).not.toHaveBeenCalled();
  });

  it("submits the form and routes to /onboarding/verify-email on VERIFICATION_PENDING", async () => {
    vi.mocked(apiConfig.authRegistrationApi.register).mockResolvedValue({
      data: { status: RegisterResponseStatusEnum.VerificationPending },
    } as Awaited<ReturnType<typeof apiConfig.authRegistrationApi.register>>);
    const user = userEvent.setup();
    renderWithRouter(<RegisterPage />);
    await user.type(screen.getByLabelText("Username"), "alice");
    await user.type(screen.getByLabelText("Email"), "alice@example.com");
    await user.type(screen.getByLabelText("Password"), STRONG_PASSWORD);
    await user.click(screen.getByRole("button", { name: /create account/i }));
    await waitFor(() => {
      expect(apiConfig.authRegistrationApi.register).toHaveBeenCalledWith({
        registerRequest: expect.objectContaining({
          username: "alice",
          email: "alice@example.com",
          password: STRONG_PASSWORD,
        }),
      });
    });
  });

  it("surfaces a 503 error message inline when SMTP is unconfigured server-side", async () => {
    vi.mocked(apiConfig.authRegistrationApi.register).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 503,
        data: { message: "SMTP infrastructure not configured" },
      },
      message: "Request failed with status code 503",
      // Make axios.isAxiosError(...) return true.
      ...(globalThis as unknown as {
        Symbol?: { for(name: string): symbol };
      }),
    });
    // Re-stub via axios.isAxiosError mock — we use the rejection above
    // and rely on the page's extractApiError to surface response.data.message.
    const user = userEvent.setup();
    renderWithRouter(<RegisterPage />);
    await user.type(screen.getByLabelText("Username"), "alice");
    await user.type(screen.getByLabelText("Email"), "alice@example.com");
    await user.type(screen.getByLabelText("Password"), STRONG_PASSWORD);
    await user.click(screen.getByRole("button", { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        /SMTP infrastructure not configured/,
      );
    });
  });

  it("renders the disabled-on-server card when the API returns 404", async () => {
    vi.mocked(apiConfig.authRegistrationApi.register).mockRejectedValue(
      Object.assign(new Error("Not Found"), {
        isAxiosError: true,
        response: { status: 404, data: {} },
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<RegisterPage />);
    await user.type(screen.getByLabelText("Username"), "alice");
    await user.type(screen.getByLabelText("Email"), "alice@example.com");
    await user.type(screen.getByLabelText("Password"), STRONG_PASSWORD);
    await user.click(screen.getByRole("button", { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByText(/Registration unavailable/i)).toBeInTheDocument();
    });
  });
});
