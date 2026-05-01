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
import { useAuthStore } from "../../stores/authStore";

/**
 * Index route for `/admin` — picks the destination based on the caller's
 * role rather than redirecting everyone to `global-settings`.
 *
 * Issue #411: namespace admins (`isSuperadmin === false`) are not allowed
 * to see operator-level sections. Hard-coding the index redirect to
 * `global-settings` would mean clicking the **Admin** top-bar link as a
 * namespace admin silently lands on a 403 page — exactly the support
 * noise the issue's sidebar fix tried to eliminate.
 *
 * Resolution rule (kept in sync with `AdminSidebar.ADMIN_SECTIONS`):
 *
 *   - Superadmin → `global-settings`. The historical default; preserves
 *     the existing operator workflow for the ~99 % case where superadmin
 *     == the human typing.
 *   - Anyone else → `namespaces`. The first namespace-scoped section in
 *     the sidebar order, and the one that's overwhelmingly relevant for
 *     a namespace admin (their own namespaces' membership, API keys, …).
 *
 * If a future contributor reorders or replaces these defaults, the test
 * file pinned to this component will fail loudly — better than a silent
 * 403 redirect for a real user.
 */
export function AdminIndexRedirect() {
  const isSuperadmin = useAuthStore((s) => s.isSuperadmin);
  return (
    <Navigate to={isSuperadmin ? "global-settings" : "namespaces"} replace />
  );
}
