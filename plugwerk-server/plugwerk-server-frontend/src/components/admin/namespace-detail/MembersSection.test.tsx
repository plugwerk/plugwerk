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
import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MembersSection } from "./MembersSection";
import { renderWithTheme } from "../../../test/renderWithTheme";
import * as apiConfig from "../../../api/config";
import type { UserDto } from "../../../api/generated/model";

vi.mock("../../../api/config", () => ({
  adminUsersApi: {
    listUsers: vi.fn(),
  },
  namespaceMembersApi: {
    listNamespaceMembers: vi.fn(),
  },
}));

function userDto(overrides: Partial<UserDto>): UserDto {
  return {
    id: crypto.randomUUID(),
    displayName: "Anon",
    source: "INTERNAL",
    email: "anon@example.test",
    enabled: true,
    passwordChangeRequired: false,
    isSuperadmin: false,
    createdAt: new Date().toISOString(),
    namespaceMembershipCount: 0,
    ...overrides,
  } as UserDto;
}

/**
 * Opens the "Add Member" dialog and waits for the user-options effect to
 * fire. Returns both the userEvent instance and the dialog element.
 */
async function openAddMemberDialog() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: /add member/i }));
  const dialog = await screen.findByRole("dialog");
  return { user, dialog };
}

describe("MembersSection — add-member user picker (issue #412)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Existing namespace members irrelevant for the picker assertions; just
    // return an empty list so the "already a member" filter stays out of the
    // way and every mocked user is a candidate.
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({ data: [] } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);
  });

  it("renders EXTERNAL user with provider name in parentheses", async () => {
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: [
        userDto({
          displayName: "Alice Schmidt",
          source: "EXTERNAL",
          username: undefined,
          providerName: "Company Keycloak",
        }),
      ],
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    await user.click(within(dialog).getByRole("combobox", { name: /user/i }));

    // The MUI Autocomplete option list portals out of the dialog tree, so we
    // search the whole document.
    expect(
      await screen.findByText("Alice Schmidt (Company Keycloak)"),
    ).toBeInTheDocument();
  });

  it("renders EXTERNAL user without providerName as bare displayName (defensive null path)", async () => {
    // Data inconsistency edge: an EXTERNAL row whose oidc_identity row is
    // missing by the time the API serialises. The dropdown still renders a
    // valid label rather than "Bob (undefined)".
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: [
        userDto({
          displayName: "Bob Müller",
          source: "EXTERNAL",
          username: undefined,
          providerName: undefined,
        }),
      ],
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();
    await user.click(within(dialog).getByRole("combobox", { name: /user/i }));

    expect(await screen.findByText("Bob Müller")).toBeInTheDocument();
    // Negative assertion — no parenthesised suffix anywhere.
    expect(screen.queryByText(/\(undefined\)/)).not.toBeInTheDocument();
  });

  it("renders INTERNAL user with distinct username as 'displayName (username)' — existing behaviour", async () => {
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: [
        userDto({
          displayName: "Charlie Admin",
          username: "charlie",
          source: "INTERNAL",
        }),
      ],
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();
    await user.click(within(dialog).getByRole("combobox", { name: /user/i }));

    expect(
      await screen.findByText("Charlie Admin (charlie)"),
    ).toBeInTheDocument();
  });

  it("Autocomplete free-text search does NOT match the provider-name suffix", async () => {
    // Issue #412 invariant: the provider hint is decorative. Typing the
    // provider name must not surface every user from that provider.
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: [
        userDto({
          displayName: "Alice Schmidt",
          source: "EXTERNAL",
          username: undefined,
          providerName: "Google",
        }),
        userDto({
          displayName: "Diana Prince",
          source: "INTERNAL",
          username: "diana",
        }),
      ],
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.type(input, "Google");

    // If the filter were leaking the provider suffix, Alice would still
    // appear. The createFilterOptions stringify only sees displayName +
    // username, so "Google" matches nothing.
    await waitFor(() => {
      expect(
        screen.queryByText("Alice Schmidt (Google)"),
      ).not.toBeInTheDocument();
    });
  });
});
