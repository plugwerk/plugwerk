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
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { refreshAccessToken } from "./refresh";

// After #351 the JWT-`sub` is the plugwerk_user.id UUID and the refresh
// response carries `userId` + `displayName` directly — no client-side JWT
// decode anymore.
const FAKE_USER_ID = "11111111-2222-3333-4444-555555555555";
const FAKE_ACCESS_TOKEN = "hdr.payload.sig";

const SUCCESS_BODY = {
  accessToken: FAKE_ACCESS_TOKEN,
  userId: FAKE_USER_ID,
  displayName: "Alice",
  username: "alice",
  passwordChangeRequired: false,
  isSuperadmin: false,
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("refreshAccessToken — CSRF bootstrap retry", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    // Reset cookies between tests (jsdom persists across describe blocks).
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("returns the auth payload when the first refresh succeeds", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(SUCCESS_BODY));

    const result = await refreshAccessToken();

    expect(result).not.toBeNull();
    expect(result!.accessToken).toBe(FAKE_ACCESS_TOKEN);
    expect(result!.userId).toBe(FAKE_USER_ID);
    expect(result!.username).toBe("alice");
    expect(result!.displayName).toBe("Alice");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("retries once when the first call 401s AFTER jsdom gains an XSRF cookie", async () => {
    // First call: 401. We emulate Spring's Set-Cookie side effect by mutating
    // document.cookie between the two fetch() invocations — exactly what the
    // browser does in production when CsrfFilter writes a fresh XSRF-TOKEN
    // onto its 401 response.
    fetchMock
      .mockImplementationOnce(async () => {
        document.cookie = "XSRF-TOKEN=bootstrap-token; path=/";
        return new Response("", { status: 401 });
      })
      .mockResolvedValueOnce(jsonResponse(SUCCESS_BODY));

    const result = await refreshAccessToken();

    expect(result).not.toBeNull();
    expect(result!.accessToken).toBe(FAKE_ACCESS_TOKEN);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    // Second call must carry the X-XSRF-TOKEN header set from the new cookie.
    const secondCall = fetchMock.mock.calls[1];
    const secondInit = secondCall[1] as RequestInit;
    const headers = secondInit.headers as Record<string, string>;
    expect(headers["X-XSRF-TOKEN"]).toBe("bootstrap-token");
  });

  it("does NOT retry when the XSRF cookie was already in the jar", async () => {
    // The CSRF bootstrap is specifically "no cookie → cookie". If we already had
    // a cookie and still got 401, something else is wrong (e.g. the refresh
    // token is genuinely revoked) and a retry would just burn another request
    // for the same outcome.
    document.cookie = "XSRF-TOKEN=already-present; path=/";
    fetchMock.mockResolvedValueOnce(new Response("", { status: 401 }));

    const result = await refreshAccessToken();

    expect(result).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does NOT retry when the first call fails with a non-401 status", async () => {
    // 5xx / 429 etc. are not CSRF bootstrap signals. Retrying would mask
    // transient backend failures by pretending they were CSRF issues.
    fetchMock.mockImplementationOnce(async () => {
      document.cookie = "XSRF-TOKEN=appeared-anyway; path=/";
      return new Response("", { status: 500 });
    });

    const result = await refreshAccessToken();

    expect(result).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does NOT retry when the first call 401s but no new XSRF cookie appeared", async () => {
    // If Spring did not issue a cookie, retrying the same call will fail the
    // same way. Give up.
    fetchMock.mockResolvedValueOnce(new Response("", { status: 401 }));

    const result = await refreshAccessToken();

    expect(result).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("deduplicates concurrent callers onto a single in-flight promise", async () => {
    let resolveFetch: (value: Response) => void = () => {};
    fetchMock.mockReturnValueOnce(
      new Promise<Response>((resolve) => {
        resolveFetch = resolve;
      }),
    );

    const [a, b] = [refreshAccessToken(), refreshAccessToken()];
    resolveFetch(jsonResponse(SUCCESS_BODY));
    const [aResult, bResult] = await Promise.all([a, b]);

    expect(aResult?.accessToken).toBe(FAKE_ACCESS_TOKEN);
    expect(bResult?.accessToken).toBe(FAKE_ACCESS_TOKEN);
    // Single-flight: two concurrent callers → one network request.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
