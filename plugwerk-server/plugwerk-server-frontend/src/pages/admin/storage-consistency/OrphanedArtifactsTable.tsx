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
  Tooltip,
  Typography,
} from "@mui/material";
import { Database, Search, Trash2 } from "lucide-react";
import type { OrphanedArtifact } from "../../../api/generated/model";
import { tokens } from "../../../theme/tokens";
import { Timestamp } from "../../../components/common/Timestamp";
import { useTableState } from "./useTableState";
import type { SortDefinition } from "./useTableState";
import { ageBucket, formatBytes } from "./format";

type SortKey = "key" | "size" | "age";

const SORTS: SortDefinition<OrphanedArtifact, SortKey>[] = [
  {
    key: "key",
    label: "Key",
    compare: (a, b) => a.key.localeCompare(b.key),
  },
  {
    key: "size",
    label: "Size",
    compare: (a, b) => a.sizeBytes - b.sizeBytes,
    defaultDirection: "desc",
  },
  {
    key: "age",
    label: "Age",
    compare: (a, b) => a.ageHours - b.ageHours,
    defaultDirection: "desc",
  },
];

const PAGE_SIZE_OPTIONS = [25, 50, 100];

interface OrphanedArtifactsTableProps {
  readonly rows: readonly OrphanedArtifact[];
  readonly busy: boolean;
  readonly onDeleteMany: (rows: readonly OrphanedArtifact[]) => void;
}

/**
 * Triage view for storage objects with no `plugin_release` row. The bulk
 * of admin remediation happens here — a publish-flow that crashed after
 * upload leaves dozens of orphans, an S3 lifecycle misfire leaves
 * hundreds. Sort by size (default desc) surfaces the worst space-wasters
 * first; age filter helps verify that fresh orphans aren't in-flight
 * uploads (the reaper grace period from #496 catches that on the schedule
 * side, but admins sometimes need to clean up older drift manually).
 */
export function OrphanedArtifactsTable({
  rows,
  busy,
  onDeleteMany,
}: OrphanedArtifactsTableProps) {
  const state = useTableState<OrphanedArtifact, SortKey>({
    rows,
    searchFields: (r) => [r.key],
    sorts: SORTS,
    initialSortKey: "size",
  });

  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());

  const totalSize = useMemo(
    () => rows.reduce((sum, r) => sum + r.sizeBytes, 0),
    [rows],
  );
  const filteredSize = useMemo(
    () => state.filtered.reduce((sum, r) => sum + r.sizeBytes, 0),
    [state.filtered],
  );
  const oldestHours = useMemo(
    () => (rows.length === 0 ? 0 : Math.max(...rows.map((r) => r.ageHours))),
    [rows],
  );

  const visibleIds = useMemo(
    () => new Set(state.pageRows.map((r) => r.key)),
    [state.pageRows],
  );
  const selectedOnPage = useMemo(
    () => state.pageRows.filter((r) => selected.has(r.key)),
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
    const targets = rows.filter((r) => selected.has(r.key));
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
  const selectedSize = useMemo(
    () =>
      rows
        .filter((r) => selected.has(r.key))
        .reduce((sum, r) => sum + r.sizeBytes, 0),
    [rows, selected],
  );

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <StatsRow
        count={rows.length}
        totalSize={totalSize}
        oldestHours={oldestHours}
        filterActive={filterActive}
        filteredCount={state.filteredCount}
        filteredSize={filteredSize}
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
          placeholder="Filter by key…"
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
              label={`${selected.size} selected • ${formatBytes(selectedSize)}`}
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
              Delete {selected.size} selected
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
              ? `Delete all ${state.filteredCount} matching`
              : `Delete all (${rows.length})`}
          </Button>
        </Box>
      </Box>

      {rows.length === 0 ? (
        <EmptyState
          icon={<Database size={22} />}
          title="No orphaned objects"
          description="Every storage object has a matching plugin_release row."
        />
      ) : state.filteredCount === 0 ? (
        <EmptyState
          icon={<Search size={22} />}
          title="No matches"
          description="No orphan matches the current filter. Clear it to see all rows."
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
            aria-label="Orphaned artifacts"
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
                  label="Storage key"
                  active={state.sortKey === "key"}
                  direction={state.sortDirection}
                  onClick={() => state.toggleSort("key")}
                />
                <SortHeader
                  label="Size"
                  width={120}
                  align="right"
                  active={state.sortKey === "size"}
                  direction={state.sortDirection}
                  onClick={() => state.toggleSort("size")}
                />
                <SortHeader
                  label="Age"
                  width={160}
                  align="right"
                  active={state.sortKey === "age"}
                  direction={state.sortDirection}
                  onClick={() => state.toggleSort("age")}
                />
              </Box>
            </Box>
            <Box component="tbody">
              {state.pageRows.map((row) => {
                const isSelected = selected.has(row.key);
                return (
                  <Box
                    component="tr"
                    key={row.key}
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
                        onChange={() => toggleRow(row.key)}
                        slotProps={{
                          input: { "aria-label": `Select ${row.key}` },
                        }}
                      />
                    </BodyCell>
                    <BodyCell>
                      <Typography
                        variant="body2"
                        sx={{
                          fontFamily: "monospace",
                          fontSize: "0.78rem",
                          wordBreak: "break-all",
                        }}
                      >
                        {row.key}
                      </Typography>
                    </BodyCell>
                    <BodyCell align="right">
                      <Typography
                        variant="body2"
                        sx={{
                          fontFeatureSettings: '"tnum"',
                          color: "text.secondary",
                        }}
                      >
                        {formatBytes(row.sizeBytes)}
                      </Typography>
                    </BodyCell>
                    <BodyCell align="right">
                      <AgeBadge hours={row.ageHours} date={row.lastModified} />
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
        {filterActive
          ? `${state.filteredCount} match(es) • ${formatBytes(filteredSize)}`
          : `${rows.length} orphan(s) • ${formatBytes(totalSize)}`}
      </Typography>
    </Box>
  );
}

function StatsRow({
  count,
  totalSize,
  oldestHours,
  filterActive,
  filteredCount,
  filteredSize,
}: {
  readonly count: number;
  readonly totalSize: number;
  readonly oldestHours: number;
  readonly filterActive: boolean;
  readonly filteredCount: number;
  readonly filteredSize: number;
}) {
  const oldestLabel =
    count === 0
      ? "—"
      : oldestHours < 24
        ? `${oldestHours}h`
        : oldestHours < 24 * 30
          ? `${Math.floor(oldestHours / 24)}d`
          : `${Math.floor(oldestHours / (24 * 30))}mo`;

  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr 1fr", sm: "repeat(4, 1fr)" },
        gap: 1.5,
      }}
    >
      <Stat label="Orphans" value={count.toLocaleString()} accent />
      <Stat label="Reclaimable" value={formatBytes(totalSize)} />
      <Stat label="Oldest" value={oldestLabel} />
      <Stat
        label={filterActive ? "Match size" : "Match"}
        value={
          filterActive
            ? `${filteredCount.toLocaleString()} • ${formatBytes(filteredSize)}`
            : "—"
        }
      />
    </Box>
  );
}

function AgeBadge({
  hours,
  date,
}: {
  readonly hours: number;
  readonly date: string;
}) {
  const bucket = ageBucket(hours);
  const color =
    bucket === "fresh"
      ? tokens.badge.draft
      : bucket === "day"
        ? tokens.badge.deprecated
        : bucket === "week"
          ? tokens.badge.version
          : tokens.badge.tag;
  const label =
    hours < 24
      ? `${hours}h`
      : hours < 24 * 30
        ? `${Math.floor(hours / 24)}d`
        : `${Math.floor(hours / (24 * 30))}mo`;
  return (
    <Tooltip
      arrow
      placement="left"
      title={<Timestamp date={date} variant="full" />}
    >
      <Chip
        label={label}
        size="small"
        sx={{
          bgcolor: color.bg,
          color: color.text,
          fontWeight: 600,
          fontFeatureSettings: '"tnum"',
          height: 20,
          minWidth: 48,
        }}
      />
    </Tooltip>
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
        borderColor: accent ? tokens.color.primary : "divider",
        borderRadius: tokens.radius.card,
        px: 2,
        py: 1.25,
        bgcolor: accent ? tokens.color.primaryLight : "background.paper",
      }}
    >
      <Typography
        variant="caption"
        sx={{
          color: accent ? tokens.color.primaryDark : "text.disabled",
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
          color: accent ? tokens.color.primaryDark : "text.primary",
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
