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
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  computeOffset,
  getTimezoneOptions,
  timezoneAbbreviation,
} from "./timezones";

// A fixed instant so DST-dependent offsets stay deterministic across runs.
const WINTER = new Date("2026-01-15T12:00:00Z");

describe("computeOffset", () => {
  it("returns +00:00 for UTC", () => {
    expect(computeOffset("UTC", WINTER)).toBe("+00:00");
  });

  it("returns a positive padded offset for a positive zone", () => {
    expect(computeOffset("Europe/Berlin", WINTER)).toBe("+01:00");
  });

  it("returns a negative offset for a western zone", () => {
    expect(computeOffset("America/New_York", WINTER)).toBe("-05:00");
  });

  it("renders half-hour offsets with minutes", () => {
    expect(computeOffset("Asia/Kolkata", WINTER)).toBe("+05:30");
  });

  it("falls back to +00:00 for an unknown identifier", () => {
    expect(computeOffset("Not/AZone", WINTER)).toBe("+00:00");
  });
});

describe("timezoneAbbreviation", () => {
  it("returns the short name for a real zone", () => {
    expect(timezoneAbbreviation("UTC", WINTER)).toBe("UTC");
  });

  it("echoes the identifier back when the zone is invalid", () => {
    expect(timezoneAbbreviation("Bogus/Zone", WINTER)).toBe("Bogus/Zone");
  });
});

describe("getTimezoneOptions", () => {
  it("enriches each id with region, city, offset and a label", () => {
    const options = getTimezoneOptions(WINTER);
    const berlin = options.find((o) => o.id === "Europe/Berlin");
    expect(berlin).toBeDefined();
    expect(berlin!.region).toBe("Europe");
    expect(berlin!.city).toBe("Berlin");
    expect(berlin!.label).toBe("Europe/Berlin (UTC+01:00)");
  });

  it("treats slash-less ids (UTC) as region Etc with the id as the city", () => {
    const options = getTimezoneOptions(WINTER);
    const utc = options.find((o) => o.id === "UTC");
    expect(utc).toBeDefined();
    expect(utc!.region).toBe("Etc");
    expect(utc!.city).toBe("UTC");
  });

  it("always surfaces the explicitly-included UTC / Etc zones", () => {
    const ids = getTimezoneOptions(WINTER).map((o) => o.id);
    expect(ids).toContain("UTC");
    expect(ids).toContain("Etc/UTC");
    expect(ids).toContain("Etc/GMT");
  });

  it("returns ids sorted alphabetically", () => {
    const ids = getTimezoneOptions(WINTER).map((o) => o.id);
    const sorted = [...ids].sort((a, b) => a.localeCompare(b));
    expect(ids).toEqual(sorted);
  });

  it("formats multi-segment cities by replacing underscores and slashes", () => {
    const options = getTimezoneOptions(WINTER);
    const ba = options.find((o) => o.id === "America/Argentina/Buenos_Aires");
    // Only present in the fallback list, but most runtimes expose it too.
    if (ba) {
      expect(ba.city).toBe("Argentina / Buenos Aires");
    }
  });
});

describe("getTimezoneOptions — runtime capability fallbacks", () => {
  // Cast to a shape where `supportedValuesOf` is optional so it can be deleted;
  // the lib's `typeof Intl` declares it required, which blocks `delete`.
  const intl = Intl as unknown as {
    supportedValuesOf?: (key: "timeZone") => string[];
  };
  const original = intl.supportedValuesOf;

  afterEach(() => {
    if (original) {
      intl.supportedValuesOf = original;
    } else {
      delete intl.supportedValuesOf;
    }
  });

  it("uses the bundled fallback list when supportedValuesOf is absent", () => {
    delete intl.supportedValuesOf;
    const ids = getTimezoneOptions(WINTER).map((o) => o.id);
    expect(ids).toContain("Europe/Berlin");
    expect(ids).toContain("UTC");
  });

  it("uses the bundled fallback list when supportedValuesOf throws", () => {
    intl.supportedValuesOf = vi.fn(() => {
      throw new Error("not supported");
    });
    const ids = getTimezoneOptions(WINTER).map((o) => o.id);
    expect(ids).toContain("Asia/Tokyo");
    expect(intl.supportedValuesOf).toHaveBeenCalled();
  });
});
