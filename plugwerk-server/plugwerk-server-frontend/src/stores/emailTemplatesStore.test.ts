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
import { describe, it, expect, beforeEach, vi } from "vitest";
import * as apiConfig from "../api/config";
import { useEmailTemplatesStore } from "./emailTemplatesStore";
import type { MailTemplateResponse } from "../api/generated/model/mail-template-response";
import { MailTemplateResponseSourceEnum } from "../api/generated/model/mail-template-response";

vi.mock("../api/config", () => ({
  adminEmailTemplatesApi: {
    listMailTemplates: vi.fn(),
    getMailTemplate: vi.fn(),
    updateMailTemplate: vi.fn(),
    resetMailTemplate: vi.fn(),
    previewMailTemplate: vi.fn(),
  },
}));

function template(
  overrides: Partial<MailTemplateResponse> = {},
): MailTemplateResponse {
  return {
    key: "auth.password_reset",
    friendlyName: "Auth · Password Reset",
    locale: "en",
    subject: "Reset your password",
    bodyPlain: "Hi {{username}}",
    bodyHtml: "<p>Hi {{username}}</p>",
    defaultSubject: "Reset your password",
    defaultBodyPlain: "Hi {{username}}",
    defaultBodyHtml: "<p>Hi {{username}}</p>",
    placeholders: ["username", "resetLink", "expiresAtHuman"],
    source: MailTemplateResponseSourceEnum.Default,
    ...overrides,
  };
}

describe("useEmailTemplatesStore", () => {
  beforeEach(() => {
    useEmailTemplatesStore.getState().clear();
    vi.clearAllMocks();
  });

  it("load() populates templates and flips loaded=true", async () => {
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.listMailTemplates,
    ).mockResolvedValue({
      data: { templates: [template()] },
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.listMailTemplates>
    >);

    await useEmailTemplatesStore.getState().load();

    const state = useEmailTemplatesStore.getState();
    expect(state.loaded).toBe(true);
    expect(state.loading).toBe(false);
    expect(state.templates).toHaveLength(1);
    expect(state.templates[0].key).toBe("auth.password_reset");
  });

  it("load() sets error on failure but still flips loaded=true", async () => {
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.listMailTemplates,
    ).mockRejectedValue(new Error("network down"));

    await expect(useEmailTemplatesStore.getState().load()).rejects.toThrow();
    const state = useEmailTemplatesStore.getState();
    expect(state.loaded).toBe(true);
    expect(state.error).toBe("network down");
  });

  it("update() replaces the matching template by key, leaves others alone", async () => {
    const initial = [
      template({ key: "auth.password_reset", subject: "v1" }),
      template({ key: "auth.registration_verification", subject: "verify" }),
    ];
    useEmailTemplatesStore.setState({
      templates: initial,
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockResolvedValue({
      data: template({
        key: "auth.password_reset",
        subject: "v2",
        source: MailTemplateResponseSourceEnum.Database,
      }),
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.updateMailTemplate>
    >);

    const next = await useEmailTemplatesStore
      .getState()
      .update("auth.password_reset", {
        subject: "v2",
        bodyPlain: "Hi {{username}}",
      });

    expect(next.subject).toBe("v2");
    const state = useEmailTemplatesStore.getState();
    expect(state.templates).toHaveLength(2);
    expect(
      state.templates.find((t) => t.key === "auth.password_reset")?.subject,
    ).toBe("v2");
    expect(
      state.templates.find((t) => t.key === "auth.registration_verification")
        ?.subject,
    ).toBe("verify");
  });

  it("update() surfaces server errors as error state and rethrows", async () => {
    useEmailTemplatesStore.setState({
      templates: [template()],
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.updateMailTemplate,
    ).mockRejectedValue(new Error("validation failed"));

    await expect(
      useEmailTemplatesStore.getState().update("auth.password_reset", {
        subject: "x",
        bodyPlain: "y",
      }),
    ).rejects.toThrow("validation failed");
    expect(useEmailTemplatesStore.getState().error).toBe("validation failed");
    expect(useEmailTemplatesStore.getState().saving).toBe(false);
  });

  it("preview() forwards the draft to the backend and returns the rendered response without mutating store state", async () => {
    useEmailTemplatesStore.setState({
      templates: [template()],
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
    vi.mocked(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).mockResolvedValue({
      data: {
        subject: "Reset for Bob",
        bodyPlain: "Hi Bob",
        bodyHtml: "<p>Hi Bob</p>",
        sampleVars: {
          username: "Bob",
          resetLink: "https://x",
          expiresAtHuman: "soon",
        },
      },
    } as unknown as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.previewMailTemplate>
    >);

    const result = await useEmailTemplatesStore
      .getState()
      .preview("auth.password_reset", {
        subject: "Reset for {{username}}",
        bodyPlain: "Hi {{username}}",
        bodyHtml: "<p>Hi {{username}}</p>",
        sampleVars: { username: "Bob" },
      });

    expect(result.subject).toBe("Reset for Bob");
    expect(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).toHaveBeenCalledWith({
      key: "auth.password_reset",
      mailTemplatePreviewRequest: expect.objectContaining({
        sampleVars: { username: "Bob" },
      }),
    });
    // Store templates list untouched by preview.
    expect(useEmailTemplatesStore.getState().templates).toHaveLength(1);
    expect(useEmailTemplatesStore.getState().saving).toBe(false);
  });

  it("reset() calls DELETE then re-fetches the template via GET to refresh the slice", async () => {
    useEmailTemplatesStore.setState({
      templates: [
        template({
          source: MailTemplateResponseSourceEnum.Database,
          subject: "Customised",
        }),
      ],
      loaded: true,
      loading: false,
      saving: false,
      error: null,
    });
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
      data: template({
        subject: "Reset your password",
        source: MailTemplateResponseSourceEnum.Default,
      }),
    } as Awaited<
      ReturnType<typeof apiConfig.adminEmailTemplatesApi.getMailTemplate>
    >);

    await useEmailTemplatesStore.getState().reset("auth.password_reset");

    expect(
      apiConfig.adminEmailTemplatesApi.resetMailTemplate,
    ).toHaveBeenCalledWith({
      key: "auth.password_reset",
    });
    expect(
      apiConfig.adminEmailTemplatesApi.getMailTemplate,
    ).toHaveBeenCalledWith({
      key: "auth.password_reset",
    });
    const state = useEmailTemplatesStore.getState();
    expect(state.templates[0].subject).toBe("Reset your password");
    expect(state.templates[0].source).toBe(
      MailTemplateResponseSourceEnum.Default,
    );
  });
});
