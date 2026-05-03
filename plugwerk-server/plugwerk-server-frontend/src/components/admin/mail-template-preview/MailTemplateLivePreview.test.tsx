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
import { MailTemplateLivePreview } from "./MailTemplateLivePreview";
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

describe("MailTemplateLivePreview", () => {
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

  it("fires preview API on mount and reaches Live status", async () => {
    mockPreview();
    renderWithTheme(
      <MailTemplateLivePreview
        templateKey="auth.password_reset"
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
    await waitFor(() => {
      expect(
        screen.getByRole("status", { name: /Preview status: Live/i }),
      ).toBeInTheDocument();
    });
  });

  it("disables the HTML tab when the draft has no HTML body", async () => {
    mockPreview({ bodyHtml: null });
    renderWithTheme(
      <MailTemplateLivePreview
        templateKey="auth.password_reset"
        draft={{ ...DRAFT, bodyHtml: null }}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
      />,
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.previewMailTemplate,
      ).toHaveBeenCalled();
    });
    expect(screen.getByRole("tab", { name: "HTML" })).toBeDisabled();
  });

  it("surfaces the API error in an inline alert when the server rejects the draft", async () => {
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).mockRejectedValue(new Error("undocumented placeholder badVar"));

    renderWithTheme(
      <MailTemplateLivePreview
        templateKey="auth.password_reset"
        draft={DRAFT}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
      />,
    );

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        /undocumented placeholder/,
      );
    });
    expect(
      screen.getByRole("status", { name: /Preview status: Error/i }),
    ).toBeInTheDocument();
  });

  it("expanding sample vars + clicking Apply re-runs the preview with the edited overrides", async () => {
    const user = userEvent.setup();
    mockPreview();
    renderWithTheme(
      <MailTemplateLivePreview
        templateKey="auth.password_reset"
        draft={DRAFT}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
      />,
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.previewMailTemplate,
      ).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByRole("button", { name: /Sample variables/i }));
    const usernameField = await screen.findByLabelText("{{username}}");
    await user.clear(usernameField);
    await user.type(usernameField, "Bob");
    await user.click(screen.getByRole("button", { name: /Apply variables/i }));

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

  it("accordion variant collapses on header click and hides the body", async () => {
    const user = userEvent.setup();
    mockPreview();
    renderWithTheme(
      <MailTemplateLivePreview
        templateKey="auth.password_reset"
        draft={DRAFT}
        placeholders={["username", "resetLink", "expiresAtHuman"]}
        variant="accordion"
      />,
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.previewMailTemplate,
      ).toHaveBeenCalled();
    });
    // Toggle is the header itself.
    const header = screen.getByRole("button", { name: /^Live preview/i });
    expect(header).toHaveAttribute("aria-expanded", "true");
    await user.click(header);
    expect(header).toHaveAttribute("aria-expanded", "false");
  });
});
