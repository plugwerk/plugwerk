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
import { AlertTriangle, FileX, HardDrive, RefreshCw } from "lucide-react";
import { Section } from "../../components/common/Section";
import { ConfirmDeleteDialog } from "../../components/common/ConfirmDeleteDialog";
import { Timestamp } from "../../components/common/Timestamp";
import { adminStorageConsistencyApi } from "../../api/config";
import { useUiStore } from "../../stores/uiStore";
import type {
  ConsistencyReport,
  MissingArtifact,
  OrphanedArtifact,
} from "../../api/generated/model";
import { MissingArtifactsTable } from "./storage-consistency/MissingArtifactsTable";
import { OrphanedArtifactsTable } from "./storage-consistency/OrphanedArtifactsTable";

export function StorageConsistencySection() {
  const [report, setReport] = useState<ConsistencyReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [scanError, setScanError] = useState<string | null>(null);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  // Pending single-row delete and bulk dialogs share the same loading lock
  // so the user cannot trigger overlapping mutations against the same scan
  // snapshot — otherwise the UI would render against a half-stale view.
  const [busy, setBusy] = useState(false);
  const [releaseToDelete, setReleaseToDelete] =
    useState<MissingArtifact | null>(null);
  const [releasesToDelete, setReleasesToDelete] = useState<
    readonly MissingArtifact[] | null
  >(null);
  const [artifactsToDelete, setArtifactsToDelete] = useState<
    readonly OrphanedArtifact[] | null
  >(null);

  const addToast = useUiStore((s) => s.addToast);

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
    setBusy(true);
    try {
      await adminStorageConsistencyApi.deleteOrphanedRelease({
        releaseId: releaseToDelete.releaseId,
      });
      addToast({
        message: `Removed ${releaseToDelete.pluginId} ${releaseToDelete.version}.`,
        type: "success",
      });
      setReleaseToDelete(null);
      refresh();
    } catch {
      addToast({
        message: `Failed to remove ${releaseToDelete.pluginId} ${releaseToDelete.version}.`,
        type: "error",
      });
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteReleases() {
    if (!releasesToDelete || releasesToDelete.length === 0) return;
    setBusy(true);
    try {
      const res = await adminStorageConsistencyApi.deleteOrphanedReleases({
        orphanedReleaseDeletionRequest: {
          releaseIds: releasesToDelete.map((r) => r.releaseId),
        },
      });
      const { deleted, skipped } = res.data;
      addToast({
        message: skipped.length
          ? `Removed ${deleted.length} row(s); skipped ${skipped.length} (file reappeared or row already gone).`
          : `Removed ${deleted.length} row(s).`,
        type: "success",
      });
      setReleasesToDelete(null);
      refresh();
    } catch {
      addToast({ message: "Bulk release removal failed.", type: "error" });
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteArtifacts() {
    if (!artifactsToDelete || artifactsToDelete.length === 0) return;
    setBusy(true);
    try {
      const res = await adminStorageConsistencyApi.deleteOrphanedArtifacts({
        orphanedArtifactDeletionRequest: {
          keys: artifactsToDelete.map((r) => r.key),
        },
      });
      const { deleted, skipped } = res.data;
      addToast({
        message: skipped.length
          ? `Deleted ${deleted.length} object(s); skipped ${skipped.length} that were re-referenced.`
          : `Deleted ${deleted.length} object(s).`,
        type: "success",
      });
      setArtifactsToDelete(null);
      refresh();
    } catch {
      addToast({ message: "Bulk delete failed.", type: "error" });
    } finally {
      setBusy(false);
    }
  }

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
          disabled={loading || busy}
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
            icon={<FileX size={18} />}
            title="Missing artifacts"
            description="Release rows that point at a storage object that is no longer there."
          >
            <MissingArtifactsTable
              rows={report.missingArtifacts}
              busy={busy}
              onDeleteOne={(row) => setReleaseToDelete(row)}
              onDeleteMany={(targets) => setReleasesToDelete(targets)}
            />
          </Section>

          <Section
            icon={<HardDrive size={18} />}
            title="Orphaned artifacts"
            description="Storage objects with no plugin_release row referencing them."
          >
            <OrphanedArtifactsTable
              rows={report.orphanedArtifacts}
              busy={busy}
              onDeleteMany={(targets) => setArtifactsToDelete(targets)}
            />
          </Section>

          <Box
            sx={{
              display: "flex",
              flexWrap: "wrap",
              gap: 1,
              alignItems: "center",
              color: "text.disabled",
            }}
          >
            <Typography variant="caption">
              Scanned <Timestamp date={report.scannedAt} variant="relative" />
            </Typography>
            <Chip label={`${report.totalDbRows} DB row(s)`} size="small" />
            <Chip
              label={`${report.totalStorageObjects} storage object(s)`}
              size="small"
            />
          </Box>
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
        loading={busy}
      />
      <ConfirmDeleteDialog
        open={releasesToDelete !== null}
        onCancel={() => setReleasesToDelete(null)}
        onConfirm={handleDeleteReleases}
        title="Remove missing release rows?"
        actionLabel="Remove rows"
        message={
          releasesToDelete
            ? `This will remove ${releasesToDelete.length} release row(s) from the database. Rows whose storage file has reappeared since the last scan are skipped automatically.`
            : ""
        }
        loading={busy}
      />
      <ConfirmDeleteDialog
        open={artifactsToDelete !== null}
        onCancel={() => setArtifactsToDelete(null)}
        onConfirm={handleDeleteArtifacts}
        title="Delete orphaned storage objects?"
        actionLabel="Delete objects"
        message={
          artifactsToDelete
            ? `This will delete ${artifactsToDelete.length} object(s) from storage. Objects that have been re-referenced since the last scan are skipped automatically.`
            : ""
        }
        loading={busy}
      />
    </Box>
  );
}
