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
import { describe, it, expect, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act } from "react";
import { renderWithTheme } from "../../test/renderWithTheme";
import { ToastRegion } from "./Toast";
import { useUiStore } from "../../stores/uiStore";

describe("ToastRegion", () => {
  beforeEach(() => {
    useUiStore.setState({ toasts: [] });
  });

  it("renders an accessible notification region", () => {
    renderWithTheme(<ToastRegion />);
    expect(
      screen.getByRole("region", { name: /notifications/i }),
    ).toBeInTheDocument();
  });

  it("renders no toasts when store is empty", () => {
    renderWithTheme(<ToastRegion />);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("renders a toast when one is added to the store", () => {
    act(() => {
      useUiStore.getState().addToast({ type: "info", message: "Hello world" });
    });
    renderWithTheme(<ToastRegion />);
    expect(screen.getByText("Hello world")).toBeInTheDocument();
  });

  it("renders toast with title and message", () => {
    act(() => {
      useUiStore.setState({
        toasts: [
          {
            id: "1",
            type: "success",
            title: "Upload complete",
            message: "Plugin was uploaded.",
          },
        ],
      });
    });
    renderWithTheme(<ToastRegion />);
    expect(screen.getByText("Upload complete")).toBeInTheDocument();
    expect(screen.getByText("Plugin was uploaded.")).toBeInTheDocument();
  });

  it("renders multiple toasts", () => {
    act(() => {
      useUiStore.setState({
        toasts: [
          { id: "1", type: "info", message: "First" },
          { id: "2", type: "error", message: "Second" },
        ],
      });
    });
    renderWithTheme(<ToastRegion />);
    expect(screen.getByText("First")).toBeInTheDocument();
    expect(screen.getByText("Second")).toBeInTheDocument();
  });

  it("dismisses a toast when the close button is clicked", async () => {
    const user = userEvent.setup();
    act(() => {
      useUiStore.setState({
        toasts: [{ id: "toast-1", type: "warning", message: "Watch out" }],
      });
    });
    renderWithTheme(<ToastRegion />);
    expect(screen.getByText("Watch out")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: /dismiss notification/i }),
    );
    expect(useUiStore.getState().toasts).toHaveLength(0);
  });

  it('renders toast with role="status" for accessibility', () => {
    act(() => {
      useUiStore.setState({
        toasts: [{ id: "1", type: "error", message: "Error occurred" }],
      });
    });
    renderWithTheme(<ToastRegion />);
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});
