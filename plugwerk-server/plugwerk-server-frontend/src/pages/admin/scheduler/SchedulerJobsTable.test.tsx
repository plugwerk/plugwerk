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
import {
  screen,
  within,
  waitForElementToBeRemoved,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SchedulerJobsTable } from "./SchedulerJobsTable";
import { renderWithTheme } from "../../../test/renderWithTheme";
import type {
  SchedulerJobDto,
  SchedulerJobOutcome,
} from "../../../api/generated/model";

// SchedulerJobsTable is a pure presentational component — it never touches the
// API singletons directly (its parent SchedulerSection does). We still mock the
// module so the import graph stays inert and deterministic.
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

function noopHandlers() {
  return {
    onToggleEnabled: vi.fn(),
    onToggleDryRun: vi.fn(),
    onRunNow: vi.fn(),
  };
}

describe("SchedulerJobsTable — empty state", () => {
  it("renders the placeholder when there are no jobs", () => {
    renderWithTheme(
      <SchedulerJobsTable jobs={[]} busy={false} {...noopHandlers()} />,
    );
    expect(
      screen.getByText("No scheduled jobs registered."),
    ).toBeInTheDocument();
    // The table itself must not render in the empty branch.
    expect(
      screen.queryByRole("table", { name: /scheduled jobs/i }),
    ).not.toBeInTheDocument();
  });
});

describe("SchedulerJobsTable — rows and columns", () => {
  it("renders job name, description, cron chip and total runs", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[
          jobFixture({
            runCountTotal: 12345,
          }),
        ]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByText("refresh-token-cleanup")).toBeInTheDocument();
    expect(
      screen.getByText("Hourly purge of expired refresh tokens."),
    ).toBeInTheDocument();
    expect(screen.getByText("0 0 * * * *")).toBeInTheDocument();
    // toLocaleString() formats the count — separator is locale-dependent, so
    // match the digits non-strictly.
    expect(screen.getByText(/12.?345/)).toBeInTheDocument();
  });

  it("renders an em-dash for jobs that do not support dry-run", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[jobFixture({ supportsDryRun: false })]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    // The dry-run cell shows "—" when supportsDryRun is false; the dry-run
    // chip (which carries an aria-label) must be absent.
    expect(
      screen.queryByRole("button", { name: /toggle .* dry-run/i }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
  });
});

describe("SchedulerJobsTable — enabled switch", () => {
  it("invokes onToggleEnabled with the inverse of the current state", async () => {
    const user = userEvent.setup();
    const handlers = noopHandlers();
    const job = jobFixture({ enabled: true });
    renderWithTheme(
      <SchedulerJobsTable jobs={[job]} busy={false} {...handlers} />,
    );
    const toggle = screen.getByRole("switch", {
      name: /toggle refresh-token-cleanup enabled/i,
    });
    await user.click(toggle);
    expect(handlers.onToggleEnabled).toHaveBeenCalledWith(job, false);
  });

  it("disables the enabled switch while busy", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[jobFixture()]}
        busy={true}
        {...noopHandlers()}
      />,
    );
    expect(
      screen.getByRole("switch", { name: /toggle .* enabled/i }),
    ).toBeDisabled();
  });
});

describe("SchedulerJobsTable — DryRunCycle three-state", () => {
  // The cycle order is `null → true → false → null`. Each render asserts the
  // visible label, and clicking advances to the next state via onToggleDryRun.

  it("renders the 'default' state (dryRun null) and advances to true on click", async () => {
    const user = userEvent.setup();
    const handlers = noopHandlers();
    const job = jobFixture({ supportsDryRun: true, dryRun: null });
    renderWithTheme(
      <SchedulerJobsTable jobs={[job]} busy={false} {...handlers} />,
    );
    expect(screen.getByText("default")).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /toggle .* dry-run/i }),
    );
    expect(handlers.onToggleDryRun).toHaveBeenCalledWith(job, true);
  });

  it("renders the 'dry-run' state (dryRun true) and advances to false on click", async () => {
    const user = userEvent.setup();
    const handlers = noopHandlers();
    const job = jobFixture({ supportsDryRun: true, dryRun: true });
    renderWithTheme(
      <SchedulerJobsTable jobs={[job]} busy={false} {...handlers} />,
    );
    expect(screen.getByText("dry-run")).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /toggle .* dry-run/i }),
    );
    expect(handlers.onToggleDryRun).toHaveBeenCalledWith(job, false);
  });

  it("renders the 'live' state (dryRun false) and advances back to null on click", async () => {
    const user = userEvent.setup();
    const handlers = noopHandlers();
    const job = jobFixture({ supportsDryRun: true, dryRun: false });
    renderWithTheme(
      <SchedulerJobsTable jobs={[job]} busy={false} {...handlers} />,
    );
    expect(screen.getByText("live")).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /toggle .* dry-run/i }),
    );
    expect(handlers.onToggleDryRun).toHaveBeenCalledWith(job, null);
  });

  it("treats an undefined dryRun the same as null (default state)", () => {
    const job = jobFixture({ supportsDryRun: true, dryRun: undefined });
    renderWithTheme(
      <SchedulerJobsTable jobs={[job]} busy={false} {...noopHandlers()} />,
    );
    expect(screen.getByText("default")).toBeInTheDocument();
  });

  it("disables the dry-run chip while busy", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[jobFixture({ supportsDryRun: true, dryRun: true })]}
        busy={true}
        {...noopHandlers()}
      />,
    );
    // A disabled MUI Chip drops the role="button"; assert via aria-label.
    const chip = screen.getByLabelText(/toggle .* dry-run/i);
    expect(chip).toHaveAttribute("aria-disabled", "true");
  });
});

describe("SchedulerJobsTable — LastRunBadge", () => {
  it("renders 'Never run' when the job has no recorded outcome", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[jobFixture({ lastRunOutcome: null, lastRunAt: null })]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByText("Never run")).toBeInTheDocument();
  });

  it("renders 'Never run' when an outcome exists but no timestamp", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[jobFixture({ lastRunOutcome: "SUCCESS", lastRunAt: null })]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByText("Never run")).toBeInTheDocument();
  });

  it("renders a SUCCESS badge with the duration suffix", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[
          jobFixture({
            lastRunOutcome: "SUCCESS",
            lastRunAt: "2026-05-13T01:00:00Z",
            lastRunDurationMs: 230,
          }),
        ]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByText("Success")).toBeInTheDocument();
    expect(screen.getByText(/230 ms/)).toBeInTheDocument();
  });

  it("omits the duration suffix when durationMs is null", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[
          jobFixture({
            lastRunOutcome: "SUCCESS",
            lastRunAt: "2026-05-13T01:00:00Z",
            lastRunDurationMs: null,
          }),
        ]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByText("Success")).toBeInTheDocument();
    expect(screen.queryByText(/\bms\b/)).not.toBeInTheDocument();
  });

  it("renders a FAILED badge", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[
          jobFixture({
            lastRunOutcome: "FAILED",
            lastRunAt: "2026-05-13T01:00:00Z",
          }),
        ]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByText("Failed")).toBeInTheDocument();
  });

  // The remaining outcome labels share the "skipped/aborted" icon branch and
  // exercise the outcomeLabel + paletteFor switch arms.
  const otherOutcomes: Array<[SchedulerJobOutcome, string]> = [
    ["SKIPPED_DISABLED", "Disabled"],
    ["SKIPPED_LOCK", "Locked"],
    ["ABORTED_LIMIT", "Aborted"],
  ];

  it.each(otherOutcomes)("renders the %s outcome as '%s'", (outcome, label) => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[
          jobFixture({
            lastRunOutcome: outcome,
            lastRunAt: "2026-05-13T01:00:00Z",
          }),
        ]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});

describe("SchedulerJobsTable — run-now confirmation flow", () => {
  it("disables Run-now when the job is disabled", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[jobFixture({ enabled: false })]}
        busy={false}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByRole("button", { name: /run now/i })).toBeDisabled();
  });

  it("disables Run-now while busy even when the job is enabled", () => {
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[jobFixture({ enabled: true })]}
        busy={true}
        {...noopHandlers()}
      />,
    );
    expect(screen.getByRole("button", { name: /run now/i })).toBeDisabled();
  });

  it("opens the confirm dialog and calls onRunNow on confirm", async () => {
    // Run-now is wrapped in a Tooltip span with pointer-events: none; bypass
    // user-event's pointer check, mirroring SchedulerSection.test.tsx.
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    const handlers = noopHandlers();
    const job = jobFixture({ enabled: true });
    renderWithTheme(
      <SchedulerJobsTable jobs={[job]} busy={false} {...handlers} />,
    );

    await user.click(screen.getByRole("button", { name: /run now/i }));
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText(/run refresh-token-cleanup now\?/i),
    ).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: /run job/i }));
    expect(handlers.onRunNow).toHaveBeenCalledWith(job);
  });

  it("closes the confirm dialog without running when cancelled", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    const handlers = noopHandlers();
    renderWithTheme(
      <SchedulerJobsTable
        jobs={[jobFixture({ enabled: true })]}
        busy={false}
        {...handlers}
      />,
    );

    await user.click(screen.getByRole("button", { name: /run now/i }));
    const dialog = await screen.findByRole("dialog");
    // AppDialog's secondary action is the Cancel button.
    await user.click(within(dialog).getByRole("button", { name: /cancel/i }));

    // MUI animates the close, so the dialog unmounts asynchronously.
    await waitForElementToBeRemoved(() => screen.queryByRole("dialog"));
    expect(handlers.onRunNow).not.toHaveBeenCalled();
  });
});
