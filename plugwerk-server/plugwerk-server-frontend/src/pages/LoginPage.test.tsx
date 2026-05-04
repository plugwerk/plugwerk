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
import { LoginPage } from "./LoginPage";
import { useAuthStore } from "../stores/authStore";
import { useConfigStore } from "../stores/configStore";

describe("LoginPage", () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: null,
      username: null,
      isAuthenticated: false,
    });
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("renders the sign in heading", () => {
    renderWithRouter(<LoginPage />);
    expect(screen.getByText("Welcome back")).toBeInTheDocument();
  });

  it("renders username and password inputs", () => {
    renderWithRouter(<LoginPage />);
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
  });

  it("renders the sign in button", () => {
    renderWithRouter(<LoginPage />);
    expect(
      screen.getByRole("button", { name: /sign in/i }),
    ).toBeInTheDocument();
  });

  it("renders subtitle text", () => {
    renderWithRouter(<LoginPage />);
    expect(screen.getByText(/sign in to your account/i)).toBeInTheDocument();
  });

  it("does not show error alert initially", () => {
    renderWithRouter(<LoginPage />);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows validation error when fields are empty and form is submitted", async () => {
    const user = userEvent.setup();
    renderWithRouter(<LoginPage />);
    await user.click(screen.getByRole("button", { name: /sign in/i }));
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(
      screen.getByText(/please enter username and password/i),
    ).toBeInTheDocument();
  });

  it("calls login with username and password when form is submitted", async () => {
    const mockLogin = vi.fn().mockResolvedValue(undefined);
    useAuthStore.setState({ login: mockLogin } as never);

    const user = userEvent.setup();
    renderWithRouter(<LoginPage />);
    await user.type(screen.getByLabelText(/username/i), "alice");
    await user.type(screen.getByLabelText(/^password/i), "secret");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith("alice", "secret");
    });
  });

  it("trims whitespace from username before submitting", async () => {
    const mockLogin = vi.fn().mockResolvedValue(undefined);
    useAuthStore.setState({ login: mockLogin } as never);

    const user = userEvent.setup();
    renderWithRouter(<LoginPage />);
    await user.type(screen.getByLabelText(/username/i), "  alice  ");
    await user.type(screen.getByLabelText(/^password/i), "secret");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith("alice", "secret");
    });
  });

  it("shows error message when login fails", async () => {
    const mockLogin = vi.fn().mockRejectedValue(new Error("Unauthorized"));
    useAuthStore.setState({ login: mockLogin } as never);

    const user = userEvent.setup();
    renderWithRouter(<LoginPage />);
    await user.type(screen.getByLabelText(/username/i), "wrong");
    await user.type(screen.getByLabelText(/^password/i), "wrong");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/invalid username or password/i),
      ).toBeInTheDocument();
    });
  });

  it("clears validation error when close button is clicked", async () => {
    const user = userEvent.setup();
    renderWithRouter(<LoginPage />);
    await user.click(screen.getByRole("button", { name: /sign in/i }));
    expect(screen.getByRole("alert")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /close/i }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("hides the Sign-up link when self-registration is disabled in /config (default)", () => {
    // configStore default has selfRegistrationEnabled = false; the link
    // must not render so locked-down deployments don't even hint at the
    // /register route existing.
    useConfigStore.setState({
      selfRegistrationEnabled: false,
      loaded: true,
    } as never);
    renderWithRouter(<LoginPage />);
    expect(
      screen.queryByRole("link", { name: /sign up/i }),
    ).not.toBeInTheDocument();
  });

  it("shows the Sign-up link only when /config exposes selfRegistrationEnabled = true (#420)", () => {
    useConfigStore.setState({
      selfRegistrationEnabled: true,
      loaded: true,
    } as never);
    renderWithRouter(<LoginPage />);
    const signUp = screen.getByRole("link", { name: /sign up/i });
    expect(signUp).toBeInTheDocument();
    expect(signUp).toHaveAttribute("href", "/register");
  });
});
