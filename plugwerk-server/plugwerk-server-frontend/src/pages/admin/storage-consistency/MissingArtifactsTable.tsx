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
import { useMemo, useState } from "react";
import {
  Box,
  Button,
  Checkbox,
  Chip,
  InputAdornment,
  TablePagination,
  TextField,
  Typography,
} from "@mui/material";
import { FileX, Search, Trash2 } from "lucide-react";
import type { MissingArtifact } from "../../../api/generated/model";
import { tokens } from "../../../theme/tokens";
import { useTableState } from "./useTableState";
import type { SortDefinition } from "./useTableState";

type SortKey = "plugin" | "version" | "key";

const SORTS: SortDefinition<MissingArtifact, SortKey>[] = [
  {
    key: "plugin",
    label: "Plugin",
    compare: (a, b) => a.pluginId.localeCompare(b.pluginId),
  },
  {
    key: "version",
    label: "Version",
    compare: (a, b) =>
      a.version.localeCompare(b.version, undefined, { numeric: true }),
  },
  {
    key: "key",
    label: "Artifact key",
    compare: (a, b) => a.artifactKey.localeCompare(b.artifactKey),
  },
];

const PAGE_SIZE_OPTIONS = [25, 50, 100];

interface MissingArtifactsTableProps {
  readonly rows: readonly MissingArtifact[];
  readonly busy: boolean;
  readonly onDeleteOne: (row: MissingArtifact) => void;
  readonly onDeleteMany: (rows: readonly MissingArtifact[]) => void;
}

/**
 * Triage view for `plugin_release` rows whose artifact file is missing
 * from storage. Optimised for the long-list case: debounced search,
 * column-sortable, paginated, multi-select for surgical cleanup. The
 * legacy single-row delete button is retained so admins do not have to
 * select-then-delete for one-off cases.
 */
export function MissingArtifactsTable({
  rows,
  busy,
  onDeleteOne,
  onDeleteMany,
}: MissingArtifactsTableProps) {
  const state = useTableState<MissingArtifact, SortKey>({
    rows,
    searchFields: (r) => [r.pluginId, r.version, r.artifactKey],
    sorts: SORTS,
    initialSortKey: "plugin",
  });

  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const distinctPlugins = useMemo(
    () => new Set(rows.map((r) => r.pluginId)).size,
    [rows],
  );

  // Reconcile selection whenever filter/sort/page change — a row that
  // drops out of view should not stay "selected" silently.
  const visibleIds = useMemo(
    () => new Set(state.pageRows.map((r) => r.releaseId)),
    [state.pageRows],
  );
  const selectedOnPage = useMemo(
    () => state.pageRows.filter((r) => selected.has(r.releaseId)),
    [state.pageRows, selected],
  );
  const allOnPageSelected =
    state.pageRows.length > 0 &&
    selectedOnPage.length === state.pageRows.length;
  const someOnPageSelected = selectedOnPage.length > 0 && !allOnPageSelected;

  const togglePageSelection = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) {
        for (const id of visibleIds) next.delete(id);
      } else {
        for (const id of visibleIds) next.add(id);
      }
      return next;
    });
  };

  const toggleRow = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleDeleteSelected = () => {
    const targets = rows.filter((r) => selected.has(r.releaseId));
    if (targets.length === 0) return;
    onDeleteMany(targets);
    setSelected(new Set());
  };

  const handleDeleteAllMatching = () => {
    if (state.filteredCount === 0) return;
    onDeleteMany(state.filtered);
    setSelected(new Set());
  };

  const filterActive = state.query.trim().length > 0;
  const totalLabel = filterActive
    ? `${state.filteredCount} of ${rows.length}`
    : `${rows.length}`;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <StatsRow
        count={rows.length}
        distinctPlugins={distinctPlugins}
        filterActive={filterActive}
        filteredCount={state.filteredCount}
      />

      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          gap: 1.5,
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <TextField
          value={state.query}
          onChange={(e) => state.setQuery(e.target.value)}
          placeholder="Filter by plugin, version or key…"
          size="small"
          sx={{ minWidth: 280, flex: "1 1 280px", maxWidth: 480 }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Search size={14} />
                </InputAdornment>
              ),
            },
          }}
        />
        <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
          {selected.size > 0 && (
            <Chip
              label={`${selected.size} selected`}
              size="small"
              onDelete={() => setSelected(new Set())}
              sx={{ fontWeight: 500 }}
            />
          )}
          {selected.size > 0 && (
            <Button
              variant="outlined"
              color="error"
              size="small"
              startIcon={<Trash2 size={14} />}
              onClick={handleDeleteSelected}
              disabled={busy}
            >
              Remove {selected.size} selected
            </Button>
          )}
          <Button
            variant="contained"
            color="error"
            size="small"
            startIcon={<Trash2 size={14} />}
            disabled={state.filteredCount === 0 || busy}
            onClick={handleDeleteAllMatching}
          >
            {filterActive
              ? `Remove all ${state.filteredCount} matching`
              : `Remove all (${rows.length})`}
          </Button>
        </Box>
      </Box>

      {rows.length === 0 ? (
        <EmptyState
          icon={<FileX size={22} />}
          title="No missing artifacts"
          description="Every plugin release points at a storage object that exists."
        />
      ) : state.filteredCount === 0 ? (
        <EmptyState
          icon={<Search size={22} />}
          title="No matches"
          description="No release matches the current filter. Clear it to see all rows."
        />
      ) : (
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
            aria-label="Missing artifacts"
            sx={{ width: "100%", borderCollapse: "collapse" }}
          >
            <Box component="thead">
              <Box component="tr">
                <HeaderCell width={44} align="center">
                  <Checkbox
                    size="small"
                    checked={allOnPageSelected}
                    indeterminate={someOnPageSelected}
                    onChange={togglePageSelection}
                    slotProps={{
                      input: { "aria-label": "Select all rows on this page" },
                    }}
                  />
                </HeaderCell>
                <SortHeader
                  label="Plugin"
                  active={state.sortKey === "plugin"}
                  direction={state.sortDirection}
                  onClick={() => state.toggleSort("plugin")}
                />
                <SortHeader
                  label="Version"
                  width={140}
                  active={state.sortKey === "version"}
                  direction={state.sortDirection}
                  onClick={() => state.toggleSort("version")}
                />
                <SortHeader
                  label="Artifact key"
                  active={state.sortKey === "key"}
                  direction={state.sortDirection}
                  onClick={() => state.toggleSort("key")}
                />
                <HeaderCell width={140} />
              </Box>
            </Box>
            <Box component="tbody">
              {state.pageRows.map((row) => {
                const isSelected = selected.has(row.releaseId);
                return (
                  <Box
                    component="tr"
                    key={row.releaseId}
                    sx={{
                      bgcolor: isSelected
                        ? "action.selected"
                        : "background.paper",
                      transition: "background-color 0.15s",
                      "&:hover": {
                        bgcolor: isSelected
                          ? "action.selected"
                          : "background.default",
                      },
                    }}
                  >
                    <BodyCell align="center">
                      <Checkbox
                        size="small"
                        checked={isSelected}
                        onChange={() => toggleRow(row.releaseId)}
                        slotProps={{
                          input: {
                            "aria-label": `Select ${row.pluginId} ${row.version}`,
                          },
                        }}
                      />
                    </BodyCell>
                    <BodyCell>
                      <Typography
                        variant="body2"
                        sx={{ fontWeight: 600, fontFamily: "monospace" }}
                      >
                        {row.pluginId}
                      </Typography>
                    </BodyCell>
                    <BodyCell>
                      <Chip
                        label={row.version}
                        size="small"
                        sx={{
                          fontFamily: "monospace",
                          bgcolor: tokens.badge.version.bg,
                          color: tokens.badge.version.text,
                          height: 20,
                        }}
                      />
                    </BodyCell>
                    <BodyCell>
                      <Typography
                        variant="body2"
                        sx={{
                          fontFamily: "monospace",
                          fontSize: "0.75rem",
                          color: "text.secondary",
                          wordBreak: "break-all",
                        }}
                      >
                        {row.artifactKey}
                      </Typography>
                    </BodyCell>
                    <BodyCell align="right">
                      <Button
                        size="small"
                        color="error"
                        startIcon={<Trash2 size={12} />}
                        onClick={() => onDeleteOne(row)}
                        sx={{ minWidth: 0 }}
                      >
                        Remove
                      </Button>
                    </BodyCell>
                  </Box>
                );
              })}
            </Box>
          </Box>

          <TablePagination
            component="div"
            count={state.filteredCount}
            page={state.page}
            onPageChange={(_e, p) => state.setPage(p)}
            rowsPerPage={state.pageSize}
            onRowsPerPageChange={(e) =>
              state.setPageSize(parseInt(e.target.value, 10))
            }
            rowsPerPageOptions={PAGE_SIZE_OPTIONS}
            labelRowsPerPage="Rows per page"
            sx={{
              borderTop: "1px solid",
              borderColor: "divider",
              bgcolor: "background.default",
            }}
          />
        </Box>
      )}

      <Typography variant="caption" sx={{ color: "text.disabled" }}>
        Showing {totalLabel} row(s) • {distinctPlugins} plugin
        {distinctPlugins === 1 ? "" : "s"} affected
      </Typography>
    </Box>
  );
}

function StatsRow({
  count,
  distinctPlugins,
  filterActive,
  filteredCount,
}: {
  readonly count: number;
  readonly distinctPlugins: number;
  readonly filterActive: boolean;
  readonly filteredCount: number;
}) {
  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr 1fr", sm: "repeat(3, 1fr)" },
        gap: 1.5,
      }}
    >
      <Stat label="Releases missing" value={count.toLocaleString()} accent />
      <Stat label="Plugins affected" value={distinctPlugins.toLocaleString()} />
      <Stat
        label={filterActive ? "Matches filter" : "After filter"}
        value={filterActive ? filteredCount.toLocaleString() : "—"}
      />
    </Box>
  );
}

function Stat({
  label,
  value,
  accent,
}: {
  readonly label: string;
  readonly value: string;
  readonly accent?: boolean;
}) {
  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: accent ? tokens.badge.yanked.text : "divider",
        borderRadius: tokens.radius.card,
        px: 2,
        py: 1.25,
        bgcolor: accent ? tokens.badge.yanked.bg : "background.paper",
      }}
    >
      <Typography
        variant="caption"
        sx={{
          color: accent ? tokens.badge.yanked.text : "text.disabled",
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
          color: accent ? tokens.badge.yanked.text : "text.primary",
        }}
      >
        {value}
      </Typography>
    </Box>
  );
}

function HeaderCell({
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

function SortHeader({
  label,
  active,
  direction,
  onClick,
  width,
  align,
}: {
  readonly label: string;
  readonly active: boolean;
  readonly direction: "asc" | "desc";
  readonly onClick: () => void;
  readonly width?: number;
  readonly align?: "left" | "right" | "center";
}) {
  return (
    <HeaderCell width={width} align={align}>
      <Box
        component="button"
        type="button"
        onClick={onClick}
        sx={{
          background: "transparent",
          border: 0,
          padding: 0,
          cursor: "pointer",
          color: active ? "text.primary" : "text.secondary",
          fontSize: "inherit",
          fontWeight: "inherit",
          textTransform: "inherit",
          letterSpacing: "inherit",
          display: "inline-flex",
          alignItems: "center",
          gap: 0.5,
          "&:hover": { color: "text.primary" },
          "&:focus-visible": {
            outline: `2px solid ${tokens.color.primary}`,
            outlineOffset: 2,
          },
        }}
        aria-sort={
          active ? (direction === "asc" ? "ascending" : "descending") : "none"
        }
      >
        {label}
        <Box
          component="span"
          aria-hidden
          sx={{
            display: "inline-block",
            width: 8,
            opacity: active ? 1 : 0.25,
            transition: "transform 0.15s",
            transform:
              active && direction === "desc"
                ? "rotate(180deg)"
                : "rotate(0deg)",
            fontSize: "0.8em",
          }}
        >
          ↑
        </Box>
      </Box>
    </HeaderCell>
  );
}

function BodyCell({
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

function EmptyState({
  icon,
  title,
  description,
}: {
  readonly icon: React.ReactNode;
  readonly title: string;
  readonly description: string;
}) {
  return (
    <Box
      sx={{
        py: 5,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 0.5,
        border: "1px dashed",
        borderColor: "divider",
        borderRadius: tokens.radius.card,
        color: "text.secondary",
      }}
    >
      <Box sx={{ color: "text.disabled" }}>{icon}</Box>
      <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
        {title}
      </Typography>
      <Typography variant="caption" sx={{ color: "text.disabled" }}>
        {description}
      </Typography>
    </Box>
  );
}
