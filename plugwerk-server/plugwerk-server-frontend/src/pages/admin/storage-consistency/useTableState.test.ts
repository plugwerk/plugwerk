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
import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { type SortDefinition, useTableState } from "./useTableState";

interface Row {
  name: string;
  size: number;
}

const ROWS: Row[] = [
  { name: "charlie", size: 30 },
  { name: "alpha", size: 10 },
  { name: "bravo", size: 20 },
  { name: "delta", size: 40 },
];

const SORTS: SortDefinition<Row>[] = [
  {
    key: "name",
    label: "Name",
    compare: (a, b) => a.name.localeCompare(b.name),
    defaultDirection: "asc",
  },
  {
    key: "size",
    label: "Size",
    compare: (a, b) => a.size - b.size,
    defaultDirection: "desc",
  },
];

const searchFields = (row: Row) => [row.name];

function setup(overrides?: {
  sorts?: SortDefinition<Row>[];
  initialSortKey?: string;
  initialPageSize?: number;
}) {
  return renderHook(() =>
    useTableState<Row, string>({
      rows: ROWS,
      searchFields,
      sorts: overrides?.sorts ?? SORTS,
      initialSortKey: overrides?.initialSortKey ?? "name",
      initialPageSize: overrides?.initialPageSize,
    }),
  );
}

describe("useTableState", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("sorts ascending by the initial key by default", () => {
    const { result } = setup();
    expect(result.current.sortDirection).toBe("asc");
    expect(result.current.pageRows.map((r) => r.name)).toEqual([
      "alpha",
      "bravo",
      "charlie",
      "delta",
    ]);
    expect(result.current.filteredCount).toBe(4);
  });

  it("debounces the query, filters, and resets to the first page", () => {
    const { result } = setup({ initialPageSize: 2 });
    act(() => result.current.setPage(1));
    expect(result.current.page).toBe(1);

    act(() => result.current.setQuery("a"));
    // Before the debounce fires nothing has changed yet.
    expect(result.current.filteredCount).toBe(4);
    act(() => vi.advanceTimersByTime(120));

    expect(result.current.page).toBe(0);
    expect(result.current.filtered.map((r) => r.name).sort()).toEqual([
      "alpha",
      "bravo",
      "charlie",
      "delta",
    ]);
  });

  it("narrows results to the matching rows", () => {
    const { result } = setup();
    act(() => result.current.setQuery("lph"));
    act(() => vi.advanceTimersByTime(120));
    expect(result.current.filtered.map((r) => r.name)).toEqual(["alpha"]);
  });

  it("toggles direction when the active sort key is toggled", () => {
    const { result } = setup();
    act(() => result.current.toggleSort("name"));
    expect(result.current.sortDirection).toBe("desc");
    expect(result.current.pageRows.map((r) => r.name)).toEqual([
      "delta",
      "charlie",
      "bravo",
      "alpha",
    ]);
  });

  it("switches to a new sort key with its default direction", () => {
    const { result } = setup();
    act(() => result.current.toggleSort("size"));
    expect(result.current.sortKey).toBe("size");
    expect(result.current.sortDirection).toBe("desc");
    expect(result.current.pageRows.map((r) => r.size)).toEqual([
      40, 30, 20, 10,
    ]);
  });

  it("paginates and resets the page when the page size changes", () => {
    const { result } = setup({ initialPageSize: 2 });
    expect(result.current.pageRows.map((r) => r.name)).toEqual([
      "alpha",
      "bravo",
    ]);
    act(() => result.current.setPage(1));
    expect(result.current.pageRows.map((r) => r.name)).toEqual([
      "charlie",
      "delta",
    ]);
    act(() => result.current.setPageSize(3));
    expect(result.current.page).toBe(0);
    expect(result.current.pageRows).toHaveLength(3);
  });

  it("honours setSortDirection and setSortKey setters (with page reset)", () => {
    const { result } = setup({ initialPageSize: 2 });
    act(() => result.current.setPage(1));
    act(() => result.current.setSortDirection("desc"));
    expect(result.current.page).toBe(0);
    expect(result.current.sortDirection).toBe("desc");
    act(() => result.current.setPage(1));
    act(() => result.current.setSortKey("size"));
    expect(result.current.page).toBe(0);
    expect(result.current.sortKey).toBe("size");
  });

  it("falls back to the first sort when the active key is unknown", () => {
    const { result } = setup({ initialSortKey: "ghost" });
    // initialDir resolves to 'asc' (no matching sort), and sorted falls back
    // to sorts[0] (name asc).
    expect(result.current.sortDirection).toBe("asc");
    expect(result.current.pageRows.map((r) => r.name)).toEqual([
      "alpha",
      "bravo",
      "charlie",
      "delta",
    ]);
  });

  it("returns rows unsorted when no sort definitions are supplied", () => {
    const { result } = setup({ sorts: [], initialSortKey: "name" });
    expect(result.current.pageRows.map((r) => r.name)).toEqual([
      "charlie",
      "alpha",
      "bravo",
      "delta",
    ]);
  });
});
