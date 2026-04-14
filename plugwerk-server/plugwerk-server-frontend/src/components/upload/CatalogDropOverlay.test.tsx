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
import { CatalogDropOverlay } from "./CatalogDropOverlay";

describe("CatalogDropOverlay", () => {
  it("renders overlay text when visible is true", () => {
    render(<CatalogDropOverlay visible={true} />);
    expect(
      screen.getByText("Drop .jar or .zip files to upload"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Files will be uploaded immediately"),
    ).toBeInTheDocument();
  });

  it("hides overlay content when visible is false", () => {
    render(<CatalogDropOverlay visible={false} />);
    // MUI Fade keeps the element mounted but with visibility: hidden
    const overlay = screen
      .getByText("Drop .jar or .zip files to upload")
      .closest("[aria-hidden]");
    expect(overlay).toHaveAttribute("aria-hidden", "true");
  });

  it("has pointerEvents none to not capture drag events", () => {
    const { container } = render(<CatalogDropOverlay visible={true} />);
    const overlay = container.querySelector('[aria-hidden="false"]');
    expect(overlay).toHaveStyle({ pointerEvents: "none" });
  });
});
