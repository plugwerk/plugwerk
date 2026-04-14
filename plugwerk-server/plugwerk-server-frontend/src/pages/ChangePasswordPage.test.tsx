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
import { describe, it, expect, beforeEach, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter } from "../test/renderWithTheme";
import { ChangePasswordPage } from "./ChangePasswordPage";
import { useAuthStore } from "../stores/authStore";

vi.mock("../api/config", () => ({
  authApi: {
    changePassword: vi.fn(),
  },
}));

import { authApi } from "../api/config";

describe("ChangePasswordPage", () => {
  beforeEach(() => {
    useAuthStore.setState({
      username: "alice",
      isAuthenticated: true,
      passwordChangeRequired: true,
      clearPasswordChangeRequired: vi.fn(),
    } as never);
    vi.restoreAllMocks();
  });

  it("renders the change password heading", () => {
    renderWithRouter(<ChangePasswordPage />);
    expect(screen.getByText(/change your password/i)).toBeInTheDocument();
  });

  it("shows the logged-in username in subtitle", () => {
    renderWithRouter(<ChangePasswordPage />);
    expect(screen.getByText(/alice/i)).toBeInTheDocument();
  });

  it("renders current and new password fields", () => {
    renderWithRouter(<ChangePasswordPage />);
    expect(screen.getByLabelText(/current password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^new password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm new password/i)).toBeInTheDocument();
  });

  it("shows validation error when new password is too short", async () => {
    const user = userEvent.setup();
    renderWithRouter(<ChangePasswordPage />);
    await user.type(screen.getByLabelText(/current password/i), "old");
    await user.type(screen.getByLabelText(/^new password/i), "short");
    await user.type(screen.getByLabelText(/confirm new password/i), "short");
    await user.click(screen.getByRole("button", { name: /set new password/i }));

    expect(
      screen.getByText(/must be at least 12 characters/i),
    ).toBeInTheDocument();
  });

  it("shows validation error when passwords do not match", async () => {
    const user = userEvent.setup();
    renderWithRouter(<ChangePasswordPage />);
    await user.type(screen.getByLabelText(/current password/i), "oldpassword");
    await user.type(screen.getByLabelText(/^new password/i), "newpassword12");
    await user.type(
      screen.getByLabelText(/confirm new password/i),
      "different123",
    );
    await user.click(screen.getByRole("button", { name: /set new password/i }));

    expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
  });

  it("calls changePassword API with correct values on valid submit", async () => {
    const mockChange = vi.fn().mockResolvedValue({});
    vi.mocked(authApi.changePassword).mockImplementation(mockChange);

    const user = userEvent.setup();
    renderWithRouter(<ChangePasswordPage />);
    await user.type(screen.getByLabelText(/current password/i), "oldpassword");
    await user.type(screen.getByLabelText(/^new password/i), "newpassword12");
    await user.type(
      screen.getByLabelText(/confirm new password/i),
      "newpassword12",
    );
    await user.click(screen.getByRole("button", { name: /set new password/i }));

    await waitFor(() => {
      expect(mockChange).toHaveBeenCalledWith({
        changePasswordRequest: {
          currentPassword: "oldpassword",
          newPassword: "newpassword12",
        },
      });
    });
  });

  it("shows error message when API call fails", async () => {
    vi.mocked(authApi.changePassword).mockRejectedValue(
      new Error("Unauthorized"),
    );

    const user = userEvent.setup();
    renderWithRouter(<ChangePasswordPage />);
    await user.type(screen.getByLabelText(/current password/i), "wrongold");
    await user.type(screen.getByLabelText(/^new password/i), "newpassword12");
    await user.type(
      screen.getByLabelText(/confirm new password/i),
      "newpassword12",
    );
    await user.click(screen.getByRole("button", { name: /set new password/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/failed to change password/i),
      ).toBeInTheDocument();
    });
  });
});
