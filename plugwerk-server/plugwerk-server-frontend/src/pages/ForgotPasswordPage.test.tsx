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
import { ForgotPasswordPage } from "./ForgotPasswordPage";
import { useConfigStore } from "../stores/configStore";
import * as apiConfig from "../api/config";

vi.mock("../api/config", () => ({
  authPasswordResetApi: {
    forgotPassword: vi.fn(),
    resetPassword: vi.fn(),
  },
  axiosInstance: { get: vi.fn().mockResolvedValue({ data: {} }) },
}));

describe("ForgotPasswordPage", () => {
  beforeEach(() => {
    useConfigStore.setState({
      passwordResetEnabled: true,
      loaded: true,
    } as never);
    vi.clearAllMocks();
  });

  it("renders the form when password reset is enabled", () => {
    renderWithRouter(<ForgotPasswordPage />);
    expect(screen.getByLabelText("Username or email")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /send reset link/i }),
    ).toBeInTheDocument();
  });

  it("renders the disabled-on-server card when /config flag is false", () => {
    useConfigStore.setState({
      passwordResetEnabled: false,
      loaded: true,
    } as never);
    renderWithRouter(<ForgotPasswordPage />);
    expect(screen.getByText(/Password reset unavailable/i)).toBeInTheDocument();
    expect(
      screen.queryByLabelText("Username or email"),
    ).not.toBeInTheDocument();
  });

  it("submits the form and shows the anti-enumeration confirmation on 204", async () => {
    vi.mocked(apiConfig.authPasswordResetApi.forgotPassword).mockResolvedValue(
      // 204 has no body — axios resolves with status 204 and a void payload.
      // The cast preserves the typed signature without inventing a payload.
      { status: 204, data: undefined } as Awaited<
        ReturnType<typeof apiConfig.authPasswordResetApi.forgotPassword>
      >,
    );
    const user = userEvent.setup();
    renderWithRouter(<ForgotPasswordPage />);

    await user.type(screen.getByLabelText("Username or email"), "alice");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    await waitFor(() => {
      expect(
        apiConfig.authPasswordResetApi.forgotPassword,
      ).toHaveBeenCalledWith({
        forgotPasswordRequest: { usernameOrEmail: "alice" },
      });
    });
    // The confirmation copy must be true in BOTH the success and the
    // silenced-on-collision branches; no language that asserts an email
    // was actually sent.
    expect(
      await screen.findByText(/if an account exists/i),
    ).toBeInTheDocument();
  });

  it("still shows the same confirmation when the server returns an unexpected error (anti-enumeration)", async () => {
    vi.mocked(apiConfig.authPasswordResetApi.forgotPassword).mockRejectedValue({
      isAxiosError: true,
      response: { status: 500, data: {} },
      message: "boom",
    });
    const user = userEvent.setup();
    renderWithRouter(<ForgotPasswordPage />);

    await user.type(screen.getByLabelText("Username or email"), "alice");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    expect(
      await screen.findByText(/if an account exists/i),
    ).toBeInTheDocument();
  });

  it("surfaces a 503 error inline when SMTP is unconfigured server-side", async () => {
    vi.mocked(apiConfig.authPasswordResetApi.forgotPassword).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 503,
        data: { message: "SMTP not configured" },
      },
      message: "Request failed with status code 503",
    });
    const user = userEvent.setup();
    renderWithRouter(<ForgotPasswordPage />);

    await user.type(screen.getByLabelText("Username or email"), "alice");
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    expect(await screen.findByText(/SMTP not configured/i)).toBeInTheDocument();
  });
});
