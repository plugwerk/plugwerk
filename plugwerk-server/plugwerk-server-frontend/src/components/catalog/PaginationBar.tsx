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
import { Box, MenuItem, Pagination, Typography } from "@mui/material";
import { usePluginStore } from "../../stores/pluginStore";
import { FilterSelect } from "../common/FilterSelect";

interface PaginationBarProps {
  namespace: string;
}

const PAGE_SIZES = [12, 24, 48];

export function PaginationBar({ namespace }: PaginationBarProps) {
  const { filters, totalElements, totalPages, setFilters, fetchPlugins } =
    usePluginStore();

  function handlePageChange(_: unknown, page: number) {
    setFilters({ page: page - 1 });
    fetchPlugins(namespace);
  }

  function handleSizeChange(value: string) {
    setFilters({ size: Number(value), page: 0 });
    fetchPlugins(namespace);
  }

  const start = filters.page * filters.size + 1;
  const end = Math.min(start + filters.size - 1, totalElements);

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: 2,
        mt: 4,
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
        <Typography variant="caption" color="text.primary">
          Show:
        </Typography>
        <FilterSelect
          value={filters.size}
          onChange={handleSizeChange}
          aria-label="Items per page"
          minWidth={70}
        >
          {PAGE_SIZES.map((s) => (
            <MenuItem key={s} value={s}>
              {s}
            </MenuItem>
          ))}
        </FilterSelect>
      </Box>

      <Pagination
        count={totalPages}
        page={filters.page + 1}
        onChange={handlePageChange}
        size="small"
        color="primary"
        aria-label="Pagination"
      />

      <Typography variant="caption" color="text.primary">
        Showing {start}–{end} of {totalElements}
      </Typography>
    </Box>
  );
}
