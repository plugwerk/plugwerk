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
import { screen, waitFor, within, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, AxiosHeaders } from "axios";
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

// Replace the CodeMirror-backed editor with a lightweight stub. Real CodeMirror
// measures the DOM through Range.getClientRects, which jsdom does not lay out —
// with two to three editors per page render the retrying measure loop makes this
// suite time out on contended CI runners. The stub preserves everything the page
// contract needs: a labelled group exposing the doc text, onChange, and the
// insertAtCursor/focus handle used by the placeholder chips. The real component
// keeps its own coverage in MustacheCodeEditor.test.tsx.
vi.mock("../../components/admin/mustache/MustacheCodeEditor", () => ({
  MustacheCodeEditor: function MustacheCodeEditorStub({
    value,
    onChange,
    ariaLabel,
    disabled,
    onFocus,
    editorRef,
  }: {
    value: string;
    onChange: (next: string) => void;
    ariaLabel: string;
    disabled?: boolean;
    onFocus?: () => void;
    editorRef?: {
      current: {
        insertAtCursor: (text: string) => void;
        focus: () => void;
      } | null;
    };
  }) {
    if (editorRef) {
      editorRef.current = {
        insertAtCursor: (text: string) => onChange(value + text),
        focus: () => {},
      };
    }
    return (
      <div role="group" aria-label={ariaLabel}>
        <textarea
          aria-label={`${ariaLabel} text`}
          value={value}
          disabled={disabled}
          onFocus={onFocus}
          onChange={(event) => onChange(event.target.value)}
        />
        {/* CodeMirror renders the doc as inline DOM text; mirror that so the
            page tests' textContent assertions keep working. */}
        <span aria-hidden="true">{value}</span>
      </div>
    );
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

/** Build an AxiosError, optionally carrying a `response.data.message`. */
function makeAxiosError(message: string, dataMessage?: string): AxiosError {
  const err = new AxiosError(message);
  if (dataMessage !== undefined) {
    err.response = {
      data: { message: dataMessage },
      status: 400,
      statusText: "Bad Request",
      headers: {},
      config: { headers: new AxiosHeaders() },
    } as AxiosError["response"];
  }
  return err;
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

  it("shows a loading spinner while templates are still loading", () => {
    useEmailTemplatesStore.setState({
      templates: [],
      loaded: false,
      loading: true,
      saving: false,
      error: null,
    });
    renderAt();
    expect(screen.getByText("Loading template…")).toBeInTheDocument();
  });

  it("auto-loads templates on mount when the store is empty and surfaces an error toast on failure", async () => {
    useEmailTemplatesStore.setState({
      templates: [],
      loaded: false,
      loading: false,
      saving: false,
      error: null,
    });
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.listMailTemplates,
    ).mockRejectedValue(new Error("network down"));
    renderAt();

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.listMailTemplates,
      ).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({
          type: "error",
          message: "Failed to load mail templates.",
        }),
      );
    });
  });

  it("Back link on the 404 page navigates away (page content unmounts)", async () => {
    const user = userEvent.setup();
    useEmailTemplatesStore.setState({
      templates: [],
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    renderAt("never.registered");

    await user.click(
      screen.getByRole("button", { name: /Back to templates/i }),
    );
    // `/admin/email/templates` is not a registered route in this harness,
    // so navigating there unmounts the page — the warning alert disappears.
    await waitFor(() => {
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
  });

  it("'All templates' breadcrumb navigates away (page content unmounts)", async () => {
    const user = userEvent.setup();
    renderAt();
    expect(screen.getByText("Auth · Password Reset")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /All templates/i }));
    await waitFor(() => {
      expect(
        screen.queryByText("Auth · Password Reset"),
      ).not.toBeInTheDocument();
    });
  });

  it("inserts a placeholder into the subject field at the caret when the subject was last focused", async () => {
    const user = userEvent.setup();
    renderAt();

    const subject = screen.getByLabelText("Subject") as HTMLInputElement;
    // Focus the subject and place the caret at the start so the inserted
    // token lands at position 0 (exercises the subject-target slice path).
    await user.click(subject);
    subject.setSelectionRange(0, 0);

    // {{username}} appears in several places (chip + editor token + preview
    // sample-vars label); the chip is the first match.
    await user.click(screen.getAllByText("{{username}}")[0]);

    await waitFor(() => {
      expect(subject.value).toContain("{{username}}");
    });
    // Inserted at the caret (start), so it prefixes the original subject.
    expect(subject.value.startsWith("{{username}}")).toBe(true);
  });

  it("blocks Save with an error toast when the subject is blank", async () => {
    const user = userEvent.setup();
    setStoreWithTemplate({ ...TEMPLATE, subject: " " });
    renderAt();

    // Type into plaintext-irrelevant subject then clear it to a blank string.
    const subject = screen.getByLabelText("Subject") as HTMLInputElement;
    await user.clear(subject);
    await user.type(subject, "x");
    await user.clear(subject);

    // Save is dirty (subject changed from " " to "") so it is enabled.
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({
          type: "error",
          message: "Subject is required.",
        }),
      );
    });
    expect(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).not.toHaveBeenCalled();
  });

  it("blocks Save with an error toast when the plaintext body is blank", async () => {
    const user = userEvent.setup();
    // Seed a template whose plaintext body is already blank; editing the
    // subject makes the form dirty without populating the body.
    setStoreWithTemplate({ ...TEMPLATE, bodyPlain: "" });
    renderAt();

    await user.type(screen.getByLabelText("Subject"), "!");
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({
          type: "error",
          message: "Plaintext body is required.",
        }),
      );
    });
    expect(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).not.toHaveBeenCalled();
  });

  it("Save failure surfaces the API error message from an axios response (extractApiError: axios w/ message)", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockRejectedValue(makeAxiosError("Request failed", "Subject too long"));
    renderAt();

    await user.type(screen.getByLabelText("Subject"), " (edited)");
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({ type: "error", message: "Subject too long" }),
      );
    });
  });

  it("Save failure falls back to axios error.message when the response carries no message (extractApiError: axios w/o message)", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockRejectedValue(makeAxiosError("Network Error"));
    renderAt();

    await user.type(screen.getByLabelText("Subject"), " (edited)");
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({ type: "error", message: "Network Error" }),
      );
    });
  });

  it("Save failure surfaces a plain Error message (extractApiError: Error)", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockRejectedValue(new Error("boom"));
    renderAt();

    await user.type(screen.getByLabelText("Subject"), " (edited)");
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({ type: "error", message: "boom" }),
      );
    });
  });

  it("Save failure shows the generic fallback for a non-Error rejection (extractApiError: unknown)", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockRejectedValue("just a string");
    renderAt();

    await user.type(screen.getByLabelText("Subject"), " (edited)");
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({ type: "error", message: "Unknown error" }),
      );
    });
  });

  it("Reset failure surfaces the API error message and shows no success toast", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.resetMailTemplate,
    ).mockRejectedValue(makeAxiosError("Request failed", "Reset rejected"));
    renderAt();

    await user.click(screen.getByRole("button", { name: /Reset to default/i }));
    const dialog = await screen.findByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: /Reset template/i }),
    );

    await waitFor(() => {
      expect(useUiStore.getState().toasts).toContainEqual(
        expect.objectContaining({ type: "error", message: "Reset rejected" }),
      );
    });
    expect(useUiStore.getState().toasts).not.toContainEqual(
      expect.objectContaining({ type: "success" }),
    );
  });

  it("Save persists the edited HTML body when the HTML alternative is enabled", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockResolvedValue({
      data: TEMPLATE,
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.updateMailTemplate>
    >);
    // Template already has an HTML body, so the editor is visible on mount.
    renderAt();

    await user.type(screen.getByLabelText("Subject"), " (edited)");
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.updateMailTemplate,
      ).toHaveBeenCalledWith(
        expect.objectContaining({
          mailTemplateUpdateRequest: expect.objectContaining({
            bodyHtml: TEMPLATE.bodyHtml,
          }),
        }),
      );
    });
  });

  it("toggling the HTML alternative off clears the HTML body so Save omits it", async () => {
    const user = userEvent.setup();
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockResolvedValue({
      data: { ...TEMPLATE, bodyHtml: undefined },
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.updateMailTemplate>
    >);
    renderAt();

    // HTML editor is on (template has bodyHtml). Switching it off nulls the
    // draft's bodyHtml; Save then omits the field entirely.
    const toggle = screen.getByLabelText("HTML alternative enabled");
    await user.click(toggle);
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.updateMailTemplate,
      ).toHaveBeenCalled();
    });
    const call = vi.mocked(apiConfig.adminEmailTemplatesApi.updateMailTemplate)
      .mock.calls[0][0] as {
      mailTemplateUpdateRequest: { bodyHtml?: string };
    };
    expect(call.mailTemplateUpdateRequest.bodyHtml).toBeUndefined();
  });

  it("expands the 'Compare with default subject' panel to reveal the seeded default", async () => {
    const user = userEvent.setup();
    renderAt();

    const compareToggle = screen.getByRole("button", {
      name: /Show Default subject/i,
    });
    await user.click(compareToggle);

    await waitFor(() => {
      expect(screen.getByText(TEMPLATE.defaultSubject)).toBeInTheDocument();
    });
    // Toggling again collapses it (aria-label flips to "Hide …").
    expect(
      screen.getByRole("button", { name: /Hide Default subject/i }),
    ).toBeInTheDocument();
  });

  it("renders the 'Factory default' attribution for an un-customised template", () => {
    setStoreWithTemplate({
      ...TEMPLATE,
      source: MailTemplateResponseSourceEnum.Default,
    });
    renderAt();
    expect(screen.getByText("Factory default")).toBeInTheDocument();
  });

  it("renders the edited-by attribution without a 'by' fragment when updatedBy is absent", () => {
    setStoreWithTemplate({
      ...TEMPLATE,
      updatedBy: null,
      updatedAt: "2026-05-03T10:00:00Z",
    });
    renderAt();
    // The attribution caption starts with "Edited" and omits the "by …"
    // fragment when no author is recorded.
    const edited = screen.getByText(/^Edited/);
    expect(edited.textContent ?? "").not.toContain("by ");
  });

  it("renders a bare 'Edited' attribution when both updatedAt and updatedBy are missing", () => {
    setStoreWithTemplate({
      ...TEMPLATE,
      updatedAt: null,
      updatedBy: null,
    });
    renderAt();
    expect(screen.getByText("Edited")).toBeInTheDocument();
  });

  it("renders the 'takes no variables' note when the template has no placeholders", () => {
    setStoreWithTemplate({ ...TEMPLATE, placeholders: [] });
    renderAt();
    expect(
      screen.getByText("This template takes no variables."),
    ).toBeInTheDocument();
  });

  it("routes placeholder insertion to the HTML editor when it was last focused", async () => {
    const user = userEvent.setup();
    renderAt();

    // HTML editor is visible (template has an HTML body). Focus its editable
    // surface (the stub's labelled textarea) so lastFocusedRef flips to
    // "bodyHtml", then click a chip — insertion is dispatched to the HTML
    // editor handle.
    const htmlGroup = screen.getByRole("group", { name: "HTML body" });
    fireEvent.focus(within(htmlGroup).getByLabelText("HTML body text"));

    await user.click(screen.getAllByText("{{username}}")[0]);

    // The HTML editor now contains the inserted token. CodeMirror reflects
    // its value as inline DOM text inside the labelled group wrapper.
    await waitFor(() => {
      expect(
        screen.getByRole("group", { name: "HTML body" }).textContent ?? "",
      ).toContain("{{username}}");
    });
  });

  it("omits the default-HTML diff panel when the template has no default HTML body", async () => {
    const user = userEvent.setup();
    // No default HTML body — the "Compare with default HTML body" panel must
    // not render even once the HTML editor is shown.
    setStoreWithTemplate({
      ...TEMPLATE,
      bodyHtml: undefined,
      defaultBodyHtml: undefined,
    });
    renderAt();

    await user.click(screen.getByLabelText("Plaintext only"));
    await screen.findByRole("group", { name: "HTML body" });

    expect(
      screen.queryByRole("button", { name: /Default HTML body/i }),
    ).not.toBeInTheDocument();
  });

  it("seeds the HTML editor with an empty string when no default HTML body exists", async () => {
    const user = userEvent.setup();
    setStoreWithTemplate({
      ...TEMPLATE,
      bodyHtml: undefined,
      defaultBodyHtml: undefined,
    });
    renderAt();

    // Toggling on with no default seeds the editor with "" (the `?? ""`
    // fallback branch), so it mounts but stays empty.
    await user.click(screen.getByLabelText("Plaintext only"));
    const htmlEditor = await screen.findByRole("group", { name: "HTML body" });
    // Read the editable surface (the stub's labelled textarea) directly.
    const content =
      within(htmlEditor).getByLabelText<HTMLTextAreaElement>("HTML body text");
    expect(content.value).toBe("");
  });
});
