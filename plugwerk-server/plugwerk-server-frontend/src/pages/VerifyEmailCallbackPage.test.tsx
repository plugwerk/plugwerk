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
import { ThemeProvider, CssBaseline } from "@mui/material";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { VerifyEmailCallbackPage } from "./VerifyEmailCallbackPage";
import { buildTheme } from "../theme/theme";
import * as apiConfig from "../api/config";

vi.mock("../api/config", () => ({
  authRegistrationApi: {
    verifyEmail: vi.fn(),
  },
  axiosInstance: { get: vi.fn() },
}));

function renderAt(query: string) {
  const theme = buildTheme("light");
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <MemoryRouter initialEntries={[`/verify-email${query}`]}>
          <Routes>
            <Route path="/verify-email" element={<VerifyEmailCallbackPage />} />
            <Route path="/login" element={<div>login page</div>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe("VerifyEmailCallbackPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the missing-token branch when ?token= is absent", () => {
    renderAt("");
    expect(screen.getByText(/No verification token/i)).toBeInTheDocument();
    expect(apiConfig.authRegistrationApi.verifyEmail).not.toHaveBeenCalled();
  });

  it("calls verifyEmail with the URL token and renders the success state", async () => {
    vi.mocked(apiConfig.authRegistrationApi.verifyEmail).mockResolvedValue({
      data: { status: "VERIFIED" },
    } as Awaited<ReturnType<typeof apiConfig.authRegistrationApi.verifyEmail>>);
    renderAt("?token=abc-123");

    await waitFor(() => {
      expect(apiConfig.authRegistrationApi.verifyEmail).toHaveBeenCalledWith({
        token: "abc-123",
      });
    });
    expect(await screen.findByText(/Email verified/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /go to login/i }),
    ).toBeInTheDocument();
  });

  it("surfaces the server message when verifyEmail returns 400 (invalid/expired token)", async () => {
    vi.mocked(apiConfig.authRegistrationApi.verifyEmail).mockRejectedValue(
      Object.assign(new Error("Bad Request"), {
        isAxiosError: true,
        response: {
          status: 400,
          data: { message: "Verification token has expired" },
        },
      }),
    );
    renderAt("?token=stale");
    expect(await screen.findByText(/Verification failed/i)).toBeInTheDocument();
    expect(screen.getByText(/has expired/i)).toBeInTheDocument();
  });
});
