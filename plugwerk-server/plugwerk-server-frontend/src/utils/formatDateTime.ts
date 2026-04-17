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

export interface FormatOptions {
  /** IANA timezone identifier. Falls back to the browser's local timezone when undefined. */
  readonly timezone?: string;
  /** BCP 47 locale tag. Defaults to `en` when undefined. */
  readonly locale?: string;
}

const DEFAULT_LOCALE = "en";

function buildParts(d: Date, opts: FormatOptions | undefined) {
  const formatter = new Intl.DateTimeFormat(opts?.locale ?? DEFAULT_LOCALE, {
    timeZone: opts?.timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
  const parts = formatter.formatToParts(d);
  const find = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((p) => p.type === type)?.value ?? "";
  return {
    day: find("day"),
    month: find("month"),
    year: find("year"),
    // Some locales render midnight as "24" under hour:"2-digit"; normalize to "00".
    hour: find("hour") === "24" ? "00" : find("hour"),
    minute: find("minute"),
    second: find("second"),
  };
}

/**
 * Formats a date string as `dd.MM.yyyy HH:mm:ss` (European format with time),
 * rendered in the given timezone. Falls back to the browser's local timezone
 * when no timezone is supplied.
 */
export function formatDateTime(
  dateStr: string | undefined,
  options?: FormatOptions,
): string {
  if (!dateStr) return "—";
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return "—";
  const p = buildParts(d, options);
  return `${p.day}.${p.month}.${p.year} ${p.hour}:${p.minute}:${p.second}`;
}

/**
 * Formats a date string as a human-readable relative time (e.g. "5m ago", "2d ago").
 * Relative deltas are timezone-independent; `options` is accepted only for API
 * symmetry with {@link formatDateTime}.
 */
export function formatRelativeTime(
  dateStr: string | undefined,
  _options?: FormatOptions,
): string {
  if (!dateStr) return "—";
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return "—";
  const diff = Date.now() - d.getTime();
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  const weeks = Math.floor(days / 7);
  if (weeks < 5) return `${weeks}w ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months}mo ago`;
  return `${Math.floor(months / 12)}y ago`;
}
