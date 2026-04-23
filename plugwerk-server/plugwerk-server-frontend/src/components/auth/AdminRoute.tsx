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
import { Navigate } from "react-router-dom";
import type { ReactNode } from "react";
import { Box, CircularProgress } from "@mui/material";
import { useAuthStore } from "../../stores/authStore";
import { useNamespaceRole } from "../../api/hooks/useNamespaceRole";

interface AdminRouteProps {
  children: ReactNode;
}

/**
 * Route guard that restricts access to admin pages.
 *
 * Access is granted when the user is either:
 * - a system superadmin, or
 * - an ADMIN of the currently selected namespace.
 *
 * All other users are redirected to the 403 Forbidden page.
 */
export function AdminRoute({ children }: AdminRouteProps) {
  const isSuperadmin = useAuthStore((s) => s.isSuperadmin);
  const namespace = useAuthStore((s) => s.namespace);
  const { data: membership, isLoading } = useNamespaceRole(namespace);

  if (isSuperadmin) {
    return <>{children}</>;
  }

  // Wait for the role query to settle before gating — redirecting while the
  // query is still in flight would kick legitimate admins to /403 on a deep
  // link. The query runs in parallel with the ProtectedRoute render above, so
  // in the normal case this path is very short.
  if (isLoading) {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          minHeight: "50vh",
        }}
      >
        <CircularProgress aria-label="Loading permissions" />
      </Box>
    );
  }

  if (membership?.role !== "ADMIN") {
    return <Navigate to="/403" replace />;
  }

  return <>{children}</>;
}
