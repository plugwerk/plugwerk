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
import { useQuery } from "@tanstack/react-query";
import { namespacesApi } from "../config";
import type { NamespaceSummary } from "../generated/model";

/** TanStack Query key roots (ADR-0028 / #276). */
export const namespacesKeys = {
  all: ["namespaces"] as const,
  list: () => [...namespacesKeys.all, "list"] as const,
} as const;

/**
 * Fetches the full list of namespaces visible to the current principal.
 *
 * Replaces the old `useNamespaceStore().fetchNamespaces()` (TS-008 / #276).
 * Shared cache — multiple consumers hit the API once per `staleTime` window.
 */
export function useNamespaces() {
  return useQuery<NamespaceSummary[]>({
    queryKey: namespacesKeys.list(),
    queryFn: async () => {
      const response = await namespacesApi.listNamespaces();
      return response.data;
    },
  });
}

/**
 * Looks up a single namespace by slug (TS-009 / #276).
 *
 * Backed by the same shared list-cache from [useNamespaces] — when the list has
 * already been fetched by any other consumer, this hook resolves synchronously
 * from cache and no network round-trip happens. When the list has not yet been
 * fetched, this hook triggers the fetch and all other in-flight `useNamespace`
 * callers dedupe on the same request.
 *
 * Returns the namespace, `undefined` if the slug is not found in the list
 * (legitimate render-time state for a 404 page), plus the usual TanStack
 * `isLoading` / `error` indicators.
 */
export function useNamespace(slug: string | null | undefined) {
  const query = useNamespaces();
  const found = slug ? query.data?.find((ns) => ns.slug === slug) : undefined;
  return {
    namespace: found,
    isLoading: query.isLoading,
    error: query.error,
  };
}
