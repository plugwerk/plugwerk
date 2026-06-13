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

import {
  ALLOWED_FIELDS,
  EVENTS,
  INSTALL_TYPES,
  MAX_BODY_BYTES,
  MAX_VERSION_LENGTH,
  UUID_V4,
  VERSION_RE,
  type InstallType,
  type TelemetryEvent,
} from "./constants";

/** A payload that passed every allowlist and field-shape check. */
export interface ValidPayload {
  installId: string;
  version: string;
  installType: InstallType;
  event: TelemetryEvent;
}

export type ValidationResult =
  | { ok: true; payload: ValidPayload }
  | { ok: false; reason: string };

function reject(reason: string): ValidationResult {
  return { ok: false, reason };
}

function isAllowedField(key: string): key is (typeof ALLOWED_FIELDS)[number] {
  return (ALLOWED_FIELDS as readonly string[]).includes(key);
}

/**
 * Validate a raw telemetry request body against the strict zero-PII allowlist.
 *
 * Pure and side-effect free: no logging, no network, no mutation. The caller
 * maps any rejection to HTTP 400. `reason` names the offending field only —
 * never the submitted value — so it is safe to surface without leaking payload
 * data (defense-in-depth, even though the contract is already PII-free).
 *
 * @param rawBody    the request body, exactly as received
 * @param byteLength UTF-8 byte length of `rawBody` (the size cap is on bytes)
 */
export function validatePayload(rawBody: string, byteLength: number): ValidationResult {
  if (byteLength > MAX_BODY_BYTES) {
    return reject("body exceeds size limit");
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(rawBody);
  } catch {
    return reject("malformed JSON");
  }

  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    return reject("body must be a JSON object");
  }

  const body = parsed as Record<string, unknown>;

  // Reject (never strip) any field outside the allowlist.
  for (const key of Object.keys(body)) {
    if (!isAllowedField(key)) {
      return reject("unknown field present");
    }
  }

  const { installId, version, installType, event } = body;

  if (typeof installId !== "string" || !UUID_V4.test(installId)) {
    return reject("installId must be a UUID v4");
  }
  if (
    typeof version !== "string" ||
    version.length === 0 ||
    version.length > MAX_VERSION_LENGTH ||
    !VERSION_RE.test(version)
  ) {
    return reject("version is missing or malformed");
  }
  if (typeof installType !== "string" || !INSTALL_TYPES.includes(installType as InstallType)) {
    return reject("installType is not in the allowed set");
  }
  if (typeof event !== "string" || !EVENTS.includes(event as TelemetryEvent)) {
    return reject("event is not in the allowed set");
  }

  return {
    ok: true,
    payload: {
      installId,
      version,
      installType: installType as InstallType,
      event: event as TelemetryEvent,
    },
  };
}
