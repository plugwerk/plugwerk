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
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider, CssBaseline } from "@mui/material";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { VerifyEmailPendingPage } from "./VerifyEmailPendingPage";
import { buildTheme } from "../theme/theme";

function renderAt(initialState?: { email?: string }) {
  const theme = buildTheme("light");
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <MemoryRouter
          initialEntries={[
            {
              pathname: "/onboarding/verify-email",
              state: initialState ?? null,
            },
          ]}
        >
          <VerifyEmailPendingPage />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe("VerifyEmailPendingPage", () => {
  it("renders 'Check your inbox' heading and a back-to-login link", () => {
    renderAt();
    expect(screen.getByText("Check your inbox")).toBeInTheDocument();
    const backLink = screen.getByRole("link", { name: /back to login/i });
    expect(backLink).toHaveAttribute("href", "/login");
  });

  it("renders the email passed via router state", () => {
    renderAt({ email: "alice@example.com" });
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
  });

  it("falls back to a generic copy when no email is in router state", () => {
    renderAt();
    expect(screen.getByText(/the address you provided/i)).toBeInTheDocument();
  });
});
