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
import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Typography,
} from "@mui/material";
import { AlertTriangle, HardDrive, RefreshCw, Trash2 } from "lucide-react";
import { Section } from "../../components/common/Section";
import { DataTable } from "../../components/common/DataTable";
import type { DataColumn } from "../../components/common/DataTable";
import { ConfirmDeleteDialog } from "../../components/common/ConfirmDeleteDialog";
import { Timestamp } from "../../components/common/Timestamp";
import { adminStorageConsistencyApi } from "../../api/config";
import { useUiStore } from "../../stores/uiStore";
import type {
  ConsistencyReport,
  MissingArtifact,
  OrphanedArtifact,
} from "../../api/generated/model";

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export function StorageConsistencySection() {
  const [report, setReport] = useState<ConsistencyReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [scanError, setScanError] = useState<string | null>(null);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [releaseToDelete, setReleaseToDelete] =
    useState<MissingArtifact | null>(null);
  const [deletingRelease, setDeletingRelease] = useState(false);
  const [bulkReleaseConfirmOpen, setBulkReleaseConfirmOpen] = useState(false);
  const [bulkReleaseDeleting, setBulkReleaseDeleting] = useState(false);
  const [bulkConfirmOpen, setBulkConfirmOpen] = useState(false);
  const [bulkDeleting, setBulkDeleting] = useState(false);
  const addToast = useUiStore((s) => s.addToast);

  // Mirrors the pattern in UsersSection: define the async work inside the
  // effect, drive re-fetch via a counter. The `react-hooks/set-state-in-effect`
  // rule (React 19) flags external functions but accepts this structure.
  useEffect(() => {
    async function load() {
      setLoading(true);
      setScanError(null);
      try {
        const res =
          await adminStorageConsistencyApi.getStorageConsistencyReport();
        setReport(res.data);
      } catch (err: unknown) {
        const axiosError = err as {
          response?: {
            status?: number;
            data?: { limit?: number; scannedSoFar?: number; message?: string };
          };
        };
        if (axiosError.response?.status === 409 && axiosError.response.data) {
          const { limit, message } = axiosError.response.data;
          setScanError(
            message ??
              `Scan aborted at ${limit ?? "the configured"} keys. Increase plugwerk.storage.consistency.max-keys-per-scan or use a targeted prefix scan.`,
          );
        } else {
          setScanError("Failed to scan storage. See server logs.");
        }
        setReport(null);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [refreshTrigger]);

  const refresh = () => setRefreshTrigger((n) => n + 1);

  async function handleDeleteRelease() {
    if (!releaseToDelete) return;
    setDeletingRelease(true);
    try {
      await adminStorageConsistencyApi.deleteOrphanedRelease({
        releaseId: releaseToDelete.releaseId,
      });
      addToast({
        message: `Removed orphaned release ${releaseToDelete.pluginId} ${releaseToDelete.version}.`,
        type: "success",
      });
      setReleaseToDelete(null);
      refresh();
    } catch {
      addToast({
        message: `Failed to remove release ${releaseToDelete.pluginId} ${releaseToDelete.version}.`,
        type: "error",
      });
    } finally {
      setDeletingRelease(false);
    }
  }

  async function handleBulkDeleteReleases() {
    if (!report || report.missingArtifacts.length === 0) return;
    setBulkReleaseDeleting(true);
    try {
      const res = await adminStorageConsistencyApi.deleteOrphanedReleases({
        orphanedReleaseDeletionRequest: {
          releaseIds: report.missingArtifacts.map((m) => m.releaseId),
        },
      });
      const { deleted, skipped } = res.data;
      addToast({
        message: skipped.length
          ? `Removed ${deleted.length} release row(s); skipped ${skipped.length} (file reappeared or row already gone).`
          : `Removed ${deleted.length} release row(s).`,
        type: "success",
      });
      setBulkReleaseConfirmOpen(false);
      refresh();
    } catch {
      addToast({ message: "Bulk release removal failed.", type: "error" });
    } finally {
      setBulkReleaseDeleting(false);
    }
  }

  async function handleBulkDelete() {
    if (!report || report.orphanedArtifacts.length === 0) return;
    setBulkDeleting(true);
    try {
      const res = await adminStorageConsistencyApi.deleteOrphanedArtifacts({
        orphanedArtifactDeletionRequest: {
          keys: report.orphanedArtifacts.map((a) => a.key),
        },
      });
      const { deleted, skipped } = res.data;
      addToast({
        message: skipped.length
          ? `Deleted ${deleted.length} orphaned object(s); skipped ${skipped.length} that were re-referenced.`
          : `Deleted ${deleted.length} orphaned object(s).`,
        type: "success",
      });
      setBulkConfirmOpen(false);
      refresh();
    } catch {
      addToast({ message: "Bulk delete failed.", type: "error" });
    } finally {
      setBulkDeleting(false);
    }
  }

  const missingColumns: DataColumn<MissingArtifact>[] = [
    {
      key: "plugin",
      header: "Plugin",
      render: (row) => (
        <Box>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {row.pluginId}
          </Typography>
          <Typography variant="caption" sx={{ color: "text.secondary" }}>
            {row.version}
          </Typography>
        </Box>
      ),
    },
    {
      key: "artifactKey",
      header: "Artifact key",
      render: (row) => (
        <Typography
          variant="body2"
          sx={{
            fontFamily: "monospace",
            fontSize: "0.8rem",
            wordBreak: "break-all",
          }}
        >
          {row.artifactKey}
        </Typography>
      ),
    },
    {
      key: "action",
      header: "",
      align: "right",
      width: 140,
      render: (row) => (
        <Button
          size="small"
          color="error"
          startIcon={<Trash2 size={14} />}
          onClick={() => setReleaseToDelete(row)}
        >
          Remove row
        </Button>
      ),
    },
  ];

  const orphanedColumns: DataColumn<OrphanedArtifact>[] = [
    {
      key: "key",
      header: "Storage key",
      render: (row) => (
        <Typography
          variant="body2"
          sx={{
            fontFamily: "monospace",
            fontSize: "0.8rem",
            wordBreak: "break-all",
          }}
        >
          {row.key}
        </Typography>
      ),
    },
    {
      key: "size",
      header: "Size",
      align: "right",
      width: 100,
      render: (row) => (
        <Typography variant="body2">{formatBytes(row.sizeBytes)}</Typography>
      ),
    },
    {
      key: "age",
      header: "Age",
      align: "right",
      width: 140,
      render: (row) => (
        <Box>
          <Typography variant="body2">{row.ageHours}h</Typography>
          <Timestamp date={row.lastModified} variant="relative" />
        </Box>
      ),
    },
  ];

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Storage consistency
          </Typography>
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            Reconcile the artifact database with object storage. Missing rows
            point to files that disappeared; orphaned files have no database row
            pointing at them.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<RefreshCw size={14} />}
          onClick={refresh}
          disabled={loading}
        >
          Rescan
        </Button>
      </Box>

      {scanError && (
        <Alert severity="warning" icon={<AlertTriangle size={18} />}>
          {scanError}
        </Alert>
      )}

      {loading && !report && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress size={28} />
        </Box>
      )}

      {report && (
        <>
          <Section
            icon={<HardDrive size={18} />}
            title="Missing artifacts"
            description={`${report.missingArtifacts.length} release row(s) point at storage keys that are gone.`}
          >
            <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 1 }}>
              <Button
                variant="contained"
                color="error"
                size="small"
                startIcon={<Trash2 size={14} />}
                disabled={
                  report.missingArtifacts.length === 0 || bulkReleaseDeleting
                }
                onClick={() => setBulkReleaseConfirmOpen(true)}
              >
                Remove all
              </Button>
            </Box>
            <DataTable
              ariaLabel="Missing artifacts"
              columns={missingColumns}
              rows={report.missingArtifacts}
              keyFn={(r) => r.releaseId}
              emptyMessage="No releases reference missing storage files."
            />
          </Section>

          <Section
            icon={<HardDrive size={18} />}
            title="Orphaned artifacts"
            description={`${report.orphanedArtifacts.length} storage object(s) have no release row.`}
          >
            <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 1 }}>
              <Button
                variant="contained"
                color="error"
                size="small"
                startIcon={<Trash2 size={14} />}
                disabled={report.orphanedArtifacts.length === 0 || bulkDeleting}
                onClick={() => setBulkConfirmOpen(true)}
              >
                Remove all
              </Button>
            </Box>
            <DataTable
              ariaLabel="Orphaned artifacts"
              columns={orphanedColumns}
              rows={report.orphanedArtifacts}
              keyFn={(r) => r.key}
              emptyMessage="No orphaned objects in storage."
            />
          </Section>

          <Typography variant="caption" sx={{ color: "text.disabled" }}>
            Scanned <Timestamp date={report.scannedAt} variant="relative" /> —{" "}
            <Chip label={`${report.totalDbRows} DB row(s)`} size="small" />{" "}
            <Chip
              label={`${report.totalStorageObjects} storage object(s)`}
              size="small"
            />
          </Typography>
        </>
      )}

      <ConfirmDeleteDialog
        open={releaseToDelete !== null}
        onCancel={() => setReleaseToDelete(null)}
        onConfirm={handleDeleteRelease}
        title="Remove orphaned release row?"
        actionLabel="Remove row"
        message={
          releaseToDelete
            ? `Remove the database row for ${releaseToDelete.pluginId} ${releaseToDelete.version}. Idempotent: if the row is already gone, this is a no-op.`
            : ""
        }
        loading={deletingRelease}
      />
      <ConfirmDeleteDialog
        open={bulkReleaseConfirmOpen}
        onCancel={() => setBulkReleaseConfirmOpen(false)}
        onConfirm={handleBulkDeleteReleases}
        title="Remove all missing release rows?"
        actionLabel="Remove rows"
        message={
          report
            ? `This will remove ${report.missingArtifacts.length} release row(s) from the database. Rows whose storage file has reappeared since the last scan are skipped automatically.`
            : ""
        }
        loading={bulkReleaseDeleting}
      />
      <ConfirmDeleteDialog
        open={bulkConfirmOpen}
        onCancel={() => setBulkConfirmOpen(false)}
        onConfirm={handleBulkDelete}
        title="Delete all listed orphaned objects?"
        actionLabel="Delete objects"
        message={
          report
            ? `This will delete ${report.orphanedArtifacts.length} object(s) from storage. Objects that have been re-referenced since the last scan are skipped automatically.`
            : ""
        }
        loading={bulkDeleting}
      />
    </Box>
  );
}
