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
import { SchedulerSection } from "./SchedulerSection";
import { renderWithTheme } from "../../../test/renderWithTheme";
import * as apiConfig from "../../../api/config";
import type { SchedulerJobDto } from "../../../api/generated/model";

vi.mock("../../../api/config", () => ({
  adminSchedulerApi: {
    listSchedulerJobs: vi.fn(),
    updateSchedulerJob: vi.fn(),
    runSchedulerJobNow: vi.fn(),
  },
}));

function jobFixture(overrides: Partial<SchedulerJobDto> = {}): SchedulerJobDto {
  return {
    name: "refresh-token-cleanup",
    description: "Hourly purge of expired refresh tokens.",
    cronExpression: "0 0 * * * *",
    supportsDryRun: false,
    enabled: true,
    runCountTotal: 0,
    ...overrides,
  };
}

describe("SchedulerSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists registered jobs with status", async () => {
    vi.mocked(apiConfig.adminSchedulerApi.listSchedulerJobs).mockResolvedValue({
      data: [
        jobFixture(),
        jobFixture({
          name: "orphan-storage-reaper",
          description: "Reap orphans.",
          cronExpression: "0 15 3 * * *",
          supportsDryRun: true,
          dryRun: true,
          runCountTotal: 12,
          lastRunOutcome: "SUCCESS",
          lastRunAt: "2026-05-13T01:00:00Z",
          lastRunDurationMs: 230,
        }),
      ],
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);

    renderWithTheme(<SchedulerSection />);

    await waitFor(() => {
      expect(screen.getByText("refresh-token-cleanup")).toBeInTheDocument();
      expect(screen.getByText("orphan-storage-reaper")).toBeInTheDocument();
      expect(screen.getByText("Reap orphans.")).toBeInTheDocument();
    });
  });

  it("toggles enabled via PATCH and refetches", async () => {
    const user = userEvent.setup();
    vi.mocked(apiConfig.adminSchedulerApi.listSchedulerJobs)
      .mockResolvedValueOnce({ data: [jobFixture({ enabled: true })] } as never)
      .mockResolvedValueOnce({
        data: [jobFixture({ enabled: false })],
      } as never);
    vi.mocked(apiConfig.adminSchedulerApi.updateSchedulerJob).mockResolvedValue(
      {
        data: jobFixture({ enabled: false }),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any,
    );

    renderWithTheme(<SchedulerSection />);

    // MUI Switch renders an <input type="checkbox" role="switch"> under the
    // hood; the aria-label propagates through slotProps.
    const toggle = await screen.findByRole("switch", {
      name: /Toggle refresh-token-cleanup enabled/i,
    });
    await user.click(toggle);

    await waitFor(() => {
      expect(
        apiConfig.adminSchedulerApi.updateSchedulerJob,
      ).toHaveBeenCalledWith({
        name: "refresh-token-cleanup",
        schedulerJobUpdateRequest: { enabled: false, dryRun: undefined },
      });
    });
  });

  it("triggers run-now after confirmation", async () => {
    // MUI's Tooltip wraps the Run-now Button in a span with
    // pointer-events: none for the disabled-tooltip pattern, which
    // confuses user-event's pointer simulation. We bypass that check
    // because we are exercising the click-to-confirm flow, not the
    // hover semantics.
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    vi.mocked(apiConfig.adminSchedulerApi.listSchedulerJobs).mockResolvedValue({
      data: [jobFixture({ enabled: true })],
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any);
    vi.mocked(apiConfig.adminSchedulerApi.runSchedulerJobNow).mockResolvedValue(
      {
        data: {
          name: "refresh-token-cleanup",
          outcome: "SUCCESS",
          durationMs: 12,
        },
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any,
    );

    renderWithTheme(<SchedulerSection />);

    const runBtn = await screen.findByRole("button", { name: /Run now/i });
    await user.click(runBtn);
    const confirmBtn = await screen.findByRole("button", { name: /Run job/i });
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(
        apiConfig.adminSchedulerApi.runSchedulerJobNow,
      ).toHaveBeenCalledWith({ name: "refresh-token-cleanup" });
    });
  });

  it("surfaces a fetch error in an alert", async () => {
    vi.mocked(apiConfig.adminSchedulerApi.listSchedulerJobs).mockRejectedValue(
      new Error("boom"),
    );

    renderWithTheme(<SchedulerSection />);

    await waitFor(() => {
      expect(
        screen.getByText(/Failed to load scheduler jobs/i),
      ).toBeInTheDocument();
    });
  });
});
