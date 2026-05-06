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
import { useState, useCallback, useEffect } from "react";
import { Box, Typography, Alert, IconButton } from "@mui/material";
import { UploadCloud, FileBox, X } from "lucide-react";
import { AppDialog } from "../common/AppDialog";
import { useDropzone } from "react-dropzone";
import { useUiStore } from "../../stores/uiStore";
import { useAuthStore } from "../../stores/authStore";
import { useConfigStore } from "../../stores/configStore";
import { useUploadFiles } from "../../hooks/useUploadFiles";
import { tokens } from "../../theme/tokens";

export function UploadModal() {
  const uploadModalOpen = useUiStore((s) => s.uploadModalOpen);
  const closeUploadModal = useUiStore((s) => s.closeUploadModal);
  const namespace = useAuthStore((s) => s.namespace);
  const maxFileSizeMb = useConfigStore((s) => s.maxFileSizeMb);
  const fetchConfig = useConfigStore((s) => s.fetchConfig);
  const { uploadFiles } = useUploadFiles();

  const [files, setFiles] = useState<readonly File[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (uploadModalOpen) fetchConfig();
  }, [uploadModalOpen, fetchConfig]);

  const onDrop = useCallback((accepted: File[]) => {
    if (accepted.length === 0) return;
    setFiles((prev) => {
      const existingNames = new Set(prev.map((f) => f.name));
      const newFiles = accepted.filter((f) => !existingNames.has(f.name));
      return [...prev, ...newFiles];
    });
    setError(null);
  }, []);

  const removeFile = useCallback((name: string) => {
    setFiles((prev) => prev.filter((f) => f.name !== name));
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      "application/java-archive": [".jar"],
      "application/zip": [".jar", ".zip"],
    },
    multiple: true,
  });

  function handleClose() {
    setFiles([]);
    setError(null);
    closeUploadModal();
  }

  async function handleUpload() {
    if (files.length === 0) {
      setError("Please select at least one .jar or .zip file.");
      return;
    }

    const oversized = files.filter((f) => f.size > maxFileSizeMb * 1024 * 1024);
    if (oversized.length > 0) {
      const names = oversized.map((f) => f.name).join(", ");
      setError(`Files too large (max ${maxFileSizeMb} MB): ${names}`);
      return;
    }

    setError(null);
    // Close the dialog immediately — progress tracked via UploadProgressPanel
    const filesToUpload = [...files];
    handleClose();

    if (namespace) {
      await uploadFiles(filesToUpload, namespace);
    }
  }

  const actionLabel =
    files.length <= 1 ? "Upload Release" : `Upload ${files.length} Releases`;

  return (
    <AppDialog
      open={uploadModalOpen}
      onClose={handleClose}
      title="Upload Plugin Release"
      description="Drop plugin .jar or .zip files. All metadata (plugin ID, version, dependencies) is read from the descriptor inside the archive."
      actionLabel={actionLabel}
      onAction={handleUpload}
      actionDisabled={files.length === 0}
      maxWidth={600}
    >
      {error && (
        <Alert severity="error" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      <Box
        {...getRootProps()}
        sx={{
          border: `2px dashed ${isDragActive ? tokens.color.primary : tokens.color.gray20}`,
          borderRadius: tokens.radius.card,
          p: 4,
          textAlign: "center",
          cursor: "pointer",
          background: isDragActive
            ? tokens.color.primaryLight + "22"
            : "background.default",
          transition: "border-color 0.15s, background 0.15s",
          "&:hover": { borderColor: tokens.color.primary },
        }}
      >
        <input
          {...getInputProps()}
          aria-label="Select plugin JAR or ZIP files"
        />
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 1.5,
          }}
        >
          <UploadCloud size={36} color={tokens.color.gray40} />
          <Typography variant="body2" sx={{
            fontWeight: 600
          }}>
            {isDragActive
              ? "Drop the files here…"
              : "Drag & drop .jar or .zip files here"}
          </Typography>
          <Typography variant="caption" sx={{
            color: "text.disabled"
          }}>
            or click to browse · Max. {maxFileSizeMb} MB per file
          </Typography>
        </Box>
      </Box>
      {/* Selected file list */}
      {files.length > 0 && (
        <Box sx={{ mt: 2, display: "flex", flexDirection: "column", gap: 0.5 }}>
          {files.map((file) => (
            <Box
              key={file.name}
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 1.5,
                px: 1.5,
                py: 1,
                borderRadius: tokens.radius.input,
                border: "1px solid",
                borderColor: "divider",
              }}
            >
              <FileBox size={16} color={tokens.color.primary} />
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography
                  variant="body2"
                  sx={{
                    fontWeight: 500,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap"
                  }}>
                  {file.name}
                </Typography>
              </Box>
              <Typography
                variant="caption"
                sx={{
                  color: "text.disabled",
                  flexShrink: 0
                }}>
                {(file.size / 1024 / 1024).toFixed(2)} MB
              </Typography>
              <IconButton
                size="small"
                onClick={(e) => {
                  e.stopPropagation();
                  removeFile(file.name);
                }}
                aria-label={`Remove ${file.name}`}
                sx={{ p: 0.25, color: "text.disabled" }}
              >
                <X size={14} />
              </IconButton>
            </Box>
          ))}
        </Box>
      )}
    </AppDialog>
  );
}
