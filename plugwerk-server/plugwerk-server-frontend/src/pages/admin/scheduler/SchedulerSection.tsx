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
import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Typography,
} from "@mui/material";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { adminSchedulerApi } from "../../../api/config";
import { useUiStore } from "../../../stores/uiStore";
import type { SchedulerJobDto } from "../../../api/generated/model";
import { SchedulerStatsStrip } from "./SchedulerStatsStrip";
import { SchedulerJobsTable } from "./SchedulerJobsTable";

const POLL_INTERVAL_MS = 15_000;

/**
 * Live dashboard for the five (currently) scheduled jobs (#516).
 *
 * The page polls every 15 s in the background so the operator's view of
 * "last run" stays fresh without manual rescans. Toggles and run-now go
 * through the admin endpoints; on success the job list is refetched
 * eagerly so the row reflects the new state without waiting for the
 * next poll tick.
 */
export function SchedulerSection() {
  const [jobs, setJobs] = useState<SchedulerJobDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);
  const addToast = useUiStore((s) => s.addToast);

  useEffect(() => {
    let cancelled = false;
    async function load(initial: boolean) {
      if (initial) setLoading(true);
      try {
        const res = await adminSchedulerApi.listSchedulerJobs();
        if (cancelled) return;
        setJobs(res.data);
        setError(null);
      } catch {
        if (cancelled) return;
        setError("Failed to load scheduler jobs. See server logs.");
      } finally {
        if (initial && !cancelled) setLoading(false);
      }
    }
    load(true);
    const interval = window.setInterval(() => load(false), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [refreshTick]);

  const refresh = () => setRefreshTick((n) => n + 1);

  async function handleToggleEnabled(job: SchedulerJobDto, next: boolean) {
    setBusy(true);
    try {
      await adminSchedulerApi.updateSchedulerJob({
        name: job.name,
        // The generator drops `null` from the TS type even though the OpenAPI
        // spec marks dryRun as nullable; sending undefined deserialises to
        // null on the server, which matches the "clear override" semantics.
        schedulerJobUpdateRequest: {
          enabled: next,
          dryRun: job.dryRun ?? undefined,
        },
      });
      addToast({
        message: `${job.name} ${next ? "enabled" : "disabled"}.`,
        type: "success",
      });
      refresh();
    } catch {
      addToast({ message: `Failed to update ${job.name}.`, type: "error" });
    } finally {
      setBusy(false);
    }
  }

  async function handleToggleDryRun(
    job: SchedulerJobDto,
    next: boolean | null,
  ) {
    setBusy(true);
    try {
      await adminSchedulerApi.updateSchedulerJob({
        name: job.name,
        schedulerJobUpdateRequest: {
          enabled: job.enabled,
          dryRun: next ?? undefined,
        },
      });
      addToast({
        message:
          next === null
            ? `${job.name} dry-run cleared (using yaml default).`
            : `${job.name} dry-run = ${next}.`,
        type: "success",
      });
      refresh();
    } catch {
      addToast({ message: `Failed to update ${job.name}.`, type: "error" });
    } finally {
      setBusy(false);
    }
  }

  async function handleRunNow(job: SchedulerJobDto) {
    setBusy(true);
    try {
      const res = await adminSchedulerApi.runSchedulerJobNow({
        name: job.name,
      });
      const outcome = res.data.outcome;
      addToast({
        message:
          `${job.name}: ${outcome}` +
          (res.data.durationMs != null ? ` (${res.data.durationMs} ms)` : ""),
        type:
          outcome === "SUCCESS"
            ? "success"
            : outcome === "FAILED"
              ? "error"
              : "info",
      });
      refresh();
    } catch {
      addToast({ message: `Failed to run ${job.name}.`, type: "error" });
    } finally {
      setBusy(false);
    }
  }

  const stats = useMemo(() => {
    const enabled = jobs.filter((j) => j.enabled).length;
    const disabled = jobs.length - enabled;
    const failed = jobs.filter((j) => j.lastRunOutcome === "FAILED").length;
    const totalRuns = jobs.reduce((sum, j) => sum + j.runCountTotal, 0);
    return { total: jobs.length, enabled, disabled, failed, totalRuns };
  }, [jobs]);

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: 2,
        }}
      >
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Scheduler
          </Typography>
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            Operator dashboard for scheduled jobs. Cron patterns and tuning
            constants stay in <code>application.yml</code>; this page controls
            the runtime toggles and surfaces last-run state. Auto-refreshes
            every 15 s.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<RefreshCw size={14} />}
          onClick={refresh}
          disabled={loading || busy}
        >
          Rescan
        </Button>
      </Box>

      {error && (
        <Alert severity="warning" icon={<AlertTriangle size={18} />}>
          {error}
        </Alert>
      )}

      {loading && jobs.length === 0 ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress size={28} />
        </Box>
      ) : (
        <>
          <SchedulerStatsStrip stats={stats} />
          <SchedulerJobsTable
            jobs={jobs}
            busy={busy}
            onToggleEnabled={handleToggleEnabled}
            onToggleDryRun={handleToggleDryRun}
            onRunNow={handleRunNow}
          />
        </>
      )}
    </Box>
  );
}
