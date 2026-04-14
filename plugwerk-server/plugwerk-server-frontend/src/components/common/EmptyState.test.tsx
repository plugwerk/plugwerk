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
import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithTheme } from "../../test/renderWithTheme";
import { EmptyState } from "./EmptyState";

describe("EmptyState", () => {
  it("renders the title", () => {
    renderWithTheme(
      <EmptyState
        title="No results found"
        message="Try adjusting your filters."
      />,
    );
    expect(screen.getByText("No results found")).toBeInTheDocument();
  });

  it("renders the message", () => {
    renderWithTheme(
      <EmptyState
        title="No results found"
        message="Try adjusting your filters."
      />,
    );
    expect(screen.getByText("Try adjusting your filters.")).toBeInTheDocument();
  });

  it("does not render action button when actionLabel is not provided", () => {
    renderWithTheme(<EmptyState title="Empty" message="Nothing here." />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("does not render action button when onAction is not provided", () => {
    renderWithTheme(
      <EmptyState title="Empty" message="Nothing here." actionLabel="Retry" />,
    );
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("renders action button when both actionLabel and onAction are provided", () => {
    const onAction = vi.fn();
    renderWithTheme(
      <EmptyState
        title="Empty"
        message="Nothing here."
        actionLabel="Clear filters"
        onAction={onAction}
      />,
    );
    expect(
      screen.getByRole("button", { name: /clear filters/i }),
    ).toBeInTheDocument();
  });

  it("calls onAction when the action button is clicked", async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();
    renderWithTheme(
      <EmptyState
        title="Empty"
        message="Nothing here."
        actionLabel="Retry"
        onAction={onAction}
      />,
    );
    await user.click(screen.getByRole("button", { name: /retry/i }));
    expect(onAction).toHaveBeenCalledOnce();
  });

  it("renders an SVG illustration", () => {
    renderWithTheme(<EmptyState title="No results" message="Try again." />);
    const svg = document.querySelector('svg[aria-hidden="true"]');
    expect(svg).toBeInTheDocument();
  });
});
