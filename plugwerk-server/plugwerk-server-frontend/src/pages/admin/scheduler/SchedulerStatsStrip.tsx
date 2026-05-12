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
import { Box, Typography } from "@mui/material";
import { tokens } from "../../../theme/tokens";

interface Stats {
  readonly total: number;
  readonly enabled: number;
  readonly disabled: number;
  readonly failed: number;
  readonly totalRuns: number;
}

/**
 * Four-card overview strip above the scheduler jobs table.
 *
 * - Active jobs uses the primary accent so the operator immediately sees
 *   "how many of my workers are actually doing anything"
 * - Failed-last-run uses the yanked badge palette when > 0 — a single
 *   failed job is the only thing on this page the operator typically
 *   wants to act on; everything else is steady-state.
 * - Total runs is a cumulative counter; long-term history lives in
 *   Prometheus, this is just a sanity number for the dashboard.
 */
export function SchedulerStatsStrip({ stats }: { readonly stats: Stats }) {
  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr 1fr", sm: "repeat(4, 1fr)" },
        gap: 1.5,
      }}
    >
      <Stat
        label="Active jobs"
        value={`${stats.enabled} / ${stats.total}`}
        accent="primary"
      />
      <Stat label="Disabled" value={stats.disabled.toLocaleString()} />
      <Stat
        label="Failed last run"
        value={stats.failed.toLocaleString()}
        accent={stats.failed > 0 ? "danger" : undefined}
      />
      <Stat label="Total runs" value={stats.totalRuns.toLocaleString()} />
    </Box>
  );
}

type Accent = "primary" | "danger";

function Stat({
  label,
  value,
  accent,
}: {
  readonly label: string;
  readonly value: string;
  readonly accent?: Accent;
}) {
  const palette =
    accent === "primary"
      ? {
          border: tokens.color.primary,
          bg: tokens.color.primaryLight,
          label: tokens.color.primaryDark,
          value: tokens.color.primaryDark,
        }
      : accent === "danger"
        ? {
            border: tokens.badge.yanked.text,
            bg: tokens.badge.yanked.bg,
            label: tokens.badge.yanked.text,
            value: tokens.badge.yanked.text,
          }
        : null;
  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: palette?.border ?? "divider",
        borderRadius: tokens.radius.card,
        px: 2,
        py: 1.25,
        bgcolor: palette?.bg ?? "background.paper",
      }}
    >
      <Typography
        variant="caption"
        sx={{
          color: palette?.label ?? "text.disabled",
          textTransform: "uppercase",
          letterSpacing: "0.06em",
          fontWeight: 600,
          fontSize: "0.7rem",
        }}
      >
        {label}
      </Typography>
      <Typography
        variant="h6"
        sx={{
          fontWeight: 700,
          mt: 0.25,
          fontFeatureSettings: '"tnum"',
          color: palette?.value ?? "text.primary",
        }}
      >
        {value}
      </Typography>
    </Box>
  );
}
