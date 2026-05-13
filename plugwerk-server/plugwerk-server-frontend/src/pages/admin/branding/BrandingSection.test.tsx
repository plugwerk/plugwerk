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
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as apiConfig from "../../../api/config";
import { renderWithTheme } from "../../../test/renderWithTheme";
import { BrandingSection } from "./BrandingSection";

vi.mock("../../../api/config", () => ({
  adminBrandingApi: {
    uploadBrandingAsset: vi.fn(),
    resetBrandingAsset: vi.fn(),
  },
}));

describe("BrandingSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders three tiles for the slot model", () => {
    renderWithTheme(<BrandingSection />);

    expect(screen.getByText("Light logo")).toBeInTheDocument();
    expect(screen.getByText("Dark logo")).toBeInTheDocument();
    expect(screen.getByText("Logomark")).toBeInTheDocument();
  });

  it("falls back to Default when the public asset 404s", async () => {
    renderWithTheme(<BrandingSection />);

    // Each tile renders an <img> pointing at /api/v1/branding/{slot}.
    // Simulate the 404 the server returns when the slot is at its
    // default by firing the image's onError on every preview.
    const previews = await screen.findAllByRole("img");
    previews.forEach((img) => fireEvent.error(img));

    await waitFor(() => {
      expect(screen.getAllByText("Default").length).toBeGreaterThanOrEqual(3);
    });
    expect(
      apiConfig.adminBrandingApi.uploadBrandingAsset,
    ).not.toHaveBeenCalled();
  });

  it("flips to Custom + offers Reset when the asset loads", async () => {
    renderWithTheme(<BrandingSection />);

    const previews = await screen.findAllByRole("img");
    previews.forEach((img) => fireEvent.load(img));

    await waitFor(() => {
      expect(
        screen.getAllByRole("button", { name: /Reset to default/i }),
      ).toHaveLength(3);
    });
  });
});
