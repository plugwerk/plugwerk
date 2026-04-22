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
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { decideDownload, setDownloadAllowedHosts } from "./downloadArtifact";

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
