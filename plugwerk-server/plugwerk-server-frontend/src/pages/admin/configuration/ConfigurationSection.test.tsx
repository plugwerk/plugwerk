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
import userEvent from "@testing-library/user-event";
import { ConfigurationSection } from "./ConfigurationSection";
import { renderWithTheme } from "../../../test/renderWithTheme";
import * as apiConfig from "../../../api/config";

vi.mock("../../../api/config", () => ({
  adminConfigurationApi: {
    getEffectiveConfiguration: vi.fn(),
  },
}));

describe("ConfigurationSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the info banner and the configured/not-configured chip", async () => {
    vi.mocked(
      apiConfig.adminConfigurationApi.getEffectiveConfiguration,
    ).mockResolvedValue({
      data: {
        storage: { type: "fs" },
        auth: {
          jwtSecret: { _secret: true, configured: true },
          accessTokenValidityMinutes: 480,
        },
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    renderWithTheme(<ConfigurationSection />);

    await waitFor(() => {
      expect(screen.getByText(/cannot be edited here/i)).toBeInTheDocument();
    });

    // The plain value passes through.
    expect(screen.getByText("plugwerk.storage.type")).toBeInTheDocument();
    expect(screen.getByText("fs")).toBeInTheDocument();
    // The redacted leaf renders as a chip, never the plaintext.
    expect(screen.getByText("configured")).toBeInTheDocument();
  });

  it("filters leaves by a substring of the dotted path", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminConfigurationApi.getEffectiveConfiguration,
    ).mockResolvedValue({
      data: {
        storage: { type: "fs", consistency: { maxKeysPerScan: 100000 } },
        auth: { accessTokenValidityMinutes: 480 },
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    renderWithTheme(<ConfigurationSection />);

    await screen.findByText("plugwerk.storage.type");

    const search = screen.getByPlaceholderText(/Filter by property path/i);
    await user.type(search, "auth");

    await waitFor(() => {
      expect(
        screen.getByText("plugwerk.auth.accessTokenValidityMinutes"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("plugwerk.storage.type"),
      ).not.toBeInTheDocument();
    });
  });

  it("renders a not-configured chip when the redacted leaf is empty", async () => {
    vi.mocked(
      apiConfig.adminConfigurationApi.getEffectiveConfiguration,
    ).mockResolvedValue({
      data: {
        auth: { jwtSecret: { _secret: true, configured: false } },
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    renderWithTheme(<ConfigurationSection />);

    await waitFor(() => {
      expect(screen.getByText("not configured")).toBeInTheDocument();
    });
  });

  it("surfaces a fetch error in an alert", async () => {
    vi.mocked(
      apiConfig.adminConfigurationApi.getEffectiveConfiguration,
    ).mockRejectedValue(new Error("boom"));

    renderWithTheme(<ConfigurationSection />);

    await waitFor(() => {
      expect(
        screen.getByText(/Failed to load configuration/i),
      ).toBeInTheDocument();
    });
  });
});
