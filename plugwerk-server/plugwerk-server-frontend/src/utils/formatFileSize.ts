// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH

/**
 * Formats a byte count into a human-readable file size string.
 *
 * - < 1 KB  → "N B"
 * - < 1 MB  → "N.N KB"
 * - ≥ 1 MB  → "N.N MB"
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
