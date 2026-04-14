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
import { screen } from "@testing-library/react";
import { renderWithRouter } from "../../test/renderWithTheme";
import { PendingReviewBanner } from "./PendingReviewBanner";

function renderBanner(props: {
  pluginCount: number;
  releaseCount: number | null;
  isAdmin: boolean;
}) {
  return renderWithRouter(<PendingReviewBanner {...props} />);
}

describe("PendingReviewBanner", () => {
  it("renders nothing when pluginCount is 0", () => {
    const { container } = renderBanner({
      pluginCount: 0,
      releaseCount: null,
      isAdmin: false,
    });
    expect(container.firstChild).toBeNull();
  });

  it("shows singular plugin and release text", () => {
    renderBanner({ pluginCount: 1, releaseCount: 1, isAdmin: false });
    expect(
      screen.getByText(/1 plugin \(1 release\) pending review/),
    ).toBeInTheDocument();
  });

  it("shows plural plugins and releases text", () => {
    renderBanner({ pluginCount: 3, releaseCount: 5, isAdmin: false });
    expect(
      screen.getByText(/3 plugins \(5 releases\) pending review/),
    ).toBeInTheDocument();
  });

  it("shows plugin-only text when releaseCount is null", () => {
    renderBanner({ pluginCount: 2, releaseCount: null, isAdmin: false });
    expect(screen.getByText(/2 plugins pending review/)).toBeInTheDocument();
    expect(screen.queryByText(/releases/)).not.toBeInTheDocument();
  });

  it("shows Review link for admins pointing to /admin/reviews", () => {
    renderBanner({ pluginCount: 1, releaseCount: 2, isAdmin: true });
    const link = screen.getByText("Review");
    expect(link).toBeInTheDocument();
    expect(link.closest("a")).toHaveAttribute("href", "/admin/reviews");
  });

  it("does not show Review link for non-admins", () => {
    renderBanner({ pluginCount: 1, releaseCount: 2, isAdmin: false });
    expect(screen.queryByText("Review")).not.toBeInTheDocument();
  });
});
