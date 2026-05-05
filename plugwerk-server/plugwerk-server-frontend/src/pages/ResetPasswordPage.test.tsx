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
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider, CssBaseline } from "@mui/material";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ResetPasswordPage } from "./ResetPasswordPage";
import { buildTheme } from "../theme/theme";
import { useConfigStore } from "../stores/configStore";
import { useUiStore } from "../stores/uiStore";
import * as apiConfig from "../api/config";

vi.mock("../api/config", () => ({
  authPasswordResetApi: {
    forgotPassword: vi.fn(),
    resetPassword: vi.fn(),
  },
  axiosInstance: { get: vi.fn().mockResolvedValue({ data: {} }) },
}));

const STRONG_PASSWORD = "correct-horse-battery-staple";

function renderAt(query: string) {
  const theme = buildTheme("light");
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <MemoryRouter initialEntries={[`/reset-password${query}`]}>
          <Routes>
            <Route path="/reset-password" element={<ResetPasswordPage />} />
            <Route path="/login" element={<div>login page</div>} />
            <Route path="/forgot-password" element={<div>forgot page</div>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe("ResetPasswordPage", () => {
  beforeEach(() => {
    useConfigStore.setState({
      passwordResetEnabled: true,
      loaded: true,
    } as never);
    useUiStore.setState({ toasts: [] });
    vi.clearAllMocks();
  });

  it("renders the no-token branch when ?token= is absent", () => {
    renderAt("");
    expect(screen.getByText(/No reset token/i)).toBeInTheDocument();
    expect(apiConfig.authPasswordResetApi.resetPassword).not.toHaveBeenCalled();
  });

  it("renders the disabled-on-server card when the /config flag is false", () => {
    useConfigStore.setState({
      passwordResetEnabled: false,
      loaded: true,
    } as never);
    renderAt("?token=abc-123");
    expect(screen.getByText(/Password reset unavailable/i)).toBeInTheDocument();
    expect(screen.queryByLabelText("New Password")).not.toBeInTheDocument();
  });

  it("blocks submit when the password is shorter than 12 chars (matches backend rule)", async () => {
    const user = userEvent.setup();
    renderAt("?token=abc-123");

    await user.type(screen.getByLabelText("New Password"), "short");
    await user.type(screen.getByLabelText("Confirm Password"), "short");
    await user.click(screen.getByRole("button", { name: /set password/i }));

    expect(
      await screen.findByText(/at least 12 characters/i),
    ).toBeInTheDocument();
    expect(apiConfig.authPasswordResetApi.resetPassword).not.toHaveBeenCalled();
  });

  it("blocks submit when the confirmation does not match", async () => {
    const user = userEvent.setup();
    renderAt("?token=abc-123");

    await user.type(screen.getByLabelText("New Password"), STRONG_PASSWORD);
    await user.type(
      screen.getByLabelText("Confirm Password"),
      `${STRONG_PASSWORD}-typo`,
    );
    await user.click(screen.getByRole("button", { name: /set password/i }));

    expect(
      await screen.findByText(/passwords do not match/i),
    ).toBeInTheDocument();
    expect(apiConfig.authPasswordResetApi.resetPassword).not.toHaveBeenCalled();
  });

  it("submits the form, calls the API with the URL token, and redirects to /login on success", async () => {
    vi.mocked(apiConfig.authPasswordResetApi.resetPassword).mockResolvedValue({
      status: 204,
      data: undefined,
    } as Awaited<
      ReturnType<typeof apiConfig.authPasswordResetApi.resetPassword>
    >);
    const user = userEvent.setup();
    renderAt("?token=abc-123");

    await user.type(screen.getByLabelText("New Password"), STRONG_PASSWORD);
    await user.type(screen.getByLabelText("Confirm Password"), STRONG_PASSWORD);
    await user.click(screen.getByRole("button", { name: /set password/i }));

    await waitFor(() => {
      expect(apiConfig.authPasswordResetApi.resetPassword).toHaveBeenCalledWith(
        {
          resetPasswordRequest: {
            token: "abc-123",
            newPassword: STRONG_PASSWORD,
          },
        },
      );
    });
    expect(await screen.findByText("login page")).toBeInTheDocument();
    // Confirmation toast is enqueued for the login page to render.
    expect(useUiStore.getState().toasts).toHaveLength(1);
    expect(useUiStore.getState().toasts[0].message).toMatch(
      /password updated/i,
    );
  });

  it("surfaces the server message when the token is invalid (400)", async () => {
    vi.mocked(apiConfig.authPasswordResetApi.resetPassword).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 400,
        data: { message: "Password-reset token has already been used" },
      },
      message: "Bad Request",
    });
    const user = userEvent.setup();
    renderAt("?token=stale");

    await user.type(screen.getByLabelText("New Password"), STRONG_PASSWORD);
    await user.type(screen.getByLabelText("Confirm Password"), STRONG_PASSWORD);
    await user.click(screen.getByRole("button", { name: /set password/i }));

    expect(
      await screen.findByText(/has already been used/i),
    ).toBeInTheDocument();
  });
});
