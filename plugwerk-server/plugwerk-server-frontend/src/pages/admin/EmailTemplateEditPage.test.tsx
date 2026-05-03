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
import { EmailTemplateEditPage } from "./EmailTemplateEditPage";
import { renderWithRouterAt } from "../../test/renderWithTheme";
import { useEmailTemplatesStore } from "../../stores/emailTemplatesStore";
import { useUiStore } from "../../stores/uiStore";
import type { MailTemplateResponse } from "../../api/generated/model/mail-template-response";
import { MailTemplateResponseSourceEnum } from "../../api/generated/model/mail-template-response";
import * as apiConfig from "../../api/config";

vi.mock("../../api/config", () => ({
  adminEmailTemplatesApi: {
    listMailTemplates: vi.fn(),
    getMailTemplate: vi.fn(),
    updateMailTemplate: vi.fn(),
    resetMailTemplate: vi.fn(),
    previewMailTemplate: vi.fn(),
  },
}));

const TEMPLATE: MailTemplateResponse = {
  key: "auth.password_reset",
  friendlyName: "Auth · Password Reset",
  locale: "en",
  subject: "Reset your password",
  bodyPlain: "Hello {{username}}, click {{resetLink}}",
  bodyHtml: '<p>Hello {{username}}, <a href="{{resetLink}}">reset</a></p>',
  defaultSubject: "Reset your Plugwerk password",
  defaultBodyPlain: "Hi {{username}}, default plain body",
  defaultBodyHtml: "<p>Hi {{username}}, default HTML body</p>",
  placeholders: ["username", "resetLink", "expiresAtHuman"],
  source: MailTemplateResponseSourceEnum.Database,
  updatedAt: "2026-05-03T10:00:00Z",
  updatedBy: "admin",
};

function setStoreWithTemplate(template: MailTemplateResponse = TEMPLATE) {
  useEmailTemplatesStore.setState({
    templates: [template],
    loaded: true,
    loading: false,
    saving: false,
    error: null,
  });
}

describe("EmailTemplateEditPage", () => {
  beforeEach(() => {
    setStoreWithTemplate();
    useUiStore.setState({ toasts: [] });
    vi.clearAllMocks();
  });

  function renderAt(key: string = TEMPLATE.key) {
    return renderWithRouterAt(
      <EmailTemplateEditPage />,
      "/admin/email/templates/:key",
      `/admin/email/templates/${encodeURIComponent(key)}`,
    );
  }

  it("renders subject + plaintext body + placeholder reference chips", () => {
    renderAt();
    expect(screen.getByText("Auth · Password Reset")).toBeInTheDocument();
    expect(screen.getByLabelText("Subject")).toHaveValue("Reset your password");
    // The plaintext body renders inside a CodeMirror editor; the wrapper
    // is exposed as a labelled group with the doc text inline. We check
    // via textContent rather than `.toHaveValue` because there is no
    // `<textarea>` to read from.
    const plainEditor = screen.getByRole("group", { name: "Plaintext body" });
    expect(plainEditor.textContent ?? "").toContain("Hello");
    expect(plainEditor.textContent ?? "").toContain("{{username}}");
    // Placeholder chips render with full {{name}} braces. There may be
    // multiple matches (chip + decorated mustache token in the editor +
    // sample-vars form labels in the live preview footer), so use
    // getAllByText for all of them.
    expect(screen.getAllByText("{{username}}").length).toBeGreaterThan(0);
    expect(screen.getAllByText("{{resetLink}}").length).toBeGreaterThan(0);
    expect(screen.getAllByText("{{expiresAtHuman}}").length).toBeGreaterThan(0);
  });

  it("renders a 404-style warning + back link when the registry key is unknown", () => {
    useEmailTemplatesStore.setState({
      templates: [],
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    renderAt("never.registered");
    expect(screen.getByRole("alert")).toHaveTextContent(/never.registered/);
    expect(
      screen.getByRole("button", { name: /Back to templates/i }),
    ).toBeInTheDocument();
  });

  it("Save is disabled until the user edits a field", async () => {
    const user = userEvent.setup();
    renderAt();
    const saveButton = screen.getByRole("button", { name: /Save Changes/i });
    expect(saveButton).toBeDisabled();

    await user.type(screen.getByLabelText("Subject"), " (edited)");
    expect(saveButton).toBeEnabled();
  });

  it("Discard reverts in-form changes and re-disables Save", async () => {
    const user = userEvent.setup();
    renderAt();
    const subject = screen.getByLabelText("Subject");
    await user.type(subject, " (edited)");
    expect(subject).toHaveValue("Reset your password (edited)");

    await user.click(screen.getByRole("button", { name: /Discard/i }));
    expect(subject).toHaveValue("Reset your password");
    expect(
      screen.getByRole("button", { name: /Save Changes/i }),
    ).toBeDisabled();
  });

  it("Save calls store.update with the edited fields and shows a success toast", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockResolvedValue({
      data: { ...TEMPLATE, subject: "Reset your password (edited)" },
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.updateMailTemplate>
    >);
    renderAt();

    await user.type(screen.getByLabelText("Subject"), " (edited)");
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.updateMailTemplate,
      ).toHaveBeenCalledWith({
        key: TEMPLATE.key,
        mailTemplateUpdateRequest: expect.objectContaining({
          subject: "Reset your password (edited)",
          bodyPlain: TEMPLATE.bodyPlain,
        }),
      });
    });
    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({ type: "success" }),
      );
    });
  });

  it("clicking a placeholder chip inserts {{name}} at the focused field caret", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockResolvedValue({
      data: TEMPLATE,
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.updateMailTemplate>
    >);
    renderAt();

    // lastFocusedRef defaults to "bodyPlain" — clicking the chip inserts
    // into the plaintext editor. We can't read CodeMirror's value via
    // .toHaveValue, so verify by triggering Save and inspecting the PUT
    // payload — that's the contract that actually matters.
    //
    // Note: `{{expiresAtHuman}}` matches in two places (the chip and the
    // live-preview sample-vars form label). The chip is the first match;
    // grab it explicitly.
    await user.click(screen.getAllByText("{{expiresAtHuman}}")[0]);
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.updateMailTemplate,
      ).toHaveBeenCalledWith(
        expect.objectContaining({
          mailTemplateUpdateRequest: expect.objectContaining({
            bodyPlain: expect.stringContaining("{{expiresAtHuman}}"),
          }),
        }),
      );
    });
  });

  it("renders the live preview side-by-side and fires preview API on mount with the current draft", async () => {
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).mockResolvedValue({
      data: {
        subject: "Reset for Alice",
        bodyPlain: "Hi Alice",
        bodyHtml: "<p>Hi Alice</p>",
        sampleVars: {
          username: "Alice",
          resetLink: "https://x",
          expiresAtHuman: "soon",
        },
      },
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.previewMailTemplate>
    >);
    renderAt();

    // The live preview panel mounts and immediately fires the preview
    // request with the current draft — no Preview button to click.
    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.previewMailTemplate,
      ).toHaveBeenCalledWith({
        key: TEMPLATE.key,
        mailTemplatePreviewRequest: expect.objectContaining({
          subject: TEMPLATE.subject,
          bodyPlain: TEMPLATE.bodyPlain,
          bodyHtml: TEMPLATE.bodyHtml,
        }),
      });
    });
    // The page renders three preview panes — one per editor — sharing
    // a single API result.
    expect(
      screen.getByRole("region", { name: "Subject preview" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("region", { name: "Plaintext body preview" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("region", { name: "HTML body preview" }),
    ).toBeInTheDocument();
  });

  it("Reset is disabled when the template is not customised", () => {
    setStoreWithTemplate({
      ...TEMPLATE,
      source: MailTemplateResponseSourceEnum.Default,
    });
    renderAt();
    expect(
      screen.getByRole("button", { name: /Reset to default/i }),
    ).toBeDisabled();
  });

  it("Reset opens a confirm dialog with the template name", async () => {
    const user = userEvent.setup();
    renderAt();

    await user.click(screen.getByRole("button", { name: /Reset to default/i }));
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText(/Reset .Auth · Password Reset. to default\?/i),
    ).toBeInTheDocument();
  });

  it("Reset dialog Cancel does NOT call store.reset", async () => {
    const user = userEvent.setup();
    renderAt();

    await user.click(screen.getByRole("button", { name: /Reset to default/i }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: /Cancel/i }));

    expect(
      apiConfig.adminEmailTemplatesApi.resetMailTemplate,
    ).not.toHaveBeenCalled();
  });

  it("Reset dialog Confirm replaces the form values with the defaults", async () => {
    // Regression test: previously the store was updated but the local
    // draft state stayed put, leaving customised text in the form even
    // though "Reset" succeeded server-side.
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.resetMailTemplate,
    ).mockResolvedValue(
      undefined as unknown as Awaited<
        ReturnType<typeof apiConfig.adminEmailTemplatesApi.resetMailTemplate>
      >,
    );
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.getMailTemplate,
    ).mockResolvedValue({
      data: {
        ...TEMPLATE,
        // Server returns the default values, source flips back to DEFAULT.
        subject: TEMPLATE.defaultSubject,
        bodyPlain: TEMPLATE.defaultBodyPlain,
        bodyHtml: TEMPLATE.defaultBodyHtml,
        source: MailTemplateResponseSourceEnum.Default,
      },
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.getMailTemplate>
    >);
    renderAt();

    // Confirm the form starts with the customised values.
    expect(screen.getByLabelText("Subject")).toHaveValue("Reset your password");

    await user.click(screen.getByRole("button", { name: /Reset to default/i }));
    const dialog = await screen.findByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: /Reset template/i }),
    );

    await waitFor(() => {
      expect(screen.getByLabelText("Subject")).toHaveValue(
        TEMPLATE.defaultSubject,
      );
    });
    // The dialog's MUI exit transition leaves it in the tree briefly with
    // `aria-hidden=true` on the rest of the document, which hides
    // role-based queries against the editor. Wait for the dialog to fully
    // unmount before checking editor content.
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    // CodeMirror exposes its value as DOM text in the labelled group
    // wrapper rather than via a textarea; check the rendered content.
    const plainEditor = screen.getByRole("group", { name: "Plaintext body" });
    expect(plainEditor.textContent ?? "").toContain("default plain body");
  });

  it("Reset dialog Confirm calls store.reset and surfaces a success toast", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.resetMailTemplate,
    ).mockResolvedValue(
      undefined as unknown as Awaited<
        ReturnType<typeof apiConfig.adminEmailTemplatesApi.resetMailTemplate>
      >,
    );
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.getMailTemplate,
    ).mockResolvedValue({
      data: { ...TEMPLATE, source: MailTemplateResponseSourceEnum.Default },
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.getMailTemplate>
    >);
    renderAt();

    await user.click(screen.getByRole("button", { name: /Reset to default/i }));
    const dialog = await screen.findByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: /Reset template/i }),
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.resetMailTemplate,
      ).toHaveBeenCalledWith({
        key: TEMPLATE.key,
      });
    });
    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({ type: "success" }),
      );
    });
  });

  it("toggling 'Add HTML alternative' off and back on preserves the seeded default", async () => {
    const user = userEvent.setup();
    setStoreWithTemplate({ ...TEMPLATE, bodyHtml: undefined });
    renderAt();

    // Initially off — editor not visible.
    expect(
      screen.queryByRole("group", { name: "HTML body" }),
    ).not.toBeInTheDocument();

    // FormControlLabel's visible label takes precedence over the input's
    // aria-label as the accessible name, so the toggle is queryable as
    // "Plaintext only" (off state) here.
    const toggle = screen.getByLabelText("Plaintext only");
    await user.click(toggle);

    // Editor appears, seeded with the enum default. CodeMirror exposes
    // its content as inline DOM text inside the labelled group wrapper.
    const htmlEditor = await screen.findByRole("group", { name: "HTML body" });
    expect(htmlEditor.textContent ?? "").toContain("default HTML body");
  });
});
