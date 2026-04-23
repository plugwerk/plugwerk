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
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { axiosInstance, catalogApi } from "../config";
import type { ListPluginsStatusEnum } from "../generated/api/catalog-api";
import type { PluginPagedResponse } from "../generated/model";

/**
 * Filter form state consumed by [usePlugins]. Kept as its own type because the
 * Zustand UI store owns the canonical form (`src/stores/pluginStore.ts`) and
 * passes it straight through to the hook.
 */
export interface PluginFilters {
  search: string;
  tag: string;
  status: string;
  version: string;
  sort: string;
  page: number;
  size: number;
}

/** TanStack Query key roots (ADR-0028 / #328). */
export const pluginsKeys = {
  all: ["plugins"] as const,
  namespace: (ns: string) => [...pluginsKeys.all, "ns", ns] as const,
  list: (ns: string, filters: PluginFilters) =>
    [...pluginsKeys.namespace(ns), "list", filters] as const,
  tags: (ns: string) => [...pluginsKeys.namespace(ns), "tags"] as const,
} as const;

/**
 * Fetches the paginated plugin list for a namespace.
 *
 * Replaces the old `usePluginStore().fetchPlugins()` (TS-007 / #328). The full
 * `filters` object is part of the query key, so every filter combination has
 * its own cache entry and switching back to a previously-visited filter set
 * resolves instantly from cache.
 *
 * `placeholderData: keepPreviousData` keeps the previous page visible during
 * pagination so the grid does not flash back to the skeleton between page
 * jumps. Initial load still shows the skeleton because `data` starts
 * `undefined`.
 *
 * `enabled: !!namespace` prevents a fetch before the router has resolved the
 * `:namespace` param.
 */
export function usePlugins(namespace: string, filters: PluginFilters) {
  return useQuery<PluginPagedResponse>({
    queryKey: pluginsKeys.list(namespace, filters),
    queryFn: async () => {
      const response = await catalogApi.listPlugins({
        ns: namespace,
        page: filters.page,
        size: filters.size,
        sort: filters.sort,
        q: filters.search || undefined,
        tag: filters.tag || undefined,
        status: (filters.status || undefined) as
          | ListPluginsStatusEnum
          | undefined,
        version: filters.version || undefined,
      });
      return response.data;
    },
    enabled: !!namespace,
    placeholderData: keepPreviousData,
  });
}

/**
 * Fetches the tag autocomplete list for a namespace.
 *
 * Tags change rarely so we cache for 5 minutes instead of the default 60s.
 * Errors resolve to an empty array at the consumer via `data ?? []` — matches
 * the old store behaviour that swallowed the failure silently.
 */
export function usePluginTags(namespace: string) {
  return useQuery<string[]>({
    queryKey: pluginsKeys.tags(namespace),
    queryFn: async () => {
      const response = await axiosInstance.get<string[]>(
        `/namespaces/${namespace}/tags`,
      );
      return response.data;
    },
    enabled: !!namespace,
    staleTime: 5 * 60_000,
  });
}

/**
 * Derives the pending-review plugin and release counts from the shared
 * [usePlugins] cache.
 *
 * The backend returns both counts inside the same `listPlugins` response, so
 * a separate endpoint call would be redundant. This hook is a thin selector
 * over [usePlugins] with the current filters — matching the old store which
 * simply exposed whatever the last fetch returned.
 *
 * Filters are read from the UI store directly so callers don't have to pass
 * them in. Callers that mount this hook without also mounting [usePlugins]
 * still get the correct result because both share the same query key.
 */
export function usePluginPendingReviewCount(
  namespace: string,
  filters: PluginFilters,
): { pluginCount: number | null; releaseCount: number | null } {
  const query = usePlugins(namespace, filters);
  return {
    pluginCount: query.data?.pendingReviewPluginCount ?? null,
    releaseCount: query.data?.pendingReviewReleaseCount ?? null,
  };
}
