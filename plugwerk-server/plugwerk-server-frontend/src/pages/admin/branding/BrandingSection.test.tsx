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
import { screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as apiConfig from "../../../api/config";
import { renderWithTheme } from "../../../test/renderWithTheme";
import { BrandingSection } from "./BrandingSection";

vi.mock("../../../api/config", () => ({
  adminBrandingApi: {
    uploadBrandingAsset: vi.fn(),
    resetBrandingAsset: vi.fn(),
  },
}));

// Each card decides Custom-vs-Default (and whether to show "Reset to default")
// by probing its public endpoint with an off-DOM `new Image()` inside an effect
// — deliberately *not* the rendered <img>'s onLoad/onError, to avoid the
// fallback-swap race documented in BrandingSection.tsx ("#530 follow-up").
// jsdom never fires load/error on a real Image, so drive the probe with a
// controllable stub whose outcome each test selects via `probeOutcome`.
let probeOutcome: "load" | "error" = "error";

class FakeImage {
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  private _src = "";

  set src(value: string) {
    this._src = value;
    // Defer so the effect has finished assigning onload/onerror before we
    // fire, mirroring a real asynchronous image fetch.
    setTimeout(() => {
      if (probeOutcome === "load") this.onload?.();
      else this.onerror?.();
    }, 0);
  }

  get src(): string {
    return this._src;
  }
}

describe("BrandingSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    probeOutcome = "error";
    vi.stubGlobal("Image", FakeImage);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders three tiles for the slot model", () => {
    renderWithTheme(<BrandingSection />);

    expect(screen.getByText("Light logo")).toBeInTheDocument();
    expect(screen.getByText("Dark logo")).toBeInTheDocument();
    expect(screen.getByText("Logomark")).toBeInTheDocument();
  });

  it("falls back to Default when the public asset 404s", async () => {
    probeOutcome = "error";
    renderWithTheme(<BrandingSection />);

    await waitFor(() => {
      expect(screen.getAllByText("Default").length).toBeGreaterThanOrEqual(3);
    });
    expect(
      apiConfig.adminBrandingApi.uploadBrandingAsset,
    ).not.toHaveBeenCalled();
  });

  it("flips to Custom + offers Reset when the asset loads", async () => {
    probeOutcome = "load";
    renderWithTheme(<BrandingSection />);

    await waitFor(() => {
      expect(
        screen.getAllByRole("button", { name: /Reset to default/i }),
      ).toHaveLength(3);
    });
  });
});
