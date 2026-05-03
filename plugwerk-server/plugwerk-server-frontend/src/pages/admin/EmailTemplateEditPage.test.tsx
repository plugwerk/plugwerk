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
    expect(screen.getByLabelText("Plaintext body")).toHaveValue(
      "Hello {{username}}, click {{resetLink}}",
    );
    // Placeholder chips render with full {{name}} braces.
    expect(screen.getByText("{{username}}")).toBeInTheDocument();
    expect(screen.getByText("{{resetLink}}")).toBeInTheDocument();
    expect(screen.getByText("{{expiresAtHuman}}")).toBeInTheDocument();
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
    renderAt();
    const bodyPlain = screen.getByLabelText(
      "Plaintext body",
    ) as HTMLTextAreaElement;
    // Last-focused defaults to bodyPlain — append a new var via chip click.
    await user.click(bodyPlain);
    bodyPlain.setSelectionRange(bodyPlain.value.length, bodyPlain.value.length);
    await user.click(screen.getByText("{{expiresAtHuman}}"));

    expect(bodyPlain.value).toContain("{{expiresAtHuman}}");
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

    // Initially off — body field not visible.
    expect(screen.queryByLabelText("HTML body")).not.toBeInTheDocument();

    // FormControlLabel's visible label takes precedence over the input's
    // aria-label as the accessible name, so the toggle is queryable as
    // "Plaintext only" (off state) here.
    const toggle = screen.getByLabelText("Plaintext only");
    await user.click(toggle);
    const htmlField = await screen.findByLabelText("HTML body");
    // Seeded with the enum default.
    expect(htmlField).toHaveValue(TEMPLATE.defaultBodyHtml);
  });
});
