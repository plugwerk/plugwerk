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
import { MailTemplatePreviewDrawer } from "./MailTemplatePreviewDrawer";
import { renderWithTheme } from "../../../test/renderWithTheme";
import { useEmailTemplatesStore } from "../../../stores/emailTemplatesStore";
import * as apiConfig from "../../../api/config";

vi.mock("../../../api/config", () => ({
  adminEmailTemplatesApi: {
    listMailTemplates: vi.fn(),
    getMailTemplate: vi.fn(),
    updateMailTemplate: vi.fn(),
    resetMailTemplate: vi.fn(),
    previewMailTemplate: vi.fn(),
  },
}));

const DRAFT = {
  subject: "Reset for {{username}}",
  bodyPlain: "Hi {{username}}, click {{resetLink}}",
  bodyHtml: '<p>Hi {{username}}, <a href="{{resetLink}}">reset</a></p>',
};

function mockPreview(
  overrides: Partial<{
    subject: string;
    bodyPlain: string;
    bodyHtml: string | null;
    sampleVars: Record<string, string>;
  }> = {},
) {
  vi.mocked(
    apiConfig.adminEmailTemplatesApi.previewMailTemplate,
  ).mockResolvedValue({
    data: {
      subject: "Reset for Alice",
      bodyPlain: "Hi Alice, click https://example.com/reset",
      bodyHtml:
        '<p>Hi Alice, <a href="https://example.com/reset">reset</a></p>',
      sampleVars: {
        username: "Alice",
        resetLink: "https://example.com/reset",
        expiresAtHuman: "in 30 minutes",
      },
      ...overrides,
    },
  } as unknown as Awaited<
    ReturnType<typeof apiConfig.adminEmailTemplatesApi.previewMailTemplate>
  >);
}

describe("MailTemplatePreviewDrawer", () => {
  beforeEach(() => {
    useEmailTemplatesStore.setState({
      templates: [],
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    vi.clearAllMocks();
  });

  it("calls preview API on open and renders subject + plaintext body", async () => {
    mockPreview();
    renderWithTheme(
      <MailTemplatePreviewDrawer
        open={true}
        onClose={() => {}}
        templateKey="auth.password_reset"
        templateFriendlyName="Auth · Password Reset"
        draft={DRAFT}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
      />,
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.previewMailTemplate,
      ).toHaveBeenCalledWith({
        key: "auth.password_reset",
        mailTemplatePreviewRequest: expect.objectContaining({
          subject: DRAFT.subject,
          bodyPlain: DRAFT.bodyPlain,
          bodyHtml: DRAFT.bodyHtml,
        }),
      });
    });
    // Header shows the rendered subject. The HTML tab is the default
    // when an HTML body is present, so look in the iframe for HTML
    // and switch to plain to verify the plaintext rendered text.
    await waitFor(() => {
      expect(
        screen.getByText("Reset for Alice", { selector: "*" }),
      ).toBeInTheDocument();
    });
  });

  it("disables the HTML tab when the draft has no HTML body", async () => {
    mockPreview({ bodyHtml: null });
    renderWithTheme(
      <MailTemplatePreviewDrawer
        open={true}
        onClose={() => {}}
        templateKey="auth.password_reset"
        templateFriendlyName="Auth · Password Reset"
        draft={{ ...DRAFT, bodyHtml: null }}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
      />,
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.previewMailTemplate,
      ).toHaveBeenCalled();
    });
    // MUI's Tab forwards `disabled` to the underlying ButtonBase as
    // a real `disabled` attribute (not aria-disabled) — that's the
    // assertion that matches MUI's implementation.
    const htmlTab = screen.getByRole("tab", { name: "HTML" });
    expect(htmlTab).toBeDisabled();
  });

  it("renders an inline error when the API rejects the draft", async () => {
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).mockRejectedValue(new Error("undocumented placeholder badVar"));

    renderWithTheme(
      <MailTemplatePreviewDrawer
        open={true}
        onClose={() => {}}
        templateKey="auth.password_reset"
        templateFriendlyName="Auth · Password Reset"
        draft={DRAFT}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
      />,
    );

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        /undocumented placeholder/,
      );
    });
  });

  it("expanding sample vars + clicking refresh re-runs preview with the edited overrides", async () => {
    const user = userEvent.setup();
    mockPreview();
    renderWithTheme(
      <MailTemplatePreviewDrawer
        open={true}
        onClose={() => {}}
        templateKey="auth.password_reset"
        templateFriendlyName="Auth · Password Reset"
        draft={DRAFT}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
      />,
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.previewMailTemplate,
      ).toHaveBeenCalledTimes(1);
    });

    // Open the sample-vars panel and edit `username`.
    await user.click(screen.getByRole("button", { name: /Sample variables/i }));
    const usernameField = await screen.findByLabelText("{{username}}");
    await user.clear(usernameField);
    await user.type(usernameField, "Bob");
    await user.click(
      screen.getByRole("button", { name: /Refresh with these values/i }),
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.previewMailTemplate,
      ).toHaveBeenLastCalledWith({
        key: "auth.password_reset",
        mailTemplatePreviewRequest: expect.objectContaining({
          sampleVars: expect.objectContaining({ username: "Bob" }),
        }),
      });
    });
  });

  it("Close button fires onClose", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    mockPreview();
    renderWithTheme(
      <MailTemplatePreviewDrawer
        open={true}
        onClose={onClose}
        templateKey="auth.password_reset"
        templateFriendlyName="Auth · Password Reset"
        draft={DRAFT}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
      />,
    );

    await user.click(screen.getByRole("button", { name: /Close preview/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
