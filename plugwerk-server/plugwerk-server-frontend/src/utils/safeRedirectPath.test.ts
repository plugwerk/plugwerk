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
import { describe, it, expect } from "vitest";
import { safeRedirectPath } from "./safeRedirectPath";

describe("safeRedirectPath", () => {
  describe("returns the path unchanged for safe same-origin inputs", () => {
    it.each([
      ["/", "/"],
      ["/foo", "/foo"],
      ["/foo/bar", "/foo/bar"],
      ["/admin/users", "/admin/users"],
      ["/foo?baz=1&qux=2", "/foo?baz=1&qux=2"],
      ["/foo#hash", "/foo#hash"],
      ["/foo?bar=1#hash", "/foo?bar=1#hash"],
      ["/namespaces/acme/plugins", "/namespaces/acme/plugins"],
    ])("preserves %s", (input, expected) => {
      expect(safeRedirectPath(input)).toBe(expected);
    });
  });

  describe("falls back to / for null, undefined, empty, or non-string inputs", () => {
    it.each([[null], [undefined], [""]])("rejects %s", (input) => {
      expect(safeRedirectPath(input)).toBe("/");
    });
  });

  describe("falls back to / for protocol-relative or external URLs (open-redirect guard)", () => {
    it.each([
      // Protocol-relative — react-router's navigate() would normalise this and may
      // jump to https://evil.com in some browsers. This is the original TS-004 vector.
      ["//evil.com"],
      ["///evil.com"],
      ["//evil.com/legit-looking-path"],
      // Backslash tricks — some browsers treat \ as / in URL parsing
      ["/\\evil.com"],
      ["\\/evil.com"],
      ["\\\\evil.com"],
      // Absolute external URLs
      ["https://evil.com"],
      ["http://evil.com"],
      ["https://evil.com/legit-looking-path"],
      ["http://evil.com:8080/foo"],
      // Other schemes
      ["javascript:alert(1)"],
      ["data:text/html,<script>alert(1)</script>"],
      ["file:///etc/passwd"],
      // Relative paths without leading / — could be interpreted relative to /login
      ["foo"],
      ["./foo"],
      ["../foo"],
    ])("rejects %s", (input) => {
      expect(safeRedirectPath(input)).toBe("/");
    });
  });
});
