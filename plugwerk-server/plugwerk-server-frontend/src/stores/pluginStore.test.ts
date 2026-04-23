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
import { describe, it, expect, beforeEach } from "vitest";
import { act } from "react";

import { usePluginStore } from "./pluginStore";

const defaultFilters = {
  search: "",
  tag: "",
  status: "",
  version: "",
  sort: "name,asc",
  page: 0,
  size: 24,
};

describe("usePluginStore (UI-only after #328)", () => {
  beforeEach(() => {
    usePluginStore.setState({ filters: { ...defaultFilters } });
  });

  describe("initial state", () => {
    it("starts with default filters", () => {
      expect(usePluginStore.getState().filters).toEqual(defaultFilters);
    });
  });

  describe("setFilters", () => {
    it("merges partial filters", () => {
      act(() => {
        usePluginStore.getState().setFilters({ tag: "analytics" });
      });
      expect(usePluginStore.getState().filters.tag).toBe("analytics");
      expect(usePluginStore.getState().filters.sort).toBe("name,asc");
    });

    it("resets page to 0 when other filters change", () => {
      usePluginStore.setState({ filters: { ...defaultFilters, page: 3 } });
      act(() => {
        usePluginStore.getState().setFilters({ search: "auth" });
      });
      expect(usePluginStore.getState().filters.page).toBe(0);
    });

    it("keeps explicit page value when provided", () => {
      act(() => {
        usePluginStore.getState().setFilters({ page: 2 });
      });
      expect(usePluginStore.getState().filters.page).toBe(2);
    });

    it("can update multiple filters at once", () => {
      act(() => {
        usePluginStore
          .getState()
          .setFilters({ tag: "auth", sort: "downloads,desc" });
      });
      const { filters } = usePluginStore.getState();
      expect(filters.tag).toBe("auth");
      expect(filters.sort).toBe("downloads,desc");
    });
  });

  describe("resetFilters", () => {
    it("resets all filters to defaults", () => {
      usePluginStore.setState({
        filters: {
          search: "foo",
          tag: "baz",
          status: "active",
          version: "",
          sort: "downloads,desc",
          page: 5,
          size: 12,
        },
      });
      act(() => {
        usePluginStore.getState().resetFilters();
      });
      expect(usePluginStore.getState().filters).toEqual(defaultFilters);
    });
  });
});
