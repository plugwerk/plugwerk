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
import { describe, expect, it } from "vitest";
import { ageBucket, formatBytes } from "./format";

describe("formatBytes", () => {
  it("renders raw bytes below 1 KiB", () => {
    expect(formatBytes(0)).toBe("0 B");
    expect(formatBytes(512)).toBe("512 B");
  });

  it("renders kibibytes below 1 MiB", () => {
    expect(formatBytes(1024)).toBe("1.0 KB");
    expect(formatBytes(1536)).toBe("1.5 KB");
  });

  it("renders mebibytes below 1 GiB", () => {
    expect(formatBytes(1024 * 1024)).toBe("1.0 MB");
    expect(formatBytes(5 * 1024 * 1024)).toBe("5.0 MB");
  });

  it("renders gibibytes at and above 1 GiB", () => {
    expect(formatBytes(1024 * 1024 * 1024)).toBe("1.00 GB");
    expect(formatBytes(3 * 1024 * 1024 * 1024)).toBe("3.00 GB");
  });
});

describe("ageBucket", () => {
  it("classifies anything under a day as fresh", () => {
    expect(ageBucket(0)).toBe("fresh");
    expect(ageBucket(23.9)).toBe("fresh");
  });

  it("classifies one-day-to-one-week as day", () => {
    expect(ageBucket(24)).toBe("day");
    expect(ageBucket(24 * 6)).toBe("day");
  });

  it("classifies one-week-to-one-month as week", () => {
    expect(ageBucket(24 * 7)).toBe("week");
    expect(ageBucket(24 * 29)).toBe("week");
  });

  it("classifies a month or more as older", () => {
    expect(ageBucket(24 * 30)).toBe("older");
    expect(ageBucket(24 * 365)).toBe("older");
  });
});
