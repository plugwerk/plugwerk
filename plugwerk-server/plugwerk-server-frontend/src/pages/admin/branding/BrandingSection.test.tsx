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
import { screen, waitFor } from "@testing-library/react";
import { BrandingSection } from "./BrandingSection";
import { renderWithTheme } from "../../../test/renderWithTheme";
import * as apiConfig from "../../../api/config";

vi.mock("../../../api/config", () => ({
  adminBrandingApi: {
    uploadBrandingAsset: vi.fn(),
    resetBrandingAsset: vi.fn(),
  },
}));

describe("BrandingSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // HEAD probe defaults to "no custom asset" so all tiles show Default.
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: false } as Response),
    );
  });

  it("renders three tiles for the slot model", async () => {
    renderWithTheme(<BrandingSection />);

    expect(screen.getByText("Light logo")).toBeInTheDocument();
    expect(screen.getByText("Dark logo")).toBeInTheDocument();
    expect(screen.getByText("Logomark")).toBeInTheDocument();
  });

  it("labels each slot Default while no custom asset is present", async () => {
    renderWithTheme(<BrandingSection />);

    await waitFor(() => {
      expect(screen.getAllByText("Default").length).toBeGreaterThanOrEqual(3);
    });
    expect(
      apiConfig.adminBrandingApi.uploadBrandingAsset,
    ).not.toHaveBeenCalled();
  });

  it("shows Custom and a Reset button when the slot has a custom asset", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true } as Response));

    renderWithTheme(<BrandingSection />);

    await waitFor(() => {
      // Three tiles → three Custom chips (or three Reset buttons).
      expect(
        screen.getAllByRole("button", { name: /Reset to default/i }),
      ).toHaveLength(3);
    });
  });
});
