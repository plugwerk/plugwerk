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
import { useState } from "react";
import { Box, Button, Chip, Switch, Tooltip, Typography } from "@mui/material";
import {
  CircleAlert,
  CircleCheckBig,
  Eye,
  EyeOff,
  MinusCircle,
  Play,
} from "lucide-react";
import { tokens } from "../../../theme/tokens";
import { Timestamp } from "../../../components/common/Timestamp";
import { ConfirmDeleteDialog } from "../../../components/common/ConfirmDeleteDialog";
import type {
  SchedulerJobDto,
  SchedulerJobOutcome,
} from "../../../api/generated/model";

interface SchedulerJobsTableProps {
  readonly jobs: readonly SchedulerJobDto[];
  readonly busy: boolean;
  readonly onToggleEnabled: (job: SchedulerJobDto, next: boolean) => void;
  readonly onToggleDryRun: (job: SchedulerJobDto, next: boolean | null) => void;
  readonly onRunNow: (job: SchedulerJobDto) => void;
}

/**
 * Five-column live-status table for the scheduler dashboard (#516).
 * Custom (not the generic `DataTable`) because per-row actions vary by
 * `supportsDryRun` and because we want monospace job-IDs with sub-line
 * descriptions, which the generic table cannot express cleanly.
 */
export function SchedulerJobsTable({
  jobs,
  busy,
  onToggleEnabled,
  onToggleDryRun,
  onRunNow,
}: SchedulerJobsTableProps) {
  const [runConfirm, setRunConfirm] = useState<SchedulerJobDto | null>(null);

  if (jobs.length === 0) {
    return (
      <Box
        sx={{
          py: 5,
          textAlign: "center",
          border: "1px dashed",
          borderColor: "divider",
          borderRadius: tokens.radius.card,
          color: "text.disabled",
        }}
      >
        <Typography variant="body2">No scheduled jobs registered.</Typography>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: "divider",
        borderRadius: tokens.radius.card,
        overflow: "hidden",
      }}
    >
      <Box
        component="table"
        role="table"
        aria-label="Scheduled jobs"
        sx={{ width: "100%", borderCollapse: "collapse" }}
      >
        <Box component="thead">
          <Box component="tr">
            <Header>Job</Header>
            <Header width={140}>Cron</Header>
            <Header width={120}>Enabled</Header>
            <Header width={120}>Dry-run</Header>
            <Header width={170}>Last run</Header>
            <Header width={120} align="right">
              Total runs
            </Header>
            <Header width={120} align="right" />
          </Box>
        </Box>
        <Box component="tbody">
          {jobs.map((job) => (
            <Box
              component="tr"
              key={job.name}
              sx={{
                bgcolor: "background.paper",
                "&:hover": { bgcolor: "background.default" },
              }}
            >
              <Cell>
                <Typography
                  variant="body2"
                  sx={{ fontFamily: "monospace", fontSize: "0.82rem" }}
                >
                  {job.name}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{ color: "text.disabled", display: "block", mt: 0.25 }}
                >
                  {job.description}
                </Typography>
              </Cell>
              <Cell>
                <Chip
                  label={job.cronExpression}
                  size="small"
                  sx={{
                    fontFamily: "monospace",
                    bgcolor: tokens.badge.version.bg,
                    color: tokens.badge.version.text,
                    height: 20,
                    fontSize: "0.7rem",
                  }}
                />
              </Cell>
              <Cell>
                <Switch
                  size="small"
                  checked={job.enabled}
                  disabled={busy}
                  onChange={(e) => onToggleEnabled(job, e.target.checked)}
                  slotProps={{
                    input: { "aria-label": `Toggle ${job.name} enabled` },
                  }}
                />
              </Cell>
              <Cell>
                {job.supportsDryRun ? (
                  <DryRunCycle
                    value={job.dryRun ?? null}
                    busy={busy}
                    onChange={(next) => onToggleDryRun(job, next)}
                    label={`Toggle ${job.name} dry-run`}
                  />
                ) : (
                  <Typography variant="caption" sx={{ color: "text.disabled" }}>
                    —
                  </Typography>
                )}
              </Cell>
              <Cell>
                <LastRunBadge
                  outcome={job.lastRunOutcome ?? null}
                  durationMs={job.lastRunDurationMs ?? null}
                  at={job.lastRunAt ?? null}
                />
              </Cell>
              <Cell align="right">
                <Typography
                  variant="body2"
                  sx={{ fontFeatureSettings: '"tnum"' }}
                >
                  {job.runCountTotal.toLocaleString()}
                </Typography>
              </Cell>
              <Cell align="right">
                <Tooltip
                  title={
                    job.enabled
                      ? "Run now"
                      : "Enable the job before triggering a manual run"
                  }
                  arrow
                >
                  <span>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<Play size={12} />}
                      onClick={() => setRunConfirm(job)}
                      disabled={busy || !job.enabled}
                    >
                      Run now
                    </Button>
                  </span>
                </Tooltip>
              </Cell>
            </Box>
          ))}
        </Box>
      </Box>

      <ConfirmDeleteDialog
        open={runConfirm !== null}
        onCancel={() => setRunConfirm(null)}
        onConfirm={() => {
          if (runConfirm) onRunNow(runConfirm);
          setRunConfirm(null);
        }}
        title={runConfirm ? `Run ${runConfirm.name} now?` : ""}
        actionLabel="Run job"
        message={
          runConfirm
            ? `Triggers an off-schedule execution of ${runConfirm.name}. It goes through the same ShedLock as the cron tick, so a concurrent regular run will skip this one.`
            : ""
        }
        loading={busy}
      />
    </Box>
  );
}

/**
 * Three-state dry-run cycle: `null → true → false → null`. A button
 * (rather than a Switch) because the state is ternary; the label and
 * icon make the current value explicit.
 */
function DryRunCycle({
  value,
  busy,
  onChange,
  label,
}: {
  readonly value: boolean | null;
  readonly busy: boolean;
  readonly onChange: (next: boolean | null) => void;
  readonly label: string;
}) {
  const next: boolean | null = value === null ? true : value ? false : null;
  const icon =
    value === null ? (
      <MinusCircle size={14} />
    ) : value ? (
      <Eye size={14} />
    ) : (
      <EyeOff size={14} />
    );
  const text = value === null ? "default" : value ? "dry-run" : "live";
  const color =
    value === null
      ? "default"
      : value
        ? "info"
        : ("success" as "default" | "info" | "success");
  return (
    <Tooltip
      title={
        value === null
          ? "Following yaml default. Click to force dry-run."
          : value
            ? "Forcing dry-run. Click to switch to live."
            : "Forcing live mode. Click to clear override."
      }
      arrow
    >
      <span>
        <Chip
          icon={icon}
          label={text}
          size="small"
          color={color}
          variant={value === null ? "outlined" : "filled"}
          onClick={() => onChange(next)}
          disabled={busy}
          clickable
          aria-label={label}
          sx={{ fontFamily: "monospace", fontSize: "0.7rem", height: 22 }}
        />
      </span>
    </Tooltip>
  );
}

function LastRunBadge({
  outcome,
  durationMs,
  at,
}: {
  readonly outcome: SchedulerJobOutcome | null;
  readonly durationMs: number | null;
  readonly at: string | null;
}) {
  if (!outcome || !at) {
    return (
      <Typography variant="caption" sx={{ color: "text.disabled" }}>
        Never run
      </Typography>
    );
  }
  const palette = paletteFor(outcome);
  const icon =
    outcome === "SUCCESS" ? (
      <CircleCheckBig size={12} />
    ) : outcome === "FAILED" ? (
      <CircleAlert size={12} />
    ) : (
      <MinusCircle size={12} />
    );
  return (
    <Box>
      <Chip
        icon={icon}
        label={outcomeLabel(outcome)}
        size="small"
        sx={{
          bgcolor: palette.bg,
          color: palette.text,
          height: 20,
          fontSize: "0.7rem",
          fontWeight: 600,
        }}
      />
      <Typography
        variant="caption"
        sx={{ color: "text.secondary", display: "block", mt: 0.25 }}
      >
        <Timestamp date={at} variant="relative" />
        {durationMs != null && (
          <Box component="span" sx={{ ml: 0.5, fontFeatureSettings: '"tnum"' }}>
            · {durationMs} ms
          </Box>
        )}
      </Typography>
    </Box>
  );
}

function outcomeLabel(o: SchedulerJobOutcome): string {
  switch (o) {
    case "SUCCESS":
      return "Success";
    case "FAILED":
      return "Failed";
    case "SKIPPED_DISABLED":
      return "Disabled";
    case "SKIPPED_LOCK":
      return "Locked";
    case "ABORTED_LIMIT":
      return "Aborted";
  }
}

function paletteFor(o: SchedulerJobOutcome): { bg: string; text: string } {
  switch (o) {
    case "SUCCESS":
      return tokens.badge.published;
    case "FAILED":
      return tokens.badge.yanked;
    case "SKIPPED_DISABLED":
    case "SKIPPED_LOCK":
      return tokens.badge.version;
    case "ABORTED_LIMIT":
      return tokens.badge.draft;
  }
}

function Header({
  children,
  width,
  align,
}: {
  readonly children?: React.ReactNode;
  readonly width?: number;
  readonly align?: "left" | "right" | "center";
}) {
  return (
    <Box
      component="th"
      sx={{
        textAlign: align ?? "left",
        width,
        px: 2,
        py: 1,
        borderBottom: "1px solid",
        borderColor: "divider",
        bgcolor: "background.default",
        fontSize: "0.7rem",
        fontWeight: 600,
        color: "text.secondary",
        textTransform: "uppercase",
        letterSpacing: "0.06em",
      }}
    >
      {children}
    </Box>
  );
}

function Cell({
  children,
  align,
}: {
  readonly children: React.ReactNode;
  readonly align?: "left" | "right" | "center";
}) {
  return (
    <Box
      component="td"
      sx={{
        textAlign: align ?? "left",
        px: 2,
        py: 1.25,
        borderBottom: "1px solid",
        borderColor: "divider",
        fontSize: "0.875rem",
        verticalAlign: "middle",
      }}
    >
      {children}
    </Box>
  );
}
