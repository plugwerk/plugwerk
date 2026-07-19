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
import {
  decideDownload,
  downloadArtifact,
  setDownloadAllowedHosts,
} from "./downloadArtifact";
import { useAuthStore } from "../stores/authStore";

/**
 * Tests for the origin / allow-list decision (ADR-0027 / #294 — TS-003). We test
 * `decideDownload` directly rather than `downloadArtifact` to keep the assertions
 * focused on the security invariant without having to mock fetch + DOM + blob APIs.
 */
describe("decideDownload", () => {
  const testOrigin = "http://localhost:3000";

  beforeEach(() => {
    setDownloadAllowedHosts([]);
    Object.defineProperty(window, "location", {
      configurable: true,
      value: new URL(testOrigin),
    });
  });

  afterEach(() => {
    setDownloadAllowedHosts([]);
  });

  it("same-origin absolute URL attaches bearer", () => {
    const d = decideDownload(
      `${testOrigin}/api/v1/namespaces/default/plugins/x/releases/1.0.0/artifact`,
    );
    expect(d.attachBearer).toBe(true);
  });

  it("same-origin relative URL attaches bearer", () => {
    const d = decideDownload(
      "/api/v1/namespaces/default/plugins/x/releases/1.0.0/artifact",
    );
    expect(d.attachBearer).toBe(true);
  });

  it("different origin does NOT attach bearer by default (strict same-origin)", () => {
    const d = decideDownload("https://evil.example.com/steal?token=1");
    expect(d.attachBearer).toBe(false);
    expect(d.resolvedUrl.hostname).toBe("evil.example.com");
  });

  it("different port counts as different origin", () => {
    const d = decideDownload("http://localhost:8080/api/v1/x");
    expect(d.attachBearer).toBe(false);
  });

  it("explicit allow-list match attaches bearer", () => {
    setDownloadAllowedHosts(["cdn.example.com"]);
    const d = decideDownload("https://cdn.example.com/plugins/x.jar");
    expect(d.attachBearer).toBe(true);
  });

  it("allow-list match is case-insensitive", () => {
    setDownloadAllowedHosts(["CDN.Example.com"]);
    const d = decideDownload("https://cdn.example.com/plugins/x.jar");
    expect(d.attachBearer).toBe(true);
  });

  it("unrelated host is still blocked even with allow-list populated", () => {
    setDownloadAllowedHosts(["cdn.example.com"]);
    const d = decideDownload("https://evil.example.com/plugins/x.jar");
    expect(d.attachBearer).toBe(false);
  });
});

/**
 * Tests for `downloadArtifact` itself: the fetch, bearer-header wiring, error
 * translation, and the blob-URL download side effects. These are the security-
 * and UX-critical paths that `decideDownload` alone does not exercise.
 */
describe("downloadArtifact", () => {
  const testOrigin = "http://localhost:3000";
  const fetchMock = vi.fn();
  let clickSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    setDownloadAllowedHosts([]);
    Object.defineProperty(window, "location", {
      configurable: true,
      value: new URL(testOrigin),
    });
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    // jsdom does not implement blob-URL object creation.
    URL.createObjectURL = vi.fn(() => "blob:mock-url");
    URL.revokeObjectURL = vi.fn();
    // Neutralise the anchor click so jsdom does not warn about navigation.
    clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});
    useAuthStore.setState({ accessToken: null });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
    vi.restoreAllMocks();
    setDownloadAllowedHosts([]);
  });

  function okResponse(): Response {
    return {
      ok: true,
      status: 200,
      blob: () => Promise.resolve(new Blob(["artifact-bytes"])),
    } as unknown as Response;
  }

  it("attaches the bearer for a same-origin download when a token is present", async () => {
    useAuthStore.setState({ accessToken: "jwt-123" });
    fetchMock.mockResolvedValue(okResponse());

    await downloadArtifact(`${testOrigin}/api/v1/x/artifact`, "plugin.jar");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer jwt-123");
    expect(init.headers.Accept).toBe("application/octet-stream");
  });

  it("omits the bearer for a cross-origin download even with a token present", async () => {
    useAuthStore.setState({ accessToken: "jwt-123" });
    fetchMock.mockResolvedValue(okResponse());

    await downloadArtifact("https://cdn.other.com/x.jar", "x.jar");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBeUndefined();
  });

  it("omits the bearer for a same-origin download when no token is present", async () => {
    fetchMock.mockResolvedValue(okResponse());

    await downloadArtifact(`${testOrigin}/api/v1/x/artifact`, "plugin.jar");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBeUndefined();
  });

  it("triggers a blob-URL download with the requested filename and cleans up", async () => {
    vi.useFakeTimers();
    fetchMock.mockResolvedValue(okResponse());
    // The anchor click is already stubbed in beforeEach (shared clickSpy).
    const appendSpy = vi.spyOn(document.body, "appendChild");
    const removeSpy = vi.spyOn(document.body, "removeChild");

    await downloadArtifact(`${testOrigin}/api/v1/x/artifact`, "my-plugin.jar");

    const anchor = appendSpy.mock.calls[0][0] as HTMLAnchorElement;
    expect(anchor.tagName).toBe("A");
    expect(anchor.getAttribute("href")).toBe("blob:mock-url");
    expect(anchor.download).toBe("my-plugin.jar");
    expect(clickSpy).toHaveBeenCalledOnce();

    // Cleanup is deferred behind a 100ms timer.
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();
    expect(removeSpy).not.toHaveBeenCalled();
    vi.advanceTimersByTime(100);
    expect(removeSpy).toHaveBeenCalledWith(anchor);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock-url");
  });

  it("throws the server-provided message on a non-ok JSON response", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 403,
      json: () => Promise.resolve({ message: "forbidden: yanked release" }),
    } as unknown as Response);

    await expect(
      downloadArtifact(`${testOrigin}/api/v1/x/artifact`, "x.jar"),
    ).rejects.toThrow("forbidden: yanked release");
  });

  it("throws a status-based message when the error body is not JSON", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 502,
      json: () => Promise.reject(new Error("not json")),
    } as unknown as Response);

    await expect(
      downloadArtifact(`${testOrigin}/api/v1/x/artifact`, "x.jar"),
    ).rejects.toThrow("Download failed (502)");
  });
});
