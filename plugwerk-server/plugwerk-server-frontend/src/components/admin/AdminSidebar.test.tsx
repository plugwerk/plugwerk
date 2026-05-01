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
import { screen } from "@testing-library/react";
import { AdminSidebar } from "./AdminSidebar";
import { renderWithRouter } from "../../test/renderWithTheme";
import { useAuthStore } from "../../stores/authStore";

const SUPERADMIN_ONLY_LABELS = [
  /general/i,
  /^users$/i,
  /oidc providers/i,
] as const;

const ALWAYS_VISIBLE_LABELS = [/namespaces/i, /pending reviews/i] as const;

describe("AdminSidebar — superadmin-only entry filter (issue #411)", () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: "tok",
      username: "alice",
      isAuthenticated: true,
      namespace: "acme",
    });
  });

  it("renders all five entries for a superadmin", () => {
    useAuthStore.setState({ isSuperadmin: true });
    renderWithRouter(<AdminSidebar />);

    for (const label of [...ALWAYS_VISIBLE_LABELS, ...SUPERADMIN_ONLY_LABELS]) {
      expect(screen.getByRole("link", { name: label })).toBeInTheDocument();
    }
  });

  it("renders only namespace-scoped entries for a non-superadmin (namespace admin)", () => {
    useAuthStore.setState({ isSuperadmin: false });
    renderWithRouter(<AdminSidebar />);

    for (const label of ALWAYS_VISIBLE_LABELS) {
      expect(screen.getByRole("link", { name: label })).toBeInTheDocument();
    }
    for (const label of SUPERADMIN_ONLY_LABELS) {
      expect(
        screen.queryByRole("link", { name: label }),
      ).not.toBeInTheDocument();
    }
  });

  it("hidden entries are absent from the DOM (not just visually hidden)", () => {
    // Guards against the trap of hiding entries via `display: none` or
    // `visibility: hidden`, which would still leak them via the
    // accessibility tree, tab-stops, and screen-readers — defeating both
    // the UX and the security-hygiene purpose of the filter.
    useAuthStore.setState({ isSuperadmin: false });
    renderWithRouter(<AdminSidebar />);

    expect(screen.queryByRole("link", { name: /general/i })).toBeNull();
    expect(screen.queryByRole("link", { name: /^users$/i })).toBeNull();
    expect(screen.queryByRole("link", { name: /oidc providers/i })).toBeNull();
    // The href targets must also be absent — not even orphaned anchor
    // tags pointing at superadmin-only routes.
    expect(document.querySelector('a[href="/admin/users"]')).toBeNull();
    expect(
      document.querySelector('a[href="/admin/oidc-providers"]'),
    ).toBeNull();
    expect(
      document.querySelector('a[href="/admin/global-settings"]'),
    ).toBeNull();
  });

  it("non-superadmin sees exactly two entries — regression guard for new sections", () => {
    // If a future contributor adds a new operator-level entry to
    // `ADMIN_SECTIONS` without setting `requiresSuperadmin: true`, this
    // count breaks and the test fails immediately. The KDoc on
    // `ADMIN_SECTIONS` points at this behaviour.
    useAuthStore.setState({ isSuperadmin: false });
    renderWithRouter(<AdminSidebar />);

    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(ALWAYS_VISIBLE_LABELS.length);
  });
});
