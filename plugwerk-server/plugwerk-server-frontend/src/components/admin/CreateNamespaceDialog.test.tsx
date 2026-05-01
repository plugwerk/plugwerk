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
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, AxiosHeaders } from "axios";
import { CreateNamespaceDialog } from "./CreateNamespaceDialog";
import { renderWithTheme } from "../../test/renderWithTheme";
import * as apiConfig from "../../api/config";

vi.mock("../../api/config", () => ({
  namespacesApi: {
    createNamespace: vi.fn(),
  },
}));

function setup(
  overrides: { onCreated?: () => void; onError?: () => void } = {},
) {
  return renderWithTheme(
    <CreateNamespaceDialog
      open
      onClose={() => {}}
      onCreated={overrides.onCreated ?? (() => {})}
      onError={overrides.onError ?? (() => {})}
    />,
  );
}

describe("CreateNamespaceDialog — premature-validation gate (issue #405)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("opens calmly: no aria-invalid fields, no Required text on mount", async () => {
    setup();

    // Wait for the dialog to be in the document.
    await screen.findByRole("dialog");

    // Slug + Name are required, but neither should be flagged as invalid
    // before the operator has interacted with the form.
    const slug = screen.getByRole("textbox", { name: /slug/i });
    const name = screen.getByRole("textbox", { name: /^name/i });
    expect(slug).not.toHaveAttribute("aria-invalid", "true");
    expect(name).not.toHaveAttribute("aria-invalid", "true");
    // Helper texts show their informational defaults, never "Required.".
    expect(screen.queryByText("Required.")).not.toBeInTheDocument();
  });

  it("blur on empty Slug reveals the format/required error", async () => {
    const user = userEvent.setup();
    setup();

    const slug = screen.getByRole("textbox", { name: /slug/i });
    await user.click(slug);
    await user.tab(); // blur away from the slug field

    await waitFor(() => {
      expect(slug).toHaveAttribute("aria-invalid", "true");
    });
    expect(screen.getByText("Required.")).toBeInTheDocument();
  });

  it("submit attempt with empty fields reveals every error and keeps the action button disabled", async () => {
    const user = userEvent.setup();
    setup();

    // The Create button stays disabled while errors exist, so we cannot
    // click it directly. We simulate the submit attempt by clicking it
    // anyway (userEvent honours the `disabled` attribute and does
    // nothing) — and then verify that even *without* a click, the
    // touched-gate is still off until either blur or a real submit.
    //
    // Tab from the slug to the name to the create-button to trigger
    // both blur events; the gate must reveal both errors.
    const slug = screen.getByRole("textbox", { name: /slug/i });
    await user.click(slug);
    await user.tab(); // → name
    await user.tab(); // → description (blurs name)

    await waitFor(() => {
      expect(screen.getAllByText("Required.")).toHaveLength(2);
    });
    const create = screen.getByRole("button", { name: /create/i });
    expect(create).toBeDisabled();
  });

  it("server 409 response surfaces inline below the Slug field even when Slug is untouched", async () => {
    // This pins the carve-out: the server-side conflict error MUST NOT be
    // hidden by the touched-gate. The operator just hit Create — they
    // need to see the conflict message, untouched-state or not.
    const user = userEvent.setup();
    vi.mocked(apiConfig.namespacesApi.createNamespace).mockRejectedValue(
      new AxiosError("Conflict", "ERR_BAD_REQUEST", undefined, undefined, {
        status: 409,
        statusText: "Conflict",
        headers: {},
        config: { headers: new AxiosHeaders() },
        data: { message: "Namespace already exists." },
      }),
    );
    setup();

    // Type a *valid* slug so client validation passes — the server-side
    // 409 is the only error path we want to verify here.
    await user.type(
      screen.getByRole("textbox", { name: /slug/i }),
      "my-namespace",
    );
    await user.type(screen.getByRole("textbox", { name: /^name/i }), "My NS");

    const create = screen.getByRole("button", { name: /create/i });
    await waitFor(() => expect(create).toBeEnabled());
    await user.click(create);

    // Server 409 message renders unconditionally — even if we hadn't
    // touched/blurred anything, this message would still appear.
    expect(
      await screen.findByText("Namespace already exists."),
    ).toBeInTheDocument();
  });

  it("server 409 message clears when the operator edits the slug", async () => {
    // Once the operator changes the conflicting value, the previous error
    // is stale and must go — otherwise it would persist across an edit
    // and confuse them about which value the message actually refers to.
    const user = userEvent.setup();
    vi.mocked(apiConfig.namespacesApi.createNamespace).mockRejectedValue(
      new AxiosError("Conflict", "ERR_BAD_REQUEST", undefined, undefined, {
        status: 409,
        statusText: "Conflict",
        headers: {},
        config: { headers: new AxiosHeaders() },
        data: { message: "Namespace already exists." },
      }),
    );
    setup();

    await user.type(
      screen.getByRole("textbox", { name: /slug/i }),
      "taken-slug",
    );
    await user.type(screen.getByRole("textbox", { name: /^name/i }), "Taken");
    await user.click(screen.getByRole("button", { name: /create/i }));
    await screen.findByText("Namespace already exists.");

    // Edit the slug — message should disappear.
    await user.type(
      screen.getByRole("textbox", { name: /slug/i }),
      "-different",
    );
    await waitFor(() => {
      expect(
        screen.queryByText("Namespace already exists."),
      ).not.toBeInTheDocument();
    });
  });
});
