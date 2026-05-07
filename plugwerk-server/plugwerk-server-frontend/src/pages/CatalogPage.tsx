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
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { Box, Container, Typography, Alert } from "@mui/material";
import { FilterBar } from "../components/catalog/FilterBar";
import { PendingReviewBanner } from "../components/catalog/PendingReviewBanner";
import { PluginCard } from "../components/catalog/PluginCard";
import { PluginListRow } from "../components/catalog/PluginListRow";
import { PluginCardSkeleton } from "../components/catalog/PluginCardSkeleton";
import { PluginListRowSkeleton } from "../components/catalog/PluginListRowSkeleton";
import { PaginationBar } from "../components/catalog/PaginationBar";
import { CatalogDropOverlay } from "../components/upload/CatalogDropOverlay";
import { EmptyState } from "../components/common/EmptyState";
import { usePluginStore } from "../stores/pluginStore";
import { useAuthStore } from "../stores/authStore";
import { useUiStore } from "../stores/uiStore";
import { useNamespaces } from "../api/hooks/useNamespaces";
import { useNamespaceRole } from "../api/hooks/useNamespaceRole";
import { usePlugins } from "../api/hooks/usePlugins";
import { useDebounce } from "../hooks/useDebounce";
import { useUploadFiles } from "../hooks/useUploadFiles";

export function CatalogPage() {
  const { namespace = "" } = useParams<{ namespace: string }>();
  const { setNamespace, isAuthenticated } = useAuthStore();
  const { data: membership } = useNamespaceRole(namespace);
  const namespaceRole = membership?.role ?? null;
  const { filters, setFilters, resetFilters } = usePluginStore();
  const { searchQuery } = useUiStore();
  const debouncedSearch = useDebounce(searchQuery, 350);
  // Prefetch the namespace list so the TopBar dropdown is populated on direct
  // catalog landings. The shared TanStack cache dedupes with any other consumer.
  useNamespaces();
  const { uploadFiles } = useUploadFiles();
  const [view, setView] = useState<"card" | "list">("card");

  // Stabilise the filters object passed to the query hook. Zustand returns a
  // new `filters` reference whenever any field changes, which is exactly what
  // we want — but React renders for unrelated state (drag-over flag etc.)
  // would otherwise also pass a new object and churn the query key.
  const queryFilters = useMemo(
    () => ({ ...filters, search: debouncedSearch }),
    [filters, debouncedSearch],
  );
  const pluginsQuery = usePlugins(namespace, queryFilters);
  const plugins = pluginsQuery.data?.content ?? [];
  const totalElements = pluginsQuery.data?.totalElements ?? 0;
  const totalPages = pluginsQuery.data?.totalPages ?? 0;
  const pendingReviewPluginCount =
    pluginsQuery.data?.pendingReviewPluginCount ?? null;
  const pendingReviewReleaseCount =
    pluginsQuery.data?.pendingReviewReleaseCount ?? null;
  // Only show the skeleton on the *initial* load (no previous data) so
  // pagination does not flash blank between page jumps. Background refetches
  // while previous data is held are handled by `keepPreviousData`.
  const loading = pluginsQuery.isPending;
  const error = pluginsQuery.error
    ? pluginsQuery.error instanceof Error
      ? pluginsQuery.error.message
      : "Failed to load plugins"
    : null;

  // Drag-and-drop state: counter prevents flicker from child element events
  const [isDragOver, setIsDragOver] = useState(false);
  const dragCounter = useRef(0);
  const canUpload = isAuthenticated && !!namespace;

  const handleDragEnter = useCallback(
    (e: React.DragEvent) => {
      if (!canUpload) return;
      e.preventDefault();
      dragCounter.current += 1;
      if (dragCounter.current === 1) setIsDragOver(true);
    },
    [canUpload],
  );

  const handleDragOver = useCallback(
    (e: React.DragEvent) => {
      if (!canUpload) return;
      e.preventDefault();
    },
    [canUpload],
  );

  const handleDragLeave = useCallback(
    (e: React.DragEvent) => {
      if (!canUpload) return;
      e.preventDefault();
      dragCounter.current -= 1;
      if (dragCounter.current === 0) setIsDragOver(false);
    },
    [canUpload],
  );

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      if (!canUpload) return;
      e.preventDefault();
      dragCounter.current = 0;
      setIsDragOver(false);

      const files = Array.from(e.dataTransfer.files);
      if (files.length > 0) {
        uploadFiles(files, namespace);
      }
    },
    [canUpload, namespace, uploadFiles],
  );

  // Zustand actions (`setNamespace`, `setFilters`) are stable references for
  // the store's lifetime, so listing them in deps does not trigger extra runs.
  // ESLint can't prove the stability statically though — exhaustive-deps is
  // happy when they're listed.
  useEffect(() => {
    setNamespace(namespace);
  }, [namespace, setNamespace]);

  // Reset page to 0 when the debounced search changes, matching the previous
  // store behaviour. The query refetches automatically because `queryFilters`
  // changes as part of the same render.
  useEffect(() => {
    setFilters({ search: debouncedSearch, page: 0 });
  }, [debouncedSearch, setFilters]);

  return (
    <Box
      component="main"
      id="main-content"
      onDragEnter={handleDragEnter}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      sx={{ flex: 1, py: 4, position: "relative" }}
    >
      <CatalogDropOverlay visible={isDragOver} />
      <Container maxWidth="xl">
        {/* Page header */}
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            flexWrap: "wrap",
            gap: 2,
            mb: 1,
          }}
        >
          <Typography variant="h1">Plugin Catalog</Typography>
          {isAuthenticated &&
            pendingReviewPluginCount != null &&
            pendingReviewPluginCount > 0 && (
              <PendingReviewBanner
                pluginCount={pendingReviewPluginCount}
                releaseCount={pendingReviewReleaseCount}
                isAdmin={namespaceRole === "ADMIN"}
              />
            )}
          <Box sx={{ flex: 1 }} />
          {!loading && (
            <Typography
              variant="caption"
              aria-live="polite"
              sx={{
                color: "text.primary",
              }}
            >
              {totalElements} plugins
            </Typography>
          )}
        </Box>

        {/* Filters */}
        <FilterBar view={view} onViewChange={setView} namespace={namespace} />

        {/* Error */}
        {error && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {error}
          </Alert>
        )}

        {/* Loading skeleton */}
        {loading && view === "card" && (
          <Box
            aria-label="Loading plugins"
            aria-busy="true"
            sx={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))",
              gap: 2.5,
            }}
          >
            {Array.from({ length: 6 }).map((_, i) => (
              <PluginCardSkeleton key={i} />
            ))}
          </Box>
        )}
        {loading && view === "list" && (
          <Box aria-label="Loading plugins" aria-busy="true">
            {Array.from({ length: 6 }).map((_, i) => (
              <PluginListRowSkeleton key={i} />
            ))}
          </Box>
        )}

        {/* Empty state */}
        {!loading && !error && plugins.length === 0 && (
          <EmptyState
            title="No plugins found"
            message="Try different search terms or reset your filters to see all available plugins."
            actionLabel="Show all plugins"
            onAction={resetFilters}
          />
        )}

        {/* Card view */}
        {!loading && !error && plugins.length > 0 && view === "card" && (
          <Box
            role="list"
            aria-label="Plugin cards"
            sx={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))",
              gap: 2.5,
            }}
          >
            {plugins.map((plugin) => (
              <PluginCard
                key={plugin.id}
                plugin={plugin}
                namespace={namespace}
              />
            ))}
          </Box>
        )}

        {/* List view */}
        {!loading && !error && plugins.length > 0 && view === "list" && (
          <Box
            role="list"
            aria-label="Plugin list"
            sx={{
              display: "flex",
              flexDirection: "column",
              border: "1px solid",
              borderColor: "divider",
              borderRadius: "8px",
              overflow: "hidden",
            }}
          >
            {plugins.map((plugin) => (
              <PluginListRow
                key={plugin.id}
                plugin={plugin}
                namespace={namespace}
              />
            ))}
          </Box>
        )}

        {/* Pagination */}
        {!loading && !error && plugins.length > 0 && (
          <PaginationBar
            totalElements={totalElements}
            totalPages={totalPages}
          />
        )}
      </Container>
    </Box>
  );
}
