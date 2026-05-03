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
import { EmailTemplatesListPage } from "./EmailTemplatesListPage";
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

function template(
  overrides: Partial<MailTemplateResponse> = {},
): MailTemplateResponse {
  return {
    key: "auth.password_reset",
    friendlyName: "Auth · Password Reset",
    locale: "en",
    subject: "Reset your Plugwerk password",
    bodyPlain: "Hi {{username}}",
    bodyHtml: undefined,
    defaultSubject: "Reset your Plugwerk password",
    defaultBodyPlain: "Hi {{username}}",
    defaultBodyHtml: undefined,
    placeholders: ["username", "resetLink"],
    source: MailTemplateResponseSourceEnum.Default,
    ...overrides,
  };
}

const TEMPLATES: MailTemplateResponse[] = [
  template({
    key: "auth.password_reset",
    friendlyName: "Auth · Password Reset",
    source: MailTemplateResponseSourceEnum.Database,
    subject: "Custom subject for reset",
  }),
  template({
    key: "auth.registration_verification",
    friendlyName: "Auth · Registration Verification",
    source: MailTemplateResponseSourceEnum.Default,
    subject: "Verify your account",
  }),
];

describe("EmailTemplatesListPage", () => {
  beforeEach(() => {
    useEmailTemplatesStore.setState({
      templates: TEMPLATES,
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    useUiStore.setState({ toasts: [] });
    vi.clearAllMocks();
  });

  it("renders one row per template with friendly name + key + subject preview", () => {
    renderWithRouterAt(
      <EmailTemplatesListPage />,
      "/admin/email/templates",
      "/admin/email/templates",
    );

    expect(screen.getByText("Auth · Password Reset")).toBeInTheDocument();
    expect(
      screen.getByText("Auth · Registration Verification"),
    ).toBeInTheDocument();
    expect(screen.getByText("auth.password_reset")).toBeInTheDocument();
    expect(screen.getByText("Custom subject for reset")).toBeInTheDocument();
  });

  it("shows 'Customised' chip only on rows with source=DATABASE", () => {
    renderWithRouterAt(
      <EmailTemplatesListPage />,
      "/admin/email/templates",
      "/admin/email/templates",
    );

    const chips = screen.getAllByText("Customised");
    // Only the first (overridden) row carries the chip.
    expect(chips).toHaveLength(1);
  });

  it("calls store.load() on first mount when not yet loaded", async () => {
    useEmailTemplatesStore.setState({
      templates: [],
      loaded: false,
      loading: false,
      saving: false,
      error: null,
    });
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.listMailTemplates,
    ).mockResolvedValue({
      data: { templates: TEMPLATES },
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.listMailTemplates>
    >);

    renderWithRouterAt(
      <EmailTemplatesListPage />,
      "/admin/email/templates",
      "/admin/email/templates",
    );

    await waitFor(() => {
      expect(
        apiConfig.adminEmailTemplatesApi.listMailTemplates,
      ).toHaveBeenCalled();
    });
  });

  it("clicking a row navigates to /admin/email/templates/:key", async () => {
    const user = userEvent.setup();
    renderWithRouterAt(
      <EmailTemplatesListPage />,
      "/admin/email/templates",
      "/admin/email/templates",
    );

    const row = screen.getByRole("button", {
      name: /Edit Auth · Password Reset template/i,
    });
    await user.click(row);
    // The route is registered only at /admin/email/templates in this test
    // fixture, so navigation away from that path unmounts the list. The
    // assertion below proves the click triggered navigation by re-querying
    // the now-empty document for the row label.
    await waitFor(() => {
      expect(
        screen.queryByText("Custom subject for reset"),
      ).not.toBeInTheDocument();
    });
  });

  it("renders an error alert when the store reports an error and no templates loaded", () => {
    useEmailTemplatesStore.setState({
      templates: [],
      loaded: true,
      loading: false,
      saving: false,
      error: "Backend exploded",
    });
    renderWithRouterAt(
      <EmailTemplatesListPage />,
      "/admin/email/templates",
      "/admin/email/templates",
    );

    expect(screen.getByRole("alert")).toHaveTextContent("Backend exploded");
  });
});
