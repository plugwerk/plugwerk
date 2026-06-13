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
import { AxiosError } from "axios";
import { MembersSection } from "./MembersSection";
import { renderWithTheme } from "../../../test/renderWithTheme";
import { useUiStore } from "../../../stores/uiStore";
import * as apiConfig from "../../../api/config";
import type { NamespaceMemberDto, UserDto } from "../../../api/generated/model";

vi.mock("../../../api/config", () => ({
  adminUsersApi: {
    listUsers: vi.fn(),
  },
  namespaceMembersApi: {
    listNamespaceMembers: vi.fn(),
    addNamespaceMember: vi.fn(),
    updateNamespaceMember: vi.fn(),
    removeNamespaceMember: vi.fn(),
  },
}));

function memberDto(
  overrides: Partial<NamespaceMemberDto> = {},
): NamespaceMemberDto {
  return {
    userId: crypto.randomUUID(),
    displayName: "Member One",
    username: null,
    source: "INTERNAL",
    providerName: null,
    role: "MEMBER",
    createdAt: new Date().toISOString(),
    ...overrides,
  } as NamespaceMemberDto;
}

/** Builds an AxiosError carrying the given HTTP status for 409-path tests. */
function axiosErrorWithStatus(status: number): AxiosError {
  const err = new AxiosError("request failed");
  // The component reads `error.response?.status`; only that field matters.
  err.response = { status } as AxiosError["response"];
  return err;
}

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

describe("MembersSection — load states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useUiStore.setState({ toasts: [] });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
  });

  it("shows a loading spinner before the members list resolves", () => {
    // Never-resolving promise keeps the component in the loading branch.
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockReturnValue(new Promise(() => {}) as never);

    renderWithTheme(<MembersSection slug="ns-1" />);

    // CircularProgress exposes role="progressbar".
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.queryByText("No members found.")).not.toBeInTheDocument();
  });

  it("shows the empty-state message when there are no members", async () => {
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({ data: [] } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);

    expect(await screen.findByText("No members found.")).toBeInTheDocument();
  });

  it("falls back to an empty list when the members request fails", async () => {
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockRejectedValue(new Error("boom"));

    renderWithTheme(<MembersSection slug="ns-1" />);

    // The catch sets members to [] and clears loading → empty state.
    expect(await screen.findByText("No members found.")).toBeInTheDocument();
  });
});

describe("MembersSection — role change", () => {
  const member = memberDto({ displayName: "Dora Member", role: "MEMBER" });

  beforeEach(() => {
    vi.clearAllMocks();
    useUiStore.setState({ toasts: [] });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({ data: [member] } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);
  });

  it("PATCHes the member role and updates the row on success", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.namespaceMembersApi.updateNamespaceMember,
    ).mockResolvedValue({
      data: { ...member, role: "ADMIN" },
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.updateNamespaceMember>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    await screen.findByText("Dora Member");

    // The row's role Select is the only "standard"-variant combobox; pick it
    // by its current visible value "Member".
    const roleSelect = screen.getByRole("combobox");
    await user.click(roleSelect);
    await user.click(await screen.findByRole("option", { name: "Admin" }));

    await waitFor(() => {
      expect(
        apiConfig.namespaceMembersApi.updateNamespaceMember,
      ).toHaveBeenCalledWith({
        ns: "ns-1",
        userId: member.userId,
        namespaceMemberUpdateRequest: { role: "ADMIN" },
      });
    });
  });

  it("surfaces an error toast when the role update fails", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.namespaceMembersApi.updateNamespaceMember,
    ).mockRejectedValue(new Error("nope"));

    renderWithTheme(<MembersSection slug="ns-1" />);
    await screen.findByText("Dora Member");

    const roleSelect = screen.getByRole("combobox");
    await user.click(roleSelect);
    await user.click(await screen.findByRole("option", { name: "Read Only" }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some(
            (t) =>
              t.message === 'Failed to update role for "Dora Member".' &&
              t.type === "error",
          ),
      ).toBe(true);
    });
  });
});

describe("MembersSection — remove member", () => {
  const member = memberDto({ displayName: "Evan Member" });

  beforeEach(() => {
    vi.clearAllMocks();
    useUiStore.setState({ toasts: [] });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({ data: [member] } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);
  });

  it("removes the member and emits a success toast", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.namespaceMembersApi.removeNamespaceMember,
    ).mockResolvedValue({} as never);

    renderWithTheme(<MembersSection slug="ns-1" />);
    await screen.findByText("Evan Member");

    await user.click(screen.getByRole("button", { name: /remove member/i }));

    await waitFor(() => {
      expect(
        apiConfig.namespaceMembersApi.removeNamespaceMember,
      ).toHaveBeenCalledWith({ ns: "ns-1", userId: member.userId });
    });
    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some(
            (t) =>
              t.message === 'Member "Evan Member" removed.' &&
              t.type === "success",
          ),
      ).toBe(true);
    });
    // The row disappears once the optimistic filter runs.
    await waitFor(() => {
      expect(screen.queryByText("Evan Member")).not.toBeInTheDocument();
    });
  });

  it("emits an error toast and keeps the row when removal fails", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.namespaceMembersApi.removeNamespaceMember,
    ).mockRejectedValue(new Error("nope"));

    renderWithTheme(<MembersSection slug="ns-1" />);
    await screen.findByText("Evan Member");

    await user.click(screen.getByRole("button", { name: /remove member/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some(
            (t) =>
              t.message === 'Failed to remove member "Evan Member".' &&
              t.type === "error",
          ),
      ).toBe(true);
    });
    // Failure path does not remove the row.
    expect(screen.getByText("Evan Member")).toBeInTheDocument();
  });
});

describe("MembersSection — add member outcomes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useUiStore.setState({ toasts: [] });
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({ data: [] } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);
  });

  it("adds a single user, emits a success toast, and closes the dialog", async () => {
    const fiona = userDto({
      displayName: "Fiona User",
      username: "fiona",
      source: "INTERNAL",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([fiona]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    vi.mocked(
      apiConfig.namespaceMembersApi.addNamespaceMember,
    ).mockResolvedValue({
      data: {
        userId: fiona.id,
        displayName: fiona.displayName,
        username: "fiona",
        role: "MEMBER",
        createdAt: new Date().toISOString(),
      },
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.addNamespaceMember>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.click(await screen.findByText("Fiona User (fiona)"));
    await user.click(
      within(dialog).getByRole("button", { name: /add member/i }),
    );

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some(
            (t) => t.message === 'Member "Fiona User (fiona)" added.',
          ),
      ).toBe(true);
    });
    // Full success closes the dialog.
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("keeps the dialog open and shows a 409 'already a member' banner on failure", async () => {
    const greg = userDto({
      displayName: "Greg User",
      username: "greg",
      source: "INTERNAL",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([greg]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    vi.mocked(
      apiConfig.namespaceMembersApi.addNamespaceMember,
    ).mockRejectedValue(axiosErrorWithStatus(409));

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.click(await screen.findByText("Greg User (greg)"));
    await user.click(
      within(dialog).getByRole("button", { name: /add member/i }),
    );

    // Single-user failure → "<label>: already a member of this namespace."
    expect(
      await screen.findByText(
        /Greg User \(greg\): already a member of this namespace\./i,
      ),
    ).toBeInTheDocument();
    // Dialog stays open so the operator sees the failure in context.
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("aggregates a partial failure across multiple users (success toast + failure banner)", async () => {
    const hank = userDto({
      displayName: "Hank User",
      username: "hank",
      source: "INTERNAL",
    });
    const iris = userDto({
      displayName: "Iris User",
      username: "iris",
      source: "INTERNAL",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([hank, iris]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    // Hank succeeds; Iris fails with a non-409 (generic "failed to add").
    vi.mocked(
      apiConfig.namespaceMembersApi.addNamespaceMember,
    ).mockImplementation((req) => {
      const userId = (
        req as { namespaceMemberCreateRequest: { userId: string } }
      ).namespaceMemberCreateRequest.userId;
      if (userId === hank.id) {
        return Promise.resolve({
          data: {
            userId: hank.id,
            displayName: hank.displayName,
            username: "hank",
            role: "MEMBER",
            createdAt: new Date().toISOString(),
          },
        }) as unknown as ReturnType<
          typeof apiConfig.namespaceMembersApi.addNamespaceMember
        >;
      }
      return Promise.reject(
        new Error("server exploded"),
      ) as unknown as ReturnType<
        typeof apiConfig.namespaceMembersApi.addNamespaceMember
      >;
    });

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.click(await screen.findByText("Hank User (hank)"));
    await user.click(await screen.findByText("Iris User (iris)"));
    await user.click(
      within(dialog).getByRole("button", { name: /add 2 members/i }),
    );

    // One success toast (singular, since only Hank went through).
    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => t.message === 'Member "Hank User (hank)" added.'),
      ).toBe(true);
    });
    // Exactly one of the two adds failed, so the banner uses the singular
    // "<label>: <reason>." form (the "N of M" aggregate form is reserved for
    // two-or-more failures). Iris failed with a non-409 → generic reason.
    expect(
      await screen.findByText("Iris User (iris): failed to add."),
    ).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("uses the aggregate 'N of M' banner when two or more users fail", async () => {
    const paul = userDto({
      displayName: "Paul User",
      username: "paul",
      source: "INTERNAL",
    });
    const quinn = userDto({
      displayName: "Quinn User",
      username: "quinn",
      source: "INTERNAL",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([paul, quinn]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    // Paul → 409 (already a member); Quinn → generic failure. Two failures
    // trigger the aggregate banner branch.
    vi.mocked(
      apiConfig.namespaceMembersApi.addNamespaceMember,
    ).mockImplementation((req) => {
      const userId = (
        req as { namespaceMemberCreateRequest: { userId: string } }
      ).namespaceMemberCreateRequest.userId;
      return Promise.reject(
        userId === paul.id
          ? axiosErrorWithStatus(409)
          : new Error("server exploded"),
      ) as unknown as ReturnType<
        typeof apiConfig.namespaceMembersApi.addNamespaceMember
      >;
    });

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.click(await screen.findByText("Paul User (paul)"));
    await user.click(await screen.findByText("Quinn User (quinn)"));
    await user.click(
      within(dialog).getByRole("button", { name: /add 2 members/i }),
    );

    expect(
      await screen.findByText((content) => {
        const normalized = content.replace(/\s+/g, " ");
        return (
          normalized.includes("2 of 2 users could not be added") &&
          normalized.includes(
            "Paul User (paul) (already a member of this namespace)",
          ) &&
          normalized.includes("Quinn User (quinn) (failed to add)")
        );
      }),
    ).toBeInTheDocument();
    // No success toast — every add failed.
    expect(useUiStore.getState().toasts.some((t) => t.type === "success")).toBe(
      false,
    );
  });

  it("emits a plural success toast when several users are added at once", async () => {
    const jane = userDto({
      displayName: "Jane User",
      username: "jane",
      source: "INTERNAL",
    });
    const kyle = userDto({
      displayName: "Kyle User",
      username: "kyle",
      source: "INTERNAL",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([jane, kyle]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    vi.mocked(
      apiConfig.namespaceMembersApi.addNamespaceMember,
    ).mockImplementation((req) => {
      const userId = (
        req as { namespaceMemberCreateRequest: { userId: string } }
      ).namespaceMemberCreateRequest.userId;
      return Promise.resolve({
        data: {
          userId,
          displayName: userId === jane.id ? "Jane User" : "Kyle User",
          username: null,
          role: "MEMBER",
          createdAt: new Date().toISOString(),
        },
      }) as unknown as ReturnType<
        typeof apiConfig.namespaceMembersApi.addNamespaceMember
      >;
    });

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.click(await screen.findByText("Jane User (jane)"));
    await user.click(await screen.findByText("Kyle User (kyle)"));
    await user.click(
      within(dialog).getByRole("button", { name: /add 2 members/i }),
    );

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => t.message === "2 members added."),
      ).toBe(true);
    });
  });

  it("changes the role select before adding so the new member gets that role", async () => {
    const liam = userDto({
      displayName: "Liam User",
      username: "liam",
      source: "INTERNAL",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([liam]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    vi.mocked(
      apiConfig.namespaceMembersApi.addNamespaceMember,
    ).mockResolvedValue({
      data: {
        userId: liam.id,
        displayName: liam.displayName,
        username: "liam",
        role: "ADMIN",
        createdAt: new Date().toISOString(),
      },
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.addNamespaceMember>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();

    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.click(await screen.findByText("Liam User (liam)"));

    // Open the Role select in the dialog and pick Admin.
    await user.click(within(dialog).getByRole("combobox", { name: /role/i }));
    await user.click(await screen.findByRole("option", { name: "Admin" }));

    await user.click(
      within(dialog).getByRole("button", { name: /add member/i }),
    );

    await waitFor(() => {
      expect(
        apiConfig.namespaceMembersApi.addNamespaceMember,
      ).toHaveBeenCalledWith({
        ns: "ns-1",
        namespaceMemberCreateRequest: { userId: liam.id, role: "ADMIN" },
      });
    });
  });

  it("filters out users who are already members from the picker options", async () => {
    // An existing member must not appear as an add candidate.
    const existing = memberDto({ displayName: "Mona Member" });
    const newUser = userDto({
      id: existing.userId,
      displayName: "Mona Member",
      username: "mona",
      source: "INTERNAL",
    });
    const other = userDto({
      displayName: "Nina User",
      username: "nina",
      source: "INTERNAL",
    });
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({ data: [existing] } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([newUser, other]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    // Wait for the member row so `members` state is populated before opening.
    await screen.findByText("Mona Member");
    const { user, dialog } = await openAddMemberDialog();
    await user.click(within(dialog).getByRole("combobox", { name: /user/i }));

    // The non-member option is offered…
    expect(await screen.findByText("Nina User (nina)")).toBeInTheDocument();
    // …but the already-member option is not in the dropdown list.
    expect(screen.queryByText("Mona Member (mona)")).not.toBeInTheDocument();
  });

  it("empties the picker options when the user search request fails (non-cancel)", async () => {
    // A genuine failure (not an abort) must clear the option list per the
    // catch branch's `if (!isCancel) setUserOptions([])`.
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockRejectedValue(
      new Error("search backend down"),
    );

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();
    await user.click(within(dialog).getByRole("combobox", { name: /user/i }));

    // MUI shows "No options" when the list is empty after a failed load.
    expect(await screen.findByText(/no options/i)).toBeInTheDocument();
  });

  it("keeps the existing options when the search request is cancelled", async () => {
    // A CanceledError must NOT clear the list — the in-flight request was
    // superseded, not failed. We seed a successful first load, then reject the
    // follow-up search with a CanceledError and assert the option survives.
    const ruth = userDto({
      displayName: "Ruth User",
      username: "ruth",
      source: "INTERNAL",
    });
    const cancel = Object.assign(new Error("canceled"), {
      name: "CanceledError",
      code: "ERR_CANCELED",
    });
    let call = 0;
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockImplementation(() => {
      call += 1;
      if (call === 1) {
        return Promise.resolve({
          data: pagedUsers([ruth]),
        }) as unknown as ReturnType<typeof apiConfig.adminUsersApi.listUsers>;
      }
      return Promise.reject(cancel) as unknown as ReturnType<
        typeof apiConfig.adminUsersApi.listUsers
      >;
    });

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();
    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    expect(await screen.findByText("Ruth User (ruth)")).toBeInTheDocument();

    // Type to trigger a second (cancelled) search; the first option survives.
    await user.type(input, "ru");
    // Allow the debounced/cancelled effect to settle.
    await waitFor(() => {
      expect(apiConfig.adminUsersApi.listUsers).toHaveBeenCalledTimes(2);
    });
    expect(screen.getByText("Ruth User (ruth)")).toBeInTheDocument();
  });

  it("updates only the changed member's row when several members exist", async () => {
    // Exercises the `m.userId === member.userId ? res.data : m` mapping's
    // false branch — the untouched member must keep its original role.
    const user = userEvent.setup();
    const sam = memberDto({ displayName: "Sam Member", role: "MEMBER" });
    const tina = memberDto({ displayName: "Tina Member", role: "READ_ONLY" });
    vi.mocked(
      apiConfig.namespaceMembersApi.listNamespaceMembers,
    ).mockResolvedValue({ data: [sam, tina] } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.listNamespaceMembers>
    >);
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);
    vi.mocked(
      apiConfig.namespaceMembersApi.updateNamespaceMember,
    ).mockResolvedValue({
      data: { ...sam, role: "ADMIN" },
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.namespaceMembersApi.updateNamespaceMember>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    await screen.findByText("Sam Member");

    // Two role selects (one per member) — Sam's is the first.
    const roleSelects = screen.getAllByRole("combobox");
    await user.click(roleSelects[0]);
    await user.click(await screen.findByRole("option", { name: "Admin" }));

    await waitFor(() => {
      expect(
        apiConfig.namespaceMembersApi.updateNamespaceMember,
      ).toHaveBeenCalledWith({
        ns: "ns-1",
        userId: sam.userId,
        namespaceMemberUpdateRequest: { role: "ADMIN" },
      });
    });
    // Tina's row is unchanged.
    expect(screen.getByText("Tina Member")).toBeInTheDocument();
  });

  it("resets the picker state when the dialog is closed via the X button", async () => {
    const opal = userDto({
      displayName: "Opal User",
      username: "opal",
      source: "INTERNAL",
    });
    vi.mocked(apiConfig.adminUsersApi.listUsers).mockResolvedValue({
      data: pagedUsers([opal]),
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminUsersApi.listUsers>
    >);

    renderWithTheme(<MembersSection slug="ns-1" />);
    const { user, dialog } = await openAddMemberDialog();
    const input = within(dialog).getByRole("combobox", { name: /user/i });
    await user.click(input);
    await user.click(await screen.findByText("Opal User (opal)"));

    // Close via the header X — exercises the onClose reset branch.
    await user.click(
      within(dialog).getByRole("button", { name: /close dialog/i }),
    );
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // Re-open: the previous selection is gone (fresh dialog).
    await user.click(screen.getByRole("button", { name: /add member/i }));
    const dialog2 = await screen.findByRole("dialog");
    expect(
      within(dialog2).getByRole("button", { name: /^add member$/i }),
    ).toBeDisabled();
  });
});
