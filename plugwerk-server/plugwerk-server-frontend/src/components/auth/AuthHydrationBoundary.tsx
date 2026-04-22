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
import { useAuthStore } from "../../stores/authStore";

interface AuthHydrationBoundaryProps {
  children: ReactNode;
}

/**
 * Blocks rendering of auth-dependent UI until `hydrate()` has resolved (ADR-0027 /
 * #294). The refresh-cookie call on mount is the one place where the access token
 * materialises in memory; downstream API calls would race against it and get 401
 * before the store has a token to attach.
 */
export function AuthHydrationBoundary({
  children,
}: AuthHydrationBoundaryProps) {
  const isHydrating = useAuthStore((s) => s.isHydrating);
  const hydrate = useAuthStore((s) => s.hydrate);

  useEffect(() => {
    // Fire-and-forget; the store flips isHydrating to false when settled.
    void hydrate();
  }, [hydrate]);

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
