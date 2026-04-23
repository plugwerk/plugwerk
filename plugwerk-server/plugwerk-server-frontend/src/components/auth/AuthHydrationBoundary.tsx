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
import { useEffect, type ReactNode } from "react";
import { Box, CircularProgress } from "@mui/material";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../../stores/authStore";

interface AuthHydrationBoundaryProps {
  children: ReactNode;
}

/**
 * Module-scoped guard. Ensures `hydrate()` runs at most once per app lifetime,
 * even under React StrictMode double-invoke in dev. Without this, StrictMode
 * would call `/api/v1/auth/refresh` twice in quick succession; the rotating
 * refresh-token protocol (ADR-0027) treats the second call against an already-
 * rotated cookie as a reuse attempt and revokes the entire family — logging
 * the user out on every reload.
 */
let hydrateCalled = false;

/**
 * Blocks rendering of auth-dependent UI until `hydrate()` has resolved (ADR-0027 /
 * #294). The refresh-cookie call on mount is the one place where the access token
 * materialises in memory; downstream API calls would race against it and get 401
 * before the store has a token to attach.
 *
 * Also drops every TanStack Query cache on explicit logout (ADR-0028 / #329) so
 * role/plugin/namespace caches from the previous user do not bleed into the
 * next login on the same browser profile. Subscribes to the auth store
 * imperatively (outside the React render cycle) to catch the transition
 * deterministically — a useEffect-on-deps approach is fragile under
 * StrictMode and batched state updates.
 */
export function AuthHydrationBoundary({
  children,
}: AuthHydrationBoundaryProps) {
  const isHydrating = useAuthStore((s) => s.isHydrating);
  const hydrate = useAuthStore((s) => s.hydrate);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (hydrateCalled) return;
    hydrateCalled = true;
    // Fire-and-forget; the store flips isHydrating to false when settled.
    void hydrate();
  }, [hydrate]);

  useEffect(() => {
    // Clear the entire TanStack cache on authenticated→unauthenticated
    // transitions so the next user on the same browser profile does not see
    // the previous user's cached data. Using `subscribe` rather than a
    // state-dep effect gives us a precise before/after snapshot and avoids
    // false positives from StrictMode / batching.
    return useAuthStore.subscribe((state, prev) => {
      if (
        prev.isAuthenticated &&
        !state.isAuthenticated &&
        !state.isHydrating
      ) {
        queryClient.clear();
      }
    });
  }, [queryClient]);

  if (isHydrating) {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          minHeight: "100vh",
        }}
      >
        <CircularProgress aria-label="Loading session" />
      </Box>
    );
  }
  return <>{children}</>;
}
