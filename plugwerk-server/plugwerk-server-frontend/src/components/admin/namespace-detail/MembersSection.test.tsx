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
    addNamespaceMember: vi.fn(),
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
 * Wraps a list of users into the paged-response shape that listUsers returns
 * after #485. The test bypasses strict typing via the `as unknown as` cast
 * already in use throughout this file, so missing UserPagedResponse fields
 * (none for this test's purposes) do not need to be filled in.
 */
function pagedUsers(users: UserDto[]) {
  return {
    content: users,
    totalElements: users.length,
    page: 0,
    size: users.length,
    totalPages: users.length === 0 ? 0 : 1,
  };
}

/**
 * Opens the "Add Members" dialog and waits for the user-options effect to
 * fire. Returns both the userEvent instance and the dialog element.
 */
async function openAddMemberDialog() {
  const user = userEvent.setup();
  // Match both legacy "Add Member" and current "Add Members" toolbar label.
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
      data: pagedUsers([
        userDto({
          displayName: "Alice Schmidt",
          source: "EXTERNAL",
          username: undefined,
          providerName: "Company Keycloak",
        }),
      ]),
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
      data: pagedUsers([
        userDto({
          displayName: "Bob Müller",
          source: "EXTERNAL",
          username: undefined,
          providerName: undefined,
        }),
      ]),
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
      data: pagedUsers([
        userDto({
          displayName: "Charlie Admin",
          username: "charlie",
          source: "INTERNAL",
        }),
      ]),
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

  it("multi-select: action button reflects the number of selected users and submits one request per user", async () => {
    // The dialog accepts any number of users at once; the operator picks
    // them all, sets one role, hits submit, and we issue N independent
    // adds. The action label updates to advertise the count so the
    // operator sees what is about to happen before clicking.
    const alice = userDto({
      displayName: "Alice Schmidt",
      source: "EXTERNAL",
      username: undefined,
      providerName: "Google",
    });
    const bob = userDto({
      displayName: "Bob Müller",
      source: "INTERNAL",
      username: "bob",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([alice, bob]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    vi.mocked(
      apiConfig.namespaceMembersApi.addNamespaceMember,
    ).mockResolvedValue({
      data: {
        userId: alice.id,
        displayName: alice.displayName,
        username: null,
        role: "MEMBER",
        createdAt: new Date().toISOString(),
      },
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.addNamespaceMember>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    // Pick both users via the option list — the picker keeps the dropdown
    // open after the first pick (`disableCloseOnSelect`) so the second
    // selection is one click away.
    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.click(await screen.findByText("Alice Schmidt (Google)"));
    await user.click(await screen.findByText("Bob Müller (bob)"));

    // Action button reflects the count, in plural.
    const submit = within(dialog).getByRole("button", {
      name: /add 2 members/i,
    });
    await user.click(submit);

    // Two independent server calls — sequential per the implementation,
    // both observed by the mock regardless of order.
    await waitFor(() => {
      expect(
        apiConfig.namespaceMembersApi.addNamespaceMember,
      ).toHaveBeenCalledTimes(2);
    });
    const calls = vi.mocked(apiConfig.namespaceMembersApi.addNamespaceMember)
      .mock.calls;
    const submittedUserIds = calls.map(
      (c) => c[0].namespaceMemberCreateRequest.userId,
    );
    expect(submittedUserIds).toContain(alice.id);
    expect(submittedUserIds).toContain(bob.id);
  });

  it("Autocomplete free-text search does NOT match the provider-name suffix", async () => {
    // Issue #412 invariant: the provider hint is decorative. Typing the
    // provider name must not surface every user from that provider.
    //
    // After #492 search runs server-side, so this is now a contract on
    // the SERVER's behaviour, not the client filter: the request goes out
    // with `q="Google"`, and the server (which only matches against
    // username/displayName/email) returns an empty page. The mock
    // simulates that by branching on `q`.
    const alice = userDto({
      displayName: "Alice Schmidt",
      source: "EXTERNAL",
      username: undefined,
      providerName: "Google",
    });
    const diana = userDto({
      displayName: "Diana Prince",
      source: "INTERNAL",
      username: "diana",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockImplementation((req) => {
      const q = (req as { q?: string } | undefined)?.q;
      // The server only matches username/displayName/email — `Google` lives
      // in `providerName`, not in any searchable column, so the search
      // must come back empty. Without a query, the picker still wants the
      // initial browse list.
      const content = q?.toLowerCase().includes("google") ? [] : [alice, diana];
      return Promise.resolve({
        data: pagedUsers(content),
      }) as unknown as ReturnType<typeof apiConfig.adminUsersApi.listUsers>;
    });

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.type(input, "Google");

    // Server returned empty for "Google" → Alice does not appear in the
    // picker even though her decorative label contains the word.
    await waitFor(() => {
      expect(
        screen.queryByText("Alice Schmidt (Google)"),
      ).not.toBeInTheDocument();
    });
  });
});

describe("MembersSection — members table (issue #412)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // The picker is irrelevant for the table tests — we never open the
    // dialog in this block. Stub a quiet listUsers so the picker effect,
    // which fires only on dialog open, does not race anything.
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
  });

  it("renders provider name as a secondary line under EXTERNAL members", async () => {
    // Two same-named external users from different providers — without the
    // provider hint they collapse into a confusing duplicate row pair.
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({
      data: [
        {
          userId: crypto.randomUUID(),
          displayName: "Alice Schmidt",
          username: null,
          source: "EXTERNAL",
          providerName: "Company Keycloak",
          role: "MEMBER",
          createdAt: new Date().toISOString(),
        },
        {
          userId: crypto.randomUUID(),
          displayName: "Alice Schmidt",
          username: null,
          source: "EXTERNAL",
          providerName: "Google",
          role: "MEMBER",
          createdAt: new Date().toISOString(),
        },
      ],
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);

    // Both members are rendered; the secondary lines disambiguate them.
    expect(await screen.findByText("Company Keycloak")).toBeInTheDocument();
    expect(await screen.findByText("Google")).toBeInTheDocument();
  });

  it("does not render a secondary line for INTERNAL members whose username equals displayName", async () => {
    // Local-account-only deployments where the operator never customised
    // displayName — the existing "no duplicate username" rule survives.
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({
      data: [
        {
          userId: crypto.randomUUID(),
          displayName: "admin",
          username: "admin",
          source: "INTERNAL",
          providerName: null,
          role: "ADMIN",
          createdAt: new Date().toISOString(),
        },
      ],
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);

    expect(await screen.findByText("admin")).toBeInTheDocument();
    // Only the displayName cell carries "admin" — no secondary
    // username-line under it.
    expect(screen.getAllByText("admin")).toHaveLength(1);
  });

  it("renders username as secondary line for INTERNAL members where it differs from displayName", async () => {
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({
      data: [
        {
          userId: crypto.randomUUID(),
          displayName: "Charlie Admin",
          username: "charlie",
          source: "INTERNAL",
          providerName: null,
          role: "MEMBER",
          createdAt: new Date().toISOString(),
        },
      ],
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);

    expect(await screen.findByText("Charlie Admin")).toBeInTheDocument();
    expect(await screen.findByText("charlie")).toBeInTheDocument();
  });
});
