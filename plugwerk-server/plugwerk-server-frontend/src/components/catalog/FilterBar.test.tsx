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
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider, CssBaseline } from "@mui/material";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderWithRouter } from "../../test/renderWithTheme";
import { buildTheme } from "../../theme/theme";
import { FilterBar } from "./FilterBar";
import { usePluginStore } from "../../stores/pluginStore";
import { useUiStore } from "../../stores/uiStore";

/**
 * Renders FilterBar under the real dark theme so the search input's
 * `isDark`-conditional styling (border/background/focus) is exercised. Reuses
 * the same provider stack as `renderWithRouter` but swaps in `buildTheme("dark")`
 * — the only way to reach the dark arm of those ternaries since the shared
 * helper is pinned to the light theme.
 */
function renderDark(ui: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={buildTheme("dark")}>
        <CssBaseline />
        <MemoryRouter>{ui}</MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

vi.mock("../../api/config", () => ({
  catalogApi: {
    listPlugins: vi.fn().mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0 },
    }),
  },
  // The tag autocomplete loads its options from `/namespaces/{ns}/tags`.
  // Return a small fixed list so the picker has real selectable options.
  axiosInstance: {
    get: vi
      .fn()
      .mockImplementation((url: string) =>
        url.endsWith("/tags")
          ? Promise.resolve({ data: ["auth", "billing", "search"] })
          : Promise.resolve({ data: [] }),
      ),
  },
  managementApi: {},
  reviewsApi: {},
}));

const defaultFilters = {
  search: "",
  tag: "",
  status: "",
  version: "",
  sort: "name,asc",
  page: 0,
  size: 24,
};

describe("FilterBar", () => {
  beforeEach(() => {
    usePluginStore.setState({ filters: { ...defaultFilters } });
    useUiStore.setState({ searchQuery: "" });
  });

  it("renders tag, status, compatibility and sort selects", () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    expect(screen.getByPlaceholderText(/all tags/i)).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: /filter by status/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: /filter by compatibility/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: /sort order/i }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("combobox")).toHaveLength(4);
  });

  it("does not show reset button when no active filters", () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    expect(
      screen.queryByRole("button", { name: /reset filters/i }),
    ).not.toBeInTheDocument();
  });

  it("shows reset button when tag filter is active", () => {
    usePluginStore.setState({ filters: { ...defaultFilters, tag: "auth" } });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    expect(
      screen.getByRole("button", { name: /reset filters/i }),
    ).toBeInTheDocument();
  });

  it("renders view toggle with card and list buttons", () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    expect(
      screen.getByRole("button", { name: /card view/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /list view/i }),
    ).toBeInTheDocument();
  });

  it("calls onViewChange when list view is selected", async () => {
    const user = userEvent.setup();
    const onViewChange = vi.fn();
    renderWithRouter(
      <FilterBar view="card" onViewChange={onViewChange} namespace="acme" />,
    );
    await user.click(screen.getByRole("button", { name: /list view/i }));
    expect(onViewChange).toHaveBeenCalledWith("list");
  });

  it("calls onViewChange when card view is selected", async () => {
    const user = userEvent.setup();
    const onViewChange = vi.fn();
    renderWithRouter(
      <FilterBar view="list" onViewChange={onViewChange} namespace="acme" />,
    );
    await user.click(screen.getByRole("button", { name: /card view/i }));
    expect(onViewChange).toHaveBeenCalledWith("card");
  });

  it("renders the filter/sort group with accessible label", () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    expect(
      screen.getByRole("group", { name: /filter and sort options/i }),
    ).toBeInTheDocument();
  });

  it("resets filters when reset button is clicked", async () => {
    const user = userEvent.setup();
    const resetFiltersMock = vi.fn();
    const setFiltersMock = vi.fn();
    usePluginStore.setState({
      filters: { ...defaultFilters, tag: "auth" },
      setFilters: setFiltersMock,
      resetFilters: resetFiltersMock,
    });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    await user.click(screen.getByRole("button", { name: /reset filters/i }));
    // FilterBar clears individual fields via setFilters, not resetFilters
    expect(resetFiltersMock).not.toHaveBeenCalled();
    expect(setFiltersMock).toHaveBeenCalled();
  });

  it("shows correct current sort value", () => {
    usePluginStore.setState({
      filters: { ...defaultFilters, sort: "downloadCount,desc" },
    });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    // The selected value is rendered inside the combobox
    expect(screen.getByText("Most Downloads")).toBeInTheDocument();
  });

  it("updates the status filter and resets the page to 0 on change", async () => {
    const user = userEvent.setup();
    const setFiltersMock = vi.fn();
    usePluginStore.setState({
      filters: { ...defaultFilters, page: 3 },
      setFilters: setFiltersMock,
    });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    await user.click(
      screen.getByRole("combobox", { name: /filter by status/i }),
    );
    await user.click(await screen.findByRole("option", { name: "Active" }));
    expect(setFiltersMock).toHaveBeenCalledWith({ status: "active", page: 0 });
  });

  it("updates the compatibility filter on change", async () => {
    const user = userEvent.setup();
    const setFiltersMock = vi.fn();
    usePluginStore.setState({
      filters: { ...defaultFilters },
      setFilters: setFiltersMock,
    });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    await user.click(
      screen.getByRole("combobox", { name: /filter by compatibility/i }),
    );
    await user.click(await screen.findByRole("option", { name: "≥ 2.0.0" }));
    expect(setFiltersMock).toHaveBeenCalledWith({
      version: ">=2.0.0",
      page: 0,
    });
  });

  it("updates the sort order on change", async () => {
    const user = userEvent.setup();
    const setFiltersMock = vi.fn();
    usePluginStore.setState({
      filters: { ...defaultFilters },
      setFilters: setFiltersMock,
    });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    await user.click(screen.getByRole("combobox", { name: /sort order/i }));
    await user.click(await screen.findByRole("option", { name: "Newest" }));
    expect(setFiltersMock).toHaveBeenCalledWith({
      sort: "updatedAt,desc",
      page: 0,
    });
  });

  it("updates the tag filter via the autocomplete", async () => {
    const user = userEvent.setup();
    const setFiltersMock = vi.fn();
    usePluginStore.setState({
      filters: { ...defaultFilters },
      setFilters: setFiltersMock,
    });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    // The tag autocomplete is the only input rendered with the "All Tags"
    // placeholder. Open it, then pick the "auth" option once the tags load.
    const tagInput = screen.getByPlaceholderText(/all tags/i);
    await user.click(tagInput);
    await user.click(await screen.findByRole("option", { name: "auth" }));
    await waitFor(() => {
      expect(setFiltersMock).toHaveBeenCalledWith({ tag: "auth", page: 0 });
    });
  });

  it("clears the tag filter when the autocomplete value is removed", async () => {
    const user = userEvent.setup();
    const setFiltersMock = vi.fn();
    usePluginStore.setState({
      filters: { ...defaultFilters, tag: "auth" },
      setFilters: setFiltersMock,
    });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    // MUI hides the clear indicator until the autocomplete is focused/hovered;
    // focus the input first so the "Clear" button mounts. Clicking it fires
    // onChange(null) → handleChange("tag", "").
    const tagInput = screen.getByPlaceholderText(/all tags/i);
    await user.click(tagInput);
    const clearBtn = await screen.findByRole("button", { name: /clear/i });
    await user.click(clearBtn);
    expect(setFiltersMock).toHaveBeenCalledWith({ tag: "", page: 0 });
  });

  it("writes typed text into the UI store's search query", async () => {
    const user = userEvent.setup();
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    const search = screen.getByRole("textbox", { name: /search plugins/i });
    await user.type(search, "auth");
    expect(useUiStore.getState().searchQuery).toBe("auth");
  });

  it("shows a clear button only when the search query is non-empty and clears it", async () => {
    const user = userEvent.setup();
    useUiStore.setState({ searchQuery: "auth" });
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    const clear = screen.getByRole("button", { name: /clear search/i });
    await user.click(clear);
    expect(useUiStore.getState().searchQuery).toBe("");
  });

  it("does not show the search clear button when the query is empty", () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    expect(
      screen.queryByRole("button", { name: /clear search/i }),
    ).not.toBeInTheDocument();
  });

  it("reflects the controlled view prop on the toggle group", () => {
    renderWithRouter(
      <FilterBar view="list" onViewChange={vi.fn()} namespace="acme" />,
    );
    const group = screen.getByRole("group", {
      name: /filter and sort options/i,
    });
    expect(
      within(group).getByRole("button", { name: /list view/i }),
    ).toHaveAttribute("aria-pressed", "true");
  });

  it("renders the search input under the dark theme (isDark style branch)", () => {
    // Exercises the `isDark ? … : …` ternaries on the search InputBase that
    // are otherwise unreachable under the light-themed shared render helper.
    renderDark(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    );
    expect(
      screen.getByRole("textbox", { name: /search plugins/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole("search")).toBeInTheDocument();
  });
});
