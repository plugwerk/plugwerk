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

// Minimal fallback list if Intl.supportedValuesOf('timeZone') is not available.
// Covers the most common IANA identifiers across all regions.
const FALLBACK_TIMEZONES: readonly string[] = [
  "UTC",
  "Africa/Cairo",
  "Africa/Johannesburg",
  "Africa/Lagos",
  "Africa/Nairobi",
  "America/Anchorage",
  "America/Argentina/Buenos_Aires",
  "America/Bogota",
  "America/Chicago",
  "America/Denver",
  "America/Halifax",
  "America/Lima",
  "America/Los_Angeles",
  "America/Mexico_City",
  "America/New_York",
  "America/Phoenix",
  "America/Santiago",
  "America/Sao_Paulo",
  "America/St_Johns",
  "America/Toronto",
  "America/Vancouver",
  "Asia/Bangkok",
  "Asia/Dubai",
  "Asia/Hong_Kong",
  "Asia/Jakarta",
  "Asia/Jerusalem",
  "Asia/Karachi",
  "Asia/Kolkata",
  "Asia/Kuala_Lumpur",
  "Asia/Manila",
  "Asia/Seoul",
  "Asia/Shanghai",
  "Asia/Singapore",
  "Asia/Taipei",
  "Asia/Tashkent",
  "Asia/Tehran",
  "Asia/Tokyo",
  "Atlantic/Azores",
  "Atlantic/Reykjavik",
  "Australia/Adelaide",
  "Australia/Brisbane",
  "Australia/Melbourne",
  "Australia/Perth",
  "Australia/Sydney",
  "Europe/Amsterdam",
  "Europe/Athens",
  "Europe/Berlin",
  "Europe/Brussels",
  "Europe/Bucharest",
  "Europe/Dublin",
  "Europe/Helsinki",
  "Europe/Istanbul",
  "Europe/Kyiv",
  "Europe/Lisbon",
  "Europe/London",
  "Europe/Madrid",
  "Europe/Moscow",
  "Europe/Oslo",
  "Europe/Paris",
  "Europe/Prague",
  "Europe/Rome",
  "Europe/Stockholm",
  "Europe/Vienna",
  "Europe/Warsaw",
  "Europe/Zurich",
  "Pacific/Auckland",
  "Pacific/Fiji",
  "Pacific/Honolulu",
] as const;

export interface TimezoneOption {
  readonly id: string; // IANA identifier, e.g. "Europe/Berlin"
  readonly region: string; // First segment, e.g. "Europe"
  readonly city: string; // Remaining, formatted, e.g. "Berlin"
  readonly offset: string; // "+01:00", "-05:30", "+00:00"
  readonly label: string; // "Europe/Berlin (UTC+01:00)"
}

function listAvailableTimezones(): readonly string[] {
  const intlWithSupport = Intl as typeof Intl & {
    supportedValuesOf?: (key: "timeZone") => string[];
  };
  if (typeof intlWithSupport.supportedValuesOf === "function") {
    try {
      return intlWithSupport.supportedValuesOf("timeZone");
    } catch {
      return FALLBACK_TIMEZONES;
    }
  }
  return FALLBACK_TIMEZONES;
}

/**
 * Computes the current UTC offset for the given IANA timezone, expressed as
 * ±HH:mm. Uses `Intl.DateTimeFormat` with the `longOffset` style, which
 * handles daylight saving time automatically.
 *
 * Returns `"+00:00"` as a safe fallback when the identifier is not recognized
 * by the runtime.
 */
export function computeOffset(
  timezone: string,
  now: Date = new Date(),
): string {
  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone: timezone,
      timeZoneName: "longOffset",
    }).formatToParts(now);
    const raw =
      parts.find((p) => p.type === "timeZoneName")?.value ?? "GMT+00:00";
    // "GMT", "GMT+01:00", "GMT-05:30" — normalize to "+00:00" form.
    if (raw === "GMT") return "+00:00";
    const match = raw.match(/GMT([+-])(\d{1,2})(?::(\d{2}))?/);
    if (!match) return "+00:00";
    const sign = match[1];
    const hours = match[2].padStart(2, "0");
    const minutes = (match[3] ?? "00").padStart(2, "0");
    return `${sign}${hours}:${minutes}`;
  } catch {
    return "+00:00";
  }
}

function formatCity(rest: string): string {
  return rest.replaceAll("_", " ").replaceAll("/", " / ");
}

/**
 * Returns every timezone known to the runtime, enriched with region/city/offset
 * metadata and a pre-rendered label. Sorted alphabetically by IANA identifier.
 */
export function getTimezoneOptions(now: Date = new Date()): TimezoneOption[] {
  const ids = [...listAvailableTimezones()].sort((a, b) => a.localeCompare(b));
  return ids.map((id): TimezoneOption => {
    const firstSlash = id.indexOf("/");
    const region = firstSlash === -1 ? "Etc" : id.substring(0, firstSlash);
    const city =
      firstSlash === -1 ? id : formatCity(id.substring(firstSlash + 1));
    const offset = computeOffset(id, now);
    return {
      id,
      region,
      city,
      offset,
      label: `${id} (UTC${offset})`,
    };
  });
}

/** Short timezone abbreviation for the current instant, e.g. "CET", "PST", "UTC". */
export function timezoneAbbreviation(
  timezone: string,
  now: Date = new Date(),
): string {
  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone: timezone,
      timeZoneName: "short",
    }).formatToParts(now);
    return parts.find((p) => p.type === "timeZoneName")?.value ?? timezone;
  } catch {
    return timezone;
  }
}
