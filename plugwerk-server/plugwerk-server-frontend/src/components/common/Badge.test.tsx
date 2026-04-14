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
import { renderWithTheme } from "../../test/renderWithTheme";
import { Badge } from "./Badge";
import type { BadgeVariant } from "./Badge";

const variants: BadgeVariant[] = [
  "version",
  "published",
  "draft",
  "deprecated",
  "yanked",
  "tag",
  "verified",
];

describe("Badge", () => {
  it("renders the provided text", () => {
    renderWithTheme(<Badge variant="version">v1.2.3</Badge>);
    expect(screen.getByText("v1.2.3")).toBeInTheDocument();
  });

  it.each(variants)('renders without crashing for variant "%s"', (variant) => {
    renderWithTheme(<Badge variant={variant}>Label</Badge>);
    expect(screen.getByText("Label")).toBeInTheDocument();
  });

  it('shows Check icon for "verified" variant', () => {
    renderWithTheme(<Badge variant="verified">Verified</Badge>);
    // The Check icon renders as an SVG with aria-hidden="true"
    const svgs = document.querySelectorAll('svg[aria-hidden="true"]');
    expect(svgs.length).toBeGreaterThan(0);
  });

  it("does not show Check icon for non-verified variants", () => {
    renderWithTheme(<Badge variant="published">Published</Badge>);
    const svgs = document.querySelectorAll('svg[aria-hidden="true"]');
    expect(svgs.length).toBe(0);
  });

  it("renders as inline element (span)", () => {
    renderWithTheme(<Badge variant="tag">my-tag</Badge>);
    const badge = screen.getByText("my-tag");
    expect(badge.tagName.toLowerCase()).toBe("span");
  });
});
