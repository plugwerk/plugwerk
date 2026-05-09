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
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UsersSection } from "./UsersSection";
import { renderWithTheme } from "../../test/renderWithTheme";
import { useUiStore } from "../../stores/uiStore";
import { useAuthStore } from "../../stores/authStore";
import * as apiConfig from "../../api/config";
import type { UserDto } from "../../api/generated/model";

vi.mock("../../api/config", () => ({
  adminUsersApi: {
    listUsers: vi.fn(),
    createUser: vi.fn(),
    updateUser: vi.fn(),
    deleteUser: vi.fn(),
    adminResetUserPassword: vi.fn(),
  },
}));

const CALLER_ID = "00000000-0000-0000-0000-aaaaaaaaaaaa";

const aliceInternal: UserDto = {
  id: "00000000-0000-0000-0000-000000000001",
  username: "alice",
  displayName: "Alice",
  email: "alice@example.com",
  source: "INTERNAL",
  enabled: true,
  passwordChangeRequired: false,
  isSuperadmin: false,
  createdAt: "2026-04-01T10:00:00Z",
  namespaceMembershipCount: 1,
};

const bobOidc: UserDto = {
  id: "00000000-0000-0000-0000-000000000002",
  displayName: "Bob",
  email: "bob@example.com",
  source: "EXTERNAL",
  providerName: "Company Keycloak",
  enabled: true,
  passwordChangeRequired: false,
  isSuperadmin: false,
  createdAt: "2026-04-02T10:00:00Z",
  namespaceMembershipCount: 0,
};

const callerSelf: UserDto = {
  id: CALLER_ID,
  username: "root",
  displayName: "Root Admin",
  email: "root@example.com",
  source: "INTERNAL",
  enabled: true,
  passwordChangeRequired: false,
  isSuperadmin: true,
  createdAt: "2026-01-01T10:00:00Z",
  namespaceMembershipCount: 0,
};

const carolDisabled: UserDto = {
  id: "00000000-0000-0000-0000-000000000004",
  username: "carol",
  displayName: "Carol",
  email: "carol@example.com",
  source: "INTERNAL",
  enabled: false,
  passwordChangeRequired: false,
  isSuperadmin: false,
  createdAt: "2026-04-03T10:00:00Z",
  namespaceMembershipCount: 0,
};

describe("UsersSection — admin reset password (#450)", () => {
  beforeEach(() => {
    useUiStore.setState({ toasts: [] });
    useAuthStore.setState({
      isAuthenticated: true,
      userId: CALLER_ID,
      username: "root",
      isSuperadmin: true,
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockReset();
    vi.mocked(apiConfig.adminUsersApi.adminResetUserPassword).mockReset();
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: [aliceInternal, bobOidc, callerSelf, carolDisabled],
    } as never);
  });

  function findResetButtonForRow(displayName: string): HTMLElement {
    const cell = screen.getByText(displayName);
    const row = cell.closest("tr");
    if (!row) throw new Error(`No row found for ${displayName}`);
    return within(row).getByRole("button", {
      name: /reset password|oidc users|use profile|enable the user/i,
    });
  }

  it("renders a reset-password action button for each row", async () => {
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());
    const resetButton = findResetButtonForRow("Alice");
    expect(resetButton).toBeEnabled();
    expect(resetButton).toHaveAttribute(
      "aria-label",
      expect.stringMatching(/reset password/i),
    );
  });

  it("disables the reset button for EXTERNAL (OIDC) users with provider hint", async () => {
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Bob")).toBeInTheDocument());
    const resetButton = findResetButtonForRow("Bob");
    expect(resetButton).toBeDisabled();
    expect(resetButton).toHaveAttribute(
      "aria-label",
      expect.stringMatching(/Company Keycloak/i),
    );
  });

  it("disables the reset button for the caller's own row (self-reset guard)", async () => {
    renderWithTheme(<UsersSection />);
    await waitFor(() =>
      expect(screen.getByText("Root Admin")).toBeInTheDocument(),
    );
    const resetButton = findResetButtonForRow("Root Admin");
    expect(resetButton).toBeDisabled();
    expect(resetButton).toHaveAttribute(
      "aria-label",
      expect.stringMatching(/profile/i),
    );
  });

  it("disables the reset button for disabled users", async () => {
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Carol")).toBeInTheDocument());
    const resetButton = findResetButtonForRow("Carol");
    expect(resetButton).toBeDisabled();
  });

  it("opens a confirmation dialog naming the target user when reset is clicked", async () => {
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());
    await user.click(findResetButtonForRow("Alice"));
    const description = await screen.findByText(
      /Reset password for "Alice"\? An email with a single-use reset link/i,
    );
    // Scope email assertion to the description text — `alice@example.com`
    // also appears in the row above.
    expect(description.textContent).toMatch(/alice@example\.com/i);
  });

  it("on success with emailSent=true shows success toast and closes dialog", async () => {
    vi.mocked(apiConfig.adminUsersApi.adminResetUserPassword).mockResolvedValue(
      {
        data: {
          tokenIssued: true,
          emailSent: true,
          expiresAt: "2026-05-09T13:00:00Z",
        },
      } as never,
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(findResetButtonForRow("Alice"));
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    await waitFor(() => {
      const toasts = useUiStore.getState().toasts;
      expect(
        toasts.some((t) => t.message && /Reset email sent/i.test(t.message)),
      ).toBe(true);
    });
    expect(apiConfig.adminUsersApi.adminResetUserPassword).toHaveBeenCalledWith(
      { userId: aliceInternal.id },
    );
  });

  it("on emailSent=false with resetUrl shows AdminResetLinkDialog with copy button", async () => {
    vi.mocked(apiConfig.adminUsersApi.adminResetUserPassword).mockResolvedValue(
      {
        data: {
          tokenIssued: true,
          emailSent: false,
          expiresAt: "2026-05-09T13:00:00Z",
          resetUrl: "https://plugwerk.example.com/reset-password?token=raw-xyz",
        },
      } as never,
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(findResetButtonForRow("Alice"));
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    expect(
      await screen.findByText(/SMTP unavailable — deliver this reset link/i),
    ).toBeInTheDocument();
    // The TextField holds the URL as its display value — search by value
    // instead of by label, since MUI's inputProps spread does not always
    // surface aria-label as a discoverable accessible name.
    const linkInput = screen.getByDisplayValue(
      "https://plugwerk.example.com/reset-password?token=raw-xyz",
    ) as HTMLInputElement;
    expect(linkInput.readOnly).toBe(true);
    expect(
      screen.getByRole("button", {
        name: /copy reset link to clipboard/i,
      }),
    ).toBeInTheDocument();
  });

  it("on API failure shows error toast and keeps the confirm dialog open", async () => {
    vi.mocked(apiConfig.adminUsersApi.adminResetUserPassword).mockRejectedValue(
      new Error("boom"),
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(findResetButtonForRow("Alice"));
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    await waitFor(() => {
      const toasts = useUiStore.getState().toasts;
      expect(
        toasts.some(
          (t) => t.message && /Failed to reset password/i.test(t.message),
        ),
      ).toBe(true);
    });
  });
});
