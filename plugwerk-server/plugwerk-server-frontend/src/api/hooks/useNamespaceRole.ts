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
import { isAxiosError } from "axios";
import { namespaceMembersApi } from "../config";
import type { NamespaceMembershipDto } from "../generated/model";
import { useAuthStore } from "../../stores/authStore";

/** TanStack Query key roots (ADR-0028 / #329). */
export const namespaceRoleKeys = {
  all: ["namespace-role"] as const,
  byNamespace: (slug: string) => [...namespaceRoleKeys.all, slug] as const,
} as const;

/**
 * Fetches the current user's membership (role) in the given namespace.
 *
 * Replaces the old `authStore.fetchNamespaceRole` (TS-008 follow-up / #329).
 *
 * Return semantics:
 * - `data === undefined` + `isPending` — first fetch in flight; consumers must
 *   not make gating decisions yet (showing "not admin" would be a
 *   permission-denied flash).
 * - `data === null` — request resolved, user is not a member of this namespace
 *   (backend returned 404). This is a legitimate state, not an error.
 * - `data: { role: … }` — the real membership record.
 * - `isError === true` — a 5xx / network failure. Consumers fall back to the
 *   safest gating default (treat as not-admin) by checking `data?.role ===
 *   "ADMIN"` exactly as before.
 *
 * The 404 → `null` mapping mirrors the old catch-all in `fetchNamespaceRole`
 * which also produced `namespaceRole: null` on any failure. Every existing
 * caller that only checked `role === "ADMIN"` keeps behaving identically.
 *
 * The hook is disabled while `isHydrating` is true (the refresh-cookie call is
 * in flight; firing this now would 401) and while `isAuthenticated` is false
 * (no credential to attach).
 */
export function useNamespaceRole(slug: string | null | undefined) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isHydrating = useAuthStore((s) => s.isHydrating);
  return useQuery<NamespaceMembershipDto | null>({
    // When disabled, the key is unused but must still be stable; fall back to
    // the root so the query hook is happy even with an empty slug.
    queryKey: slug
      ? namespaceRoleKeys.byNamespace(slug)
      : namespaceRoleKeys.all,
    queryFn: async () => {
      try {
        const response = await namespaceMembersApi.getMyMembership({
          ns: slug!,
        });
        return response.data;
      } catch (error) {
        if (isAxiosError(error) && error.response?.status === 404) {
          return null;
        }
        throw error;
      }
    },
    enabled: !!slug && isAuthenticated && !isHydrating,
  });
}
