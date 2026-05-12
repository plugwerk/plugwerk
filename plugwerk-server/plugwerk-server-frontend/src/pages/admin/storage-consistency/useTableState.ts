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

export type SortDirection = "asc" | "desc";

export interface SortDefinition<T, K extends string = string> {
  readonly key: K;
  readonly label: string;
  readonly compare: (a: T, b: T) => number;
  readonly defaultDirection?: SortDirection;
}

interface UseTableStateOptions<T, K extends string> {
  readonly rows: readonly T[];
  readonly searchFields: (row: T) => readonly string[];
  readonly sorts: readonly SortDefinition<T, K>[];
  readonly initialSortKey: K;
  readonly initialPageSize?: number;
}

export interface UseTableStateResult<T, K extends string> {
  readonly query: string;
  readonly setQuery: (q: string) => void;
  readonly sortKey: K;
  readonly setSortKey: (k: K) => void;
  readonly sortDirection: SortDirection;
  readonly setSortDirection: (d: SortDirection) => void;
  readonly toggleSort: (k: K) => void;
  readonly page: number;
  readonly setPage: (p: number) => void;
  readonly pageSize: number;
  readonly setPageSize: (s: number) => void;

  /** Rows after filtering — full set, used for "remove all matching" actions. */
  readonly filtered: readonly T[];
  /** Rows for the current page after filtering + sorting. */
  readonly pageRows: readonly T[];
  /** Total filtered count (for pagination footer + bulk-action labels). */
  readonly filteredCount: number;
}

/**
 * Client-side filter + sort + paginate pipeline for the storage-consistency
 * tables. Works on the in-memory scan output — the consistency endpoint
 * already returns the full report and we deliberately keep slicing on the
 * client so admins can switch sort/filter without re-scanning a multi-million-
 * object bucket.
 *
 * The search query is debounced lightly (120ms) to keep typing snappy on a
 * 10k-row list. Page resets to 0 whenever the filter or sort changes so the
 * user is not stranded on an empty page.
 */
export function useTableState<T, K extends string>(
  options: UseTableStateOptions<T, K>,
): UseTableStateResult<T, K> {
  const {
    rows,
    searchFields,
    sorts,
    initialSortKey,
    initialPageSize = 25,
  } = options;

  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [sortKey, setSortKey] = useState<K>(initialSortKey);
  const initialDir =
    sorts.find((s) => s.key === initialSortKey)?.defaultDirection ?? "asc";
  const [sortDirection, setSortDirection] = useState<SortDirection>(initialDir);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(initialPageSize);

  useEffect(() => {
    const id = window.setTimeout(() => {
      setDebouncedQuery(query);
      setPage(0);
    }, 120);
    return () => window.clearTimeout(id);
  }, [query]);

  // Reset page when sort or page-size changes via the setters below; we
  // wrap the bare setters so the reset happens in the event handler
  // rather than in a setState-in-effect.
  const setSortKeyWithReset = (k: K) => {
    setSortKey(k);
    setPage(0);
  };
  const setSortDirectionWithReset = (d: SortDirection) => {
    setSortDirection(d);
    setPage(0);
  };
  const setPageSizeWithReset = (s: number) => {
    setPageSize(s);
    setPage(0);
  };

  const filtered = useMemo<readonly T[]>(() => {
    const q = debouncedQuery.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((row) =>
      searchFields(row).some((field) => field.toLowerCase().includes(q)),
    );
  }, [rows, debouncedQuery, searchFields]);

  const sorted = useMemo<readonly T[]>(() => {
    const sort = sorts.find((s) => s.key === sortKey) ?? sorts[0];
    if (!sort) return filtered;
    const copy = [...filtered];
    copy.sort(sort.compare);
    if (sortDirection === "desc") copy.reverse();
    return copy;
  }, [filtered, sorts, sortKey, sortDirection]);

  const pageRows = useMemo<readonly T[]>(() => {
    const start = page * pageSize;
    return sorted.slice(start, start + pageSize);
  }, [sorted, page, pageSize]);

  const toggleSort = (k: K) => {
    if (k === sortKey) {
      setSortDirectionWithReset(sortDirection === "asc" ? "desc" : "asc");
    } else {
      const next = sorts.find((s) => s.key === k);
      setSortKeyWithReset(k);
      setSortDirectionWithReset(next?.defaultDirection ?? "asc");
    }
  };

  return {
    query,
    setQuery,
    sortKey,
    setSortKey: setSortKeyWithReset,
    sortDirection,
    setSortDirection: setSortDirectionWithReset,
    toggleSort,
    page,
    setPage,
    pageSize,
    setPageSize: setPageSizeWithReset,
    filtered,
    pageRows,
    filteredCount: filtered.length,
  };
}
