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

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";

const ENDPOINT = "https://telemetry.plugwerk.io/v1/events";

// Mock for the Workers Rate Limiting binding. Reset in beforeEach to allow-all so
// the non-rate-limit suites exercise the forwarding path; the rate limiting suite
// overrides it to simulate the over-limit case.
const limitMock = vi.fn();

const ENV = {
  POSTHOG_PROJECT_KEY: "phc_test_key",
  POSTHOG_CAPTURE_URL: "https://capture.test/capture/",
  RATE_LIMITER: { limit: limitMock } as unknown as RateLimit,
};

const VALID_BODY = JSON.stringify({
  installId: "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  version: "1.1.0-SNAPSHOT",
  installType: "k8s",
  event: "heartbeat",
});

// Minimal ExecutionContext stub; the handler does not use it.
const ctx = { waitUntil() {}, passThroughOnException() {} } as unknown as ExecutionContext;

function post(body: string, headers: Record<string, string> = { "content-type": "application/json" }): Request {
  return new Request(ENDPOINT, { method: "POST", headers, body });
}

function call(request: Request): Promise<Response> {
  // The handler ignores ctx but the runtime signature includes it.
  return (worker.fetch as (r: Request, e: typeof ENV, c: ExecutionContext) => Promise<Response>)(request, ENV, ctx);
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
  limitMock.mockReset();
  limitMock.mockResolvedValue({ success: true });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("routing and method guards", () => {
  it("404s an unknown path", async () => {
    const res = await call(new Request("https://telemetry.plugwerk.io/", { method: "POST" }));
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("405s a non-POST with an Allow header", async () => {
    const res = await call(new Request(ENDPOINT, { method: "GET" }));
    expect(res.status).toBe(405);
    expect(res.headers.get("allow")).toBe("POST");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("415s a wrong content-type", async () => {
    const res = await call(post(VALID_BODY, { "content-type": "text/plain" }));
    expect(res.status).toBe(415);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("accepts application/json with a charset parameter", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    const res = await call(post(VALID_BODY, { "content-type": "application/json; charset=utf-8" }));
    expect(res.status).toBe(204);
  });
});

describe("validation guards", () => {
  it("400s an extra-field payload and never forwards", async () => {
    const body = JSON.stringify({
      installId: "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
      version: "1.0.0",
      installType: "jar",
      event: "server_start",
      hostname: "leaky",
    });
    const res = await call(post(body));
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("400s an oversized body via the content-length pre-read guard", async () => {
    const res = await call(post(VALID_BODY, { "content-type": "application/json", "content-length": "9999" }));
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("forwarding to PostHog", () => {
  it("204s and forwards a correctly mapped event on PostHog 2xx", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ status: 1 }), { status: 200 }));
    const res = await call(post(VALID_BODY));
    expect(res.status).toBe(204);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(ENV.POSTHOG_CAPTURE_URL);
    expect(init.method).toBe("POST");

    const forwarded = JSON.parse(init.body as string);
    expect(forwarded).toEqual({
      api_key: "phc_test_key",
      event: "heartbeat",
      distinct_id: "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
      properties: {
        version: "1.1.0-SNAPSHOT",
        installType: "k8s",
        $process_person_profile: false,
      },
    });
  });

  it("502s when PostHog returns a non-2xx", async () => {
    fetchMock.mockResolvedValue(new Response("nope", { status: 500 }));
    const res = await call(post(VALID_BODY));
    expect(res.status).toBe(502);
  });

  it("502s when the upstream fetch rejects (network error / timeout)", async () => {
    fetchMock.mockRejectedValue(new Error("connection reset"));
    const res = await call(post(VALID_BODY));
    expect(res.status).toBe(502);
  });
});

describe("PostHog project key guard (DEV-54 condition 2)", () => {
  // The go-live security gate requires the write-only `phc_` project key. A personal
  // (`phx_`) or admin key must never be used to forward — it widens a leak's blast
  // radius from capture-only to data-read/admin. The Worker fails closed (502).
  const BAD_KEYS = ["phx_personal_key", "admin_secret", "phc", ""];

  function callWithKey(request: Request, key: string): Promise<Response> {
    const env = { ...ENV, POSTHOG_PROJECT_KEY: key };
    return (worker.fetch as (r: Request, e: typeof env, c: ExecutionContext) => Promise<Response>)(request, env, ctx);
  }

  it.each(BAD_KEYS)("502s and never forwards when the key is %o (not a phc_ key)", async (key) => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const res = await callWithKey(post(VALID_BODY), key);

    expect(res.status).toBe(502);
    expect(fetchMock).not.toHaveBeenCalled();
    errorSpy.mockRestore();
  });

  it("logs a secret-free misconfiguration error (never echoes the key value)", async () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    await callWithKey(post(VALID_BODY), "phx_super_secret_value");

    expect(errorSpy).toHaveBeenCalledTimes(1);
    const logged = String(errorSpy.mock.calls[0]?.[0] ?? "");
    expect(logged).not.toContain("phx_super_secret_value");
    errorSpy.mockRestore();
  });

  it("still 204s with a valid phc_ key", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    const res = await callWithKey(post(VALID_BODY), "phc_valid_project_key");
    expect(res.status).toBe(204);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe("kill switch (PROXY_DISABLED)", () => {
  function callWithEnv(request: Request, extra: Record<string, string>): Promise<Response> {
    const env = { ...ENV, ...extra };
    return (worker.fetch as (r: Request, e: typeof env, c: ExecutionContext) => Promise<Response>)(request, env, ctx);
  }

  it("503s a valid POST and neither meters nor forwards when disabled", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    const res = await callWithEnv(post(VALID_BODY), { PROXY_DISABLED: "true" });
    expect(res.status).toBe(503);
    expect(await res.text()).toBe("");
    expect(fetchMock).not.toHaveBeenCalled();
    expect(limitMock).not.toHaveBeenCalled();
  });

  it("parses the flag case-insensitively and ignoring whitespace", async () => {
    const res = await callWithEnv(post(VALID_BODY), { PROXY_DISABLED: " True " });
    expect(res.status).toBe(503);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("wins over the method guard — a GET on the telemetry path also 503s", async () => {
    const res = await callWithEnv(new Request(ENDPOINT, { method: "GET" }), { PROXY_DISABLED: "true" });
    expect(res.status).toBe(503);
  });

  it("still 404s unknown paths while disabled", async () => {
    const res = await callWithEnv(new Request("https://telemetry.plugwerk.io/", { method: "POST" }), {
      PROXY_DISABLED: "true",
    });
    expect(res.status).toBe(404);
  });

  it.each(["false", "", "1", "yes"])("stays enabled when the flag is %o", async (value) => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    const res = await callWithEnv(post(VALID_BODY), { PROXY_DISABLED: value });
    expect(res.status).toBe(204);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("stays enabled when the var is absent (default)", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    const res = await call(post(VALID_BODY));
    expect(res.status).toBe(204);
  });
});

describe("rate limiting", () => {
  // Regression test for DEV-47 (DEV-33 HIGH gate): the public, unauthenticated
  // endpoint must not amplify floods into per-event-billed PostHog forwards.
  it("429s the over-limit request and does NOT forward to PostHog", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ status: 1 }), { status: 200 }));
    // First two calls allowed, the third is over the limit.
    limitMock
      .mockResolvedValueOnce({ success: true })
      .mockResolvedValueOnce({ success: true })
      .mockResolvedValueOnce({ success: false });

    expect((await call(post(VALID_BODY))).status).toBe(204);
    expect((await call(post(VALID_BODY))).status).toBe(204);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    const throttled = await call(post(VALID_BODY));
    expect(throttled.status).toBe(429);
    // No PostHog forward on throttle — the forward count stays at 2.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("returns 429 with an empty body and a Retry-After header", async () => {
    limitMock.mockResolvedValue({ success: false });
    const res = await call(post(VALID_BODY));
    expect(res.status).toBe(429);
    expect(res.headers.get("retry-after")).toBe("60");
    expect(await res.text()).toBe("");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("meters before reading the body (throttles even an oversized/invalid request)", async () => {
    limitMock.mockResolvedValue({ success: false });
    // Oversized declared length would otherwise 400; rate limiting runs first.
    const res = await call(post(VALID_BODY, { "content-type": "application/json", "content-length": "9999" }));
    expect(res.status).toBe(429);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("keys the limiter by the cf-connecting-ip header", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    await call(post(VALID_BODY, { "content-type": "application/json", "cf-connecting-ip": "203.0.113.7" }));
    expect(limitMock).toHaveBeenCalledWith({ key: "203.0.113.7" });
  });

  it("falls back to an 'unknown' key when cf-connecting-ip is absent", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    await call(post(VALID_BODY));
    expect(limitMock).toHaveBeenCalledWith({ key: "unknown" });
  });

  it("does not meter requests rejected by the path/method/content-type guards", async () => {
    await call(new Request(ENDPOINT, { method: "GET" }));
    await call(post(VALID_BODY, { "content-type": "text/plain" }));
    await call(new Request("https://telemetry.plugwerk.io/", { method: "POST" }));
    expect(limitMock).not.toHaveBeenCalled();
  });
});
