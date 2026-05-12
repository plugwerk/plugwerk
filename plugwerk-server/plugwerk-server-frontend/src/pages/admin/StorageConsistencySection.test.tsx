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
import { StorageConsistencySection } from "./StorageConsistencySection";
import { renderWithTheme } from "../../test/renderWithTheme";
import * as apiConfig from "../../api/config";
import type { ConsistencyReport } from "../../api/generated/model";

vi.mock("../../api/config", () => ({
  adminStorageConsistencyApi: {
    getStorageConsistencyReport: vi.fn(),
    deleteOrphanedRelease: vi.fn(),
    deleteOrphanedReleases: vi.fn(),
    deleteOrphanedArtifacts: vi.fn(),
  },
}));

const NOW = "2026-05-12T12:00:00Z";

function reportFixture(
  overrides: Partial<ConsistencyReport> = {},
): ConsistencyReport {
  return {
    missingArtifacts: [],
    orphanedArtifacts: [],
    scannedAt: NOW,
    totalDbRows: 0,
    totalStorageObjects: 0,
    ...overrides,
  };
}

describe("StorageConsistencySection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders both empty tables on a clean report", async () => {
    vi.mocked(apiConfig.adminStorageConsistencyApi.getStorageConsistencyReport)
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      .mockResolvedValue({ data: reportFixture() } as any);

    renderWithTheme(<StorageConsistencySection />);

    await waitFor(() => {
      expect(
        screen.getByText("No releases reference missing storage files."),
      ).toBeInTheDocument();
      expect(
        screen.getByText("No orphaned objects in storage."),
      ).toBeInTheDocument();
    });
  });

  it("lists missing and orphaned rows from the report", async () => {
    vi.mocked(
      apiConfig.adminStorageConsistencyApi.getStorageConsistencyReport,
    ).mockResolvedValue({
      data: reportFixture({
        missingArtifacts: [
          {
            releaseId: "00000000-0000-0000-0000-000000000001",
            pluginId: "io.example.plugin",
            version: "1.0.0",
            artifactKey: "acme:io.example.plugin:1.0.0:jar",
          },
        ],
        orphanedArtifacts: [
          {
            key: "acme:orphan:0.1.0:jar",
            lastModified: "2026-05-10T12:00:00Z",
            ageHours: 48,
            sizeBytes: 12_345,
          },
        ],
        totalDbRows: 1,
        totalStorageObjects: 2,
      }),
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    renderWithTheme(<StorageConsistencySection />);

    await waitFor(() => {
      expect(screen.getByText("io.example.plugin")).toBeInTheDocument();
      expect(
        screen.getByText("acme:io.example.plugin:1.0.0:jar"),
      ).toBeInTheDocument();
      expect(screen.getByText("acme:orphan:0.1.0:jar")).toBeInTheDocument();
      expect(screen.getByText("48h")).toBeInTheDocument();
    });
  });

  it("surfaces the 409 max-keys-per-scan limit message", async () => {
    vi.mocked(
      apiConfig.adminStorageConsistencyApi.getStorageConsistencyReport,
    ).mockRejectedValue({
      response: {
        status: 409,
        data: {
          limit: 100_000,
          scannedSoFar: 100_001,
          message:
            "Storage scan aborted by max-keys-per-scan circuit breaker (limit=100000, scanned=100001).",
        },
      },
    });

    renderWithTheme(<StorageConsistencySection />);

    await waitFor(() => {
      expect(
        screen.getByText(/max-keys-per-scan circuit breaker/),
      ).toBeInTheDocument();
    });
  });

  it("triggers bulk remove for missing releases", async () => {
    const user = userEvent.setup();
    const releaseId = "00000000-0000-0000-0000-000000000001";
    vi.mocked(apiConfig.adminStorageConsistencyApi.getStorageConsistencyReport)
      .mockResolvedValueOnce({
        data: reportFixture({
          missingArtifacts: [
            {
              releaseId,
              pluginId: "io.example.plugin",
              version: "1.0.0",
              artifactKey: "acme:io.example.plugin:1.0.0:jar",
            },
          ],
          totalDbRows: 1,
        }),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any)
      .mockResolvedValueOnce({
        data: reportFixture(),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any);
    vi.mocked(
      apiConfig.adminStorageConsistencyApi.deleteOrphanedReleases,
    ).mockResolvedValue({
      data: { deleted: [releaseId], skipped: [] },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    renderWithTheme(<StorageConsistencySection />);

    await screen.findByText("io.example.plugin");

    // The Missing-section "Remove all" button is the first of the two
    // identical buttons (Missing renders before Orphaned in the DOM).
    const removeAllButtons = await screen.findAllByRole("button", {
      name: /Remove all/i,
    });
    await user.click(removeAllButtons[0]);
    await user.click(screen.getByRole("button", { name: /Remove rows/i }));

    await waitFor(() => {
      expect(
        apiConfig.adminStorageConsistencyApi.deleteOrphanedReleases,
      ).toHaveBeenCalledWith({
        orphanedReleaseDeletionRequest: { releaseIds: [releaseId] },
      });
    });
  });

  it("triggers bulk delete and re-scans on success", async () => {
    const user = userEvent.setup();
    vi.mocked(apiConfig.adminStorageConsistencyApi.getStorageConsistencyReport)
      .mockResolvedValueOnce({
        data: reportFixture({
          orphanedArtifacts: [
            {
              key: "acme:orphan:0.1.0:jar",
              lastModified: "2026-05-10T12:00:00Z",
              ageHours: 48,
              sizeBytes: 12_345,
            },
          ],
          totalStorageObjects: 1,
        }),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any)
      .mockResolvedValueOnce({
        data: reportFixture(),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any);
    vi.mocked(
      apiConfig.adminStorageConsistencyApi.deleteOrphanedArtifacts,
    ).mockResolvedValue({
      data: { deleted: ["acme:orphan:0.1.0:jar"], skipped: [] },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    renderWithTheme(<StorageConsistencySection />);

    await screen.findByText("acme:orphan:0.1.0:jar");

    // Two "Remove all" buttons exist (Missing + Orphaned). Missing is
    // disabled because that table is empty in this fixture, so we target
    // the enabled one for the orphaned-bulk flow.
    const removeAllButtons = screen.getAllByRole("button", {
      name: /Remove all/i,
    });
    const orphanedButton = removeAllButtons.find(
      (b) => !(b as HTMLButtonElement).disabled,
    );
    expect(orphanedButton).toBeDefined();
    await user.click(orphanedButton!);
    await user.click(screen.getByRole("button", { name: /Delete objects/i }));

    await waitFor(() => {
      expect(
        apiConfig.adminStorageConsistencyApi.deleteOrphanedArtifacts,
      ).toHaveBeenCalledWith({
        orphanedArtifactDeletionRequest: {
          keys: ["acme:orphan:0.1.0:jar"],
        },
      });
    });
    expect(
      apiConfig.adminStorageConsistencyApi.getStorageConsistencyReport,
    ).toHaveBeenCalledTimes(2);
  });
});
