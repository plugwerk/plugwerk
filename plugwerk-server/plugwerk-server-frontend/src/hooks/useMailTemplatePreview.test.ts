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
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { AxiosError, AxiosHeaders } from "axios";
import { createQueryWrapper } from "../test/queryWrapper";
import {
  useMailTemplatePreview,
  type PreviewDraft,
} from "./useMailTemplatePreview";
import type { MailTemplatePreviewResponse } from "../api/generated/model/mail-template-preview-response";
import * as apiConfig from "../api/config";

vi.mock("../api/config", () => ({
  adminEmailTemplatesApi: {
    previewMailTemplate: vi.fn(),
  },
}));

const KEY = "auth.password_reset";

const DRAFT: PreviewDraft = {
  subject: "Reset your password",
  bodyPlain: "Hello {{username}}",
  bodyHtml: "<p>Hello {{username}}</p>",
};

const RESULT: MailTemplatePreviewResponse = {
  subject: "Reset your password",
  bodyPlain: "Hello Alice",
  bodyHtml: "<p>Hello Alice</p>",
  sampleVars: { username: "Alice" },
} as unknown as MailTemplatePreviewResponse;

function mockPreviewResolved(data: MailTemplatePreviewResponse = RESULT) {
  vi.mocked(
    apiConfig.adminEmailTemplatesApi.previewMailTemplate,
  ).mockResolvedValue({ data } as Awaited<
    ReturnType<typeof apiConfig.adminEmailTemplatesApi.previewMailTemplate>
  >);
}

function mockPreviewRejected(error: unknown) {
  vi.mocked(
    apiConfig.adminEmailTemplatesApi.previewMailTemplate,
  ).mockRejectedValue(error);
}

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

type SentPreviewRequest = {
  subject?: string;
  bodyPlain?: string;
  bodyHtml?: string;
  sampleVars?: Record<string, string>;
};

/** Pull the `mailTemplatePreviewRequest` out of the Nth generated-client call. */
function previewRequestOf(index: number): SentPreviewRequest {
  const call = vi.mocked(apiConfig.adminEmailTemplatesApi.previewMailTemplate)
    .mock.calls[index][0] as { mailTemplatePreviewRequest: SentPreviewRequest };
  return call.mailTemplatePreviewRequest;
}

function previewRequestOfLast(): SentPreviewRequest {
  const lastCall = vi.mocked(
    apiConfig.adminEmailTemplatesApi.previewMailTemplate,
  ).mock.lastCall as
    [{ mailTemplatePreviewRequest: SentPreviewRequest }] | undefined;
  return lastCall![0].mailTemplatePreviewRequest;
}

describe("useMailTemplatePreview", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("starts idle and fires no request when the draft is empty (enabled-gate)", async () => {
    mockPreviewResolved();
    const { result } = renderHook(
      () =>
        useMailTemplatePreview(
          "",
          { subject: "", bodyPlain: "", bodyHtml: null },
          {},
        ),
      { wrapper: createQueryWrapper() },
    );

    // Empty draft → enabled is false → no API call, status stays idle.
    expect(result.current.status).toBe("idle");
    expect(result.current.result).toBeNull();
    expect(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).not.toHaveBeenCalled();
  });

  it("skips the request when templateKey is empty even with a full draft", () => {
    mockPreviewResolved();
    renderHook(() => useMailTemplatePreview("", DRAFT, {}), {
      wrapper: createQueryWrapper(),
    });
    expect(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).not.toHaveBeenCalled();
  });

  it("skips the request when subject is blank but body is present", () => {
    mockPreviewResolved();
    renderHook(
      () => useMailTemplatePreview(KEY, { ...DRAFT, subject: "" }, {}),
      { wrapper: createQueryWrapper() },
    );
    expect(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).not.toHaveBeenCalled();
  });

  it("skips the request when bodyPlain is blank but subject is present", () => {
    mockPreviewResolved();
    renderHook(
      () => useMailTemplatePreview(KEY, { ...DRAFT, bodyPlain: "" }, {}),
      { wrapper: createQueryWrapper() },
    );
    expect(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).not.toHaveBeenCalled();
  });

  it("transitions syncing → live and exposes the result on a successful render", async () => {
    mockPreviewResolved();
    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, DRAFT, {}),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(result.current.status).toBe("live"));
    expect(result.current.result).toEqual(RESULT);
    expect(result.current.error).toBeNull();
    // The hook delegates to the store's preview(), which calls the generated
    // client as `previewMailTemplate({ key, mailTemplatePreviewRequest })`.
    expect(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).toHaveBeenCalledWith({
      key: KEY,
      mailTemplatePreviewRequest: expect.objectContaining({
        subject: DRAFT.subject,
        bodyPlain: DRAFT.bodyPlain,
        bodyHtml: DRAFT.bodyHtml,
      }),
    });
  });

  it("omits bodyHtml from the request when the draft has no HTML body (null branch)", async () => {
    mockPreviewResolved();
    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, { ...DRAFT, bodyHtml: null }, {}),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(result.current.status).toBe("live"));
    const sentRequest = previewRequestOf(0);
    // The store's preview() maps `bodyHtml ?? undefined`; with a null draft
    // the field is omitted from the outgoing request.
    expect(sentRequest).toEqual(
      expect.objectContaining({ subject: DRAFT.subject }),
    );
    expect(sentRequest.bodyHtml ?? undefined).toBeUndefined();
  });

  it("forwards sample vars when present and omits them when empty", async () => {
    mockPreviewResolved();
    const { result, rerender } = renderHook(
      ({ vars }: { vars: Record<string, string> }) =>
        useMailTemplatePreview(KEY, DRAFT, vars, { debounceMs: 0 }),
      {
        wrapper: createQueryWrapper(),
        initialProps: { vars: {} as Record<string, string> },
      },
    );

    await waitFor(() => expect(result.current.status).toBe("live"));
    expect(previewRequestOf(0).sampleVars).toBeUndefined();

    rerender({ vars: { username: "Bob" } });
    await waitFor(() => {
      const lastReq = previewRequestOfLast();
      expect(lastReq.sampleVars).toEqual({ username: "Bob" });
    });
  });

  it("goes to error and surfaces the axios response.data.message", async () => {
    mockPreviewRejected(
      makeAxiosError("Request failed", "Malformed mustache token"),
    );
    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, DRAFT, {}),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error).toBe("Malformed mustache token");
  });

  it("falls back to axios error.message when response has no message", async () => {
    mockPreviewRejected(makeAxiosError("Network Error"));
    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, DRAFT, {}),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error).toBe("Network Error");
  });

  it("surfaces a plain Error's message", async () => {
    mockPreviewRejected(new Error("boom"));
    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, DRAFT, {}),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error).toBe("boom");
  });

  it("uses the generic fallback message for a non-Error rejection", async () => {
    mockPreviewRejected("just a string");
    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, DRAFT, {}),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error).toBe("Preview failed.");
  });

  it("refresh() triggers an immediate render bypassing the debounce", async () => {
    mockPreviewResolved();
    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, DRAFT, {}, { debounceMs: 500 }),
      { wrapper: createQueryWrapper() },
    );

    await waitFor(() => expect(result.current.status).toBe("live"));
    const callsAfterMount = vi.mocked(
      apiConfig.adminEmailTemplatesApi.previewMailTemplate,
    ).mock.calls.length;

    await act(async () => {
      result.current.refresh();
    });

    await waitFor(() =>
      expect(
        vi.mocked(apiConfig.adminEmailTemplatesApi.previewMailTemplate).mock
          .calls.length,
      ).toBeGreaterThan(callsAfterMount),
    );
  });

  it("enters stale while the debounce timer is pending after an edit", async () => {
    vi.useFakeTimers();
    mockPreviewResolved();
    const { result, rerender } = renderHook(
      ({ draft }: { draft: PreviewDraft }) =>
        useMailTemplatePreview(KEY, draft, {}, { debounceMs: 500 }),
      { initialProps: { draft: DRAFT }, wrapper: createQueryWrapper() },
    );

    // The mount-time debounced key equals the initial draft, so the first
    // request fires synchronously; flush its microtasks to settle "live".
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.status).toBe("live");

    // Edit the draft — debounce starts counting; status flips to "stale".
    await act(async () => {
      rerender({ draft: { ...DRAFT, subject: "Reset your password!" } });
    });
    expect(result.current.status).toBe("stale");

    // Let the debounce elapse → a fresh request fires and settles "live".
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });
    expect(result.current.status).toBe("live");
  });

  it("discards an out-of-order (stale) response", async () => {
    // First call resolves slowly, second resolves fast; the slow one must be
    // dropped because the request-id counter has advanced past it.
    let resolveSlow!: (v: { data: MailTemplatePreviewResponse }) => void;
    const slow = new Promise<{ data: MailTemplatePreviewResponse }>((res) => {
      resolveSlow = res;
    });
    const fastResult = {
      ...RESULT,
      subject: "FAST",
    } as MailTemplatePreviewResponse;

    vi.mocked(apiConfig.adminEmailTemplatesApi.previewMailTemplate)
      .mockReturnValueOnce(
        slow as ReturnType<
          typeof apiConfig.adminEmailTemplatesApi.previewMailTemplate
        >,
      )
      .mockResolvedValueOnce({ data: fastResult } as Awaited<
        ReturnType<typeof apiConfig.adminEmailTemplatesApi.previewMailTemplate>
      >);

    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, DRAFT, {}),
      { wrapper: createQueryWrapper() },
    );

    // Mount fires request #1 (the slow one). Trigger request #2 via refresh.
    await act(async () => {
      result.current.refresh();
    });
    await waitFor(() => expect(result.current.result?.subject).toBe("FAST"));

    // Now resolve the slow request #1 — it must NOT overwrite the fast result.
    await act(async () => {
      resolveSlow({ data: { ...RESULT, subject: "SLOW" } });
      await Promise.resolve();
    });
    expect(result.current.result?.subject).toBe("FAST");
  });

  it("discards a stale error response from a superseded request", async () => {
    let rejectSlow!: (e: unknown) => void;
    const slow = new Promise<{ data: MailTemplatePreviewResponse }>(
      (_res, rej) => {
        rejectSlow = rej;
      },
    );
    vi.mocked(apiConfig.adminEmailTemplatesApi.previewMailTemplate)
      .mockReturnValueOnce(
        slow as ReturnType<
          typeof apiConfig.adminEmailTemplatesApi.previewMailTemplate
        >,
      )
      .mockResolvedValueOnce({
        data: { ...RESULT, subject: "FAST" } as MailTemplatePreviewResponse,
      } as Awaited<
        ReturnType<typeof apiConfig.adminEmailTemplatesApi.previewMailTemplate>
      >);

    const { result } = renderHook(
      () => useMailTemplatePreview(KEY, DRAFT, {}),
      { wrapper: createQueryWrapper() },
    );

    await act(async () => {
      result.current.refresh();
    });
    await waitFor(() => expect(result.current.status).toBe("live"));

    // The superseded slow request now rejects — the error branch must
    // short-circuit on the request-id mismatch and leave status "live".
    await act(async () => {
      rejectSlow(new Error("late failure"));
      await Promise.resolve();
    });
    expect(result.current.status).toBe("live");
    expect(result.current.error).toBeNull();
  });
});
