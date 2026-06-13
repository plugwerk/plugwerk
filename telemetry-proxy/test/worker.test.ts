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
const ENV = { POSTHOG_PROJECT_KEY: "phc_test_key", POSTHOG_CAPTURE_URL: "https://capture.test/capture/" };

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
