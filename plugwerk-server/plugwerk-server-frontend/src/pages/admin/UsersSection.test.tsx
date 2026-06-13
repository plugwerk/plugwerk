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
      data: {
        content: [aliceInternal, bobOidc, callerSelf, carolDisabled],
        totalElements: 4,
        page: 0,
        size: 20,
        totalPages: 1,
      },
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
        toasts.some(
          (t) => t.message && /Reset email sent/i.test(t.message ?? ""),
        ),
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
          (t) => t.message && /Failed to reset password/i.test(t.message ?? ""),
        ),
      ).toBe(true);
    });
  });

  it("on emailSent=false without a resetUrl shows the defensive error toast", async () => {
    vi.mocked(apiConfig.adminUsersApi.adminResetUserPassword).mockResolvedValue(
      {
        data: { tokenIssued: true, emailSent: false },
      } as never,
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(findResetButtonForRow("Alice"));
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) =>
            /no fallback link was returned/i.test(t.message ?? ""),
          ),
      ).toBe(true);
    });
  });
});

describe("UsersSection — list / loading / empty states", () => {
  beforeEach(() => {
    useUiStore.setState({ toasts: [] });
    useAuthStore.setState({
      isAuthenticated: true,
      userId: CALLER_ID,
      username: "root",
      isSuperadmin: true,
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockReset();
  });

  it("shows a spinner while users are loading", () => {
    // Never-resolving promise keeps the component in its loading branch.
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockReturnValue(
      new Promise(() => {}) as never,
    );
    renderWithTheme(<UsersSection />);
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("shows the empty state when there are no users", async () => {
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: { content: [], totalElements: 0, page: 0, size: 20, totalPages: 0 },
    } as never);
    renderWithTheme(<UsersSection />);
    expect(await screen.findByText("No users found.")).toBeInTheDocument();
  });

  it("falls back to empty state when the list request rejects", async () => {
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockRejectedValue(
      new Error("network"),
    );
    renderWithTheme(<UsersSection />);
    expect(await screen.findByText("No users found.")).toBeInTheDocument();
  });

  it("renders superadmin and password-change-required badges", async () => {
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: {
        content: [
          callerSelf,
          { ...aliceInternal, passwordChangeRequired: true },
        ],
        totalElements: 2,
        page: 0,
        size: 20,
        totalPages: 1,
      },
    } as never);
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());
    expect(screen.getByText("superadmin")).toBeInTheDocument();
    expect(screen.getByText("pw change required")).toBeInTheDocument();
  });
});

describe("UsersSection — toggle / delete / create", () => {
  const allUsers = {
    data: {
      content: [aliceInternal, bobOidc, callerSelf, carolDisabled],
      totalElements: 4,
      page: 0,
      size: 20,
      totalPages: 1,
    },
  };

  beforeEach(() => {
    useUiStore.setState({ toasts: [] });
    useAuthStore.setState({
      isAuthenticated: true,
      userId: CALLER_ID,
      username: "root",
      isSuperadmin: true,
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockReset();
    vi.mocked(apiConfig.adminUsersApi.updateUser).mockReset();
    vi.mocked(apiConfig.adminUsersApi.deleteUser).mockReset();
    vi.mocked(apiConfig.adminUsersApi.createUser).mockReset();
    vi.mocked(apiConfig.adminUsersApi.adminResetUserPassword).mockReset();
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue(
      allUsers as never,
    );
  });

  function toggleForRow(displayName: string): HTMLElement {
    // The MUI Switch exposes its aria-label on the underlying input.
    return screen.getByLabelText(`Toggle ${displayName}`);
  }

  it("toggles a user's enabled state and shows a success toast", async () => {
    vi.mocked(apiConfig.adminUsersApi.updateUser).mockResolvedValue({
      data: { ...aliceInternal, enabled: false },
    } as never);
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(toggleForRow("Alice"));

    expect(apiConfig.adminUsersApi.updateUser).toHaveBeenCalledWith({
      userId: aliceInternal.id,
      userUpdateRequest: { enabled: false },
    });
    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => /"Alice" disabled/i.test(t.message ?? "")),
      ).toBe(true);
    });
  });

  it("shows an error toast when the enabled toggle fails", async () => {
    vi.mocked(apiConfig.adminUsersApi.updateUser).mockRejectedValue(
      new Error("nope"),
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(toggleForRow("Alice"));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) =>
            /Failed to update user Alice/i.test(t.message ?? ""),
          ),
      ).toBe(true);
    });
  });

  it("disables the enabled toggle for superadmins", async () => {
    renderWithTheme(<UsersSection />);
    await waitFor(() =>
      expect(screen.getByText("Root Admin")).toBeInTheDocument(),
    );
    expect(toggleForRow("Root Admin")).toBeDisabled();
  });

  function deleteButtonForRow(displayName: string): HTMLElement {
    const cell = screen.getByText(displayName);
    const row = cell.closest("tr");
    if (!row) throw new Error(`No row for ${displayName}`);
    return within(row).getByRole("button", { name: /^delete$/i });
  }

  it("deletes a user with a membership warning and shows a success toast", async () => {
    vi.mocked(apiConfig.adminUsersApi.deleteUser).mockResolvedValue(
      {} as never,
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(deleteButtonForRow("Alice"));
    // Alice has namespaceMembershipCount=1 → membership-aware warning.
    expect(
      await screen.findByText(/member of 1 namespace/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /delete user/i }));

    expect(apiConfig.adminUsersApi.deleteUser).toHaveBeenCalledWith({
      userId: aliceInternal.id,
    });
    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => /"Alice" deleted/i.test(t.message ?? "")),
      ).toBe(true);
    });
  });

  it("shows the no-membership delete copy and an error toast on failure", async () => {
    vi.mocked(apiConfig.adminUsersApi.deleteUser).mockRejectedValue(
      new Error("boom"),
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Carol")).toBeInTheDocument());

    await user.click(deleteButtonForRow("Carol"));
    // Carol has 0 memberships → permanent-deletion copy.
    expect(
      await screen.findByText(/will be permanently deleted/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /delete user/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) =>
            /Failed to delete user Carol/i.test(t.message ?? ""),
          ),
      ).toBe(true);
    });
  });

  it("cancels the delete dialog without calling the API", async () => {
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Carol")).toBeInTheDocument());

    await user.click(deleteButtonForRow("Carol"));
    await user.click(screen.getByRole("button", { name: /cancel/i }));

    await waitFor(() =>
      expect(
        screen.queryByText(/will be permanently deleted/i),
      ).not.toBeInTheDocument(),
    );
    expect(apiConfig.adminUsersApi.deleteUser).not.toHaveBeenCalled();
  });

  it("disables the delete button for superadmins", async () => {
    renderWithTheme(<UsersSection />);
    await waitFor(() =>
      expect(screen.getByText("Root Admin")).toBeInTheDocument(),
    );
    expect(deleteButtonForRow("Root Admin")).toBeDisabled();
  });

  it("creates a user from the Add User dialog and shows a success toast", async () => {
    vi.mocked(apiConfig.adminUsersApi.createUser).mockResolvedValue({
      data: { ...aliceInternal, displayName: "Dave" },
    } as never);
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /add user/i }));
    await user.type(screen.getByLabelText(/username/i), "dave");
    await user.type(screen.getByLabelText(/email/i), "dave@example.com");
    await user.type(screen.getByLabelText(/initial password/i), "secret123");
    await user.click(screen.getByRole("button", { name: /create user/i }));

    expect(apiConfig.adminUsersApi.createUser).toHaveBeenCalledWith({
      userCreateRequest: {
        username: "dave",
        email: "dave@example.com",
        password: "secret123",
      },
    });
    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => /"Dave" created/i.test(t.message ?? "")),
      ).toBe(true);
    });
  });

  it("shows an error toast when user creation fails", async () => {
    vi.mocked(apiConfig.adminUsersApi.createUser).mockRejectedValue(
      new Error("dup"),
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /add user/i }));
    await user.type(screen.getByLabelText(/username/i), "dave");
    await user.type(screen.getByLabelText(/email/i), "dave@example.com");
    await user.type(screen.getByLabelText(/initial password/i), "secret123");
    await user.click(screen.getByRole("button", { name: /create user/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => /Failed to create user/i.test(t.message ?? "")),
      ).toBe(true);
    });
  });

  it("keeps the Create button disabled until username and password are filled", async () => {
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /add user/i }));
    const createButton = screen.getByRole("button", { name: /create user/i });
    expect(createButton).toBeDisabled();

    await user.type(screen.getByLabelText(/username/i), "dave");
    // Still disabled — password is empty.
    expect(createButton).toBeDisabled();

    await user.type(screen.getByLabelText(/initial password/i), "secret123");
    expect(createButton).toBeEnabled();
  });

  it("changes the page size and refetches with the new size", async () => {
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(screen.getByRole("combobox", { name: /users per page/i }));
    await user.click(screen.getByRole("option", { name: "50" }));

    await waitFor(() => {
      expect(apiConfig.adminUsersApi.listUsers).toHaveBeenCalledWith(
        expect.objectContaining({ size: 50, page: 0 }),
      );
    });
  });

  it("advances to the next page and refetches with the new page index", async () => {
    // totalElements (45) exceeds the page size (20) so the next-page arrow is
    // enabled and onPageChange can fire.
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: {
        content: [aliceInternal, bobOidc, callerSelf, carolDisabled],
        totalElements: 45,
        page: 0,
        size: 20,
        totalPages: 3,
      },
    } as never);
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /next page/i }));

    await waitFor(() => {
      expect(apiConfig.adminUsersApi.listUsers).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1 }),
      );
    });
  });

  it("closes the Add User dialog when cancelled", async () => {
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /add user/i }));
    expect(
      screen.getByText(/Create a new local user account/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^cancel$/i }));
    await waitFor(() =>
      expect(
        screen.queryByText(/Create a new local user account/i),
      ).not.toBeInTheDocument(),
    );
  });

  it("dismisses the reset confirmation dialog without resetting", async () => {
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    const cell = screen.getByText("Alice");
    const row = cell.closest("tr")!;
    await user.click(
      within(row).getByRole("button", { name: /reset password/i }),
    );
    expect(
      await screen.findByText(/Reset password for "Alice"\?/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^cancel$/i }));
    await waitFor(() =>
      expect(
        screen.queryByText(/Reset password for "Alice"\?/i),
      ).not.toBeInTheDocument(),
    );
    expect(
      apiConfig.adminUsersApi.adminResetUserPassword,
    ).not.toHaveBeenCalled();
  });

  it("closes the manual reset-link dialog after an SMTP-unavailable reset", async () => {
    vi.mocked(apiConfig.adminUsersApi.adminResetUserPassword).mockResolvedValue(
      {
        data: {
          tokenIssued: true,
          emailSent: false,
          resetUrl: "https://plugwerk.example.com/reset-password?token=raw",
        },
      } as never,
    );
    const user = userEvent.setup();
    renderWithTheme(<UsersSection />);
    await waitFor(() => expect(screen.getByText("Alice")).toBeInTheDocument());

    const row = screen.getByText("Alice").closest("tr")!;
    await user.click(
      within(row).getByRole("button", { name: /reset password/i }),
    );
    await user.click(screen.getByRole("button", { name: /send reset link/i }));

    expect(
      await screen.findByDisplayValue(
        "https://plugwerk.example.com/reset-password?token=raw",
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /close dialog/i }));
    await waitFor(() =>
      expect(
        screen.queryByDisplayValue(
          "https://plugwerk.example.com/reset-password?token=raw",
        ),
      ).not.toBeInTheDocument(),
    );
  });
});
