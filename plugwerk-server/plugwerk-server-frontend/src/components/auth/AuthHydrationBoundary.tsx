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
import { useEffect, useRef, type ReactNode } from "react";
import { Box, CircularProgress } from "@mui/material";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../../stores/authStore";

interface AuthHydrationBoundaryProps {
  children: ReactNode;
}

/**
 * Blocks rendering of auth-dependent UI until `hydrate()` has resolved (ADR-0027 /
 * #294). The refresh-cookie call on mount is the one place where the access token
 * materialises in memory; downstream API calls would race against it and get 401
 * before the store has a token to attach.
 *
 * Also drops every TanStack Query cache on authenticated→unauthenticated
 * transitions (ADR-0028 / #329). Without this, role/plugin/namespace caches
 * from the previous user would bleed into the next login on the same browser
 * profile. Logging in under a less-privileged account would briefly see the
 * previous user's admin data before the first refetch.
 */
export function AuthHydrationBoundary({
  children,
}: AuthHydrationBoundaryProps) {
  const isHydrating = useAuthStore((s) => s.isHydrating);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const hydrate = useAuthStore((s) => s.hydrate);
  const queryClient = useQueryClient();
  const wasAuthenticated = useRef(false);

  useEffect(() => {
    // Fire-and-forget; the store flips isHydrating to false when settled.
    void hydrate();
  }, [hydrate]);

  useEffect(() => {
    // Only react to transitions *after* hydration has settled; the initial
    // hydration 401 is not a logout event.
    if (isHydrating) return;
    if (wasAuthenticated.current && !isAuthenticated) {
      queryClient.clear();
    }
    wasAuthenticated.current = isAuthenticated;
  }, [isAuthenticated, isHydrating, queryClient]);

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
