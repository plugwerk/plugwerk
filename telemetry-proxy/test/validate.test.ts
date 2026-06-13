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
import { MAX_BODY_BYTES, MAX_VERSION_LENGTH } from "../src/constants";
import { validatePayload, type ValidationResult } from "../src/validate";

/** Build a payload object and the (rawBody, byteLength) pair the validator expects. */
function encode(payload: unknown): [string, number] {
  const raw = JSON.stringify(payload);
  return [raw, new TextEncoder().encode(raw).length];
}

function validate(payload: unknown): ValidationResult {
  return validatePayload(...encode(payload));
}

const VALID = {
  installId: "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  version: "1.1.0-SNAPSHOT",
  installType: "docker-compose",
  event: "server_start",
} as const;

describe("validatePayload — happy path", () => {
  it("accepts a fully valid payload", () => {
    const result = validate(VALID);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.payload).toEqual(VALID);
    }
  });

  it("accepts the snapshot version from the VERSION file (lenient semver)", () => {
    const result = validate({ ...VALID, version: "1.1.0-SNAPSHOT" });
    expect(result.ok).toBe(true);
  });

  it("accepts every allowed installType", () => {
    for (const installType of ["docker-compose", "jar", "k8s", "unknown"]) {
      expect(validate({ ...VALID, installType }).ok).toBe(true);
    }
  });

  it("accepts every allowed event", () => {
    for (const event of ["server_start", "heartbeat", "namespace_created", "plugin_published"]) {
      expect(validate({ ...VALID, event }).ok).toBe(true);
    }
  });

  it("accepts a version at exactly the max length", () => {
    const version = "1" + "0".repeat(MAX_VERSION_LENGTH - 1); // length === MAX_VERSION_LENGTH
    expect(version.length).toBe(MAX_VERSION_LENGTH);
    expect(validate({ ...VALID, version }).ok).toBe(true);
  });
});

describe("validatePayload — unknown field rejection", () => {
  it("rejects a payload with one extra field (does not strip it)", () => {
    const result = validate({ ...VALID, hostname: "leaky.internal" });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe("unknown field present");
  });

  it("rejects PII-shaped extras even alongside a valid allowlist", () => {
    for (const extra of [{ ip: "1.2.3.4" }, { email: "a@b.c" }, { namespace: "acme" }, { $ip: "x" }]) {
      expect(validate({ ...VALID, ...extra }).ok).toBe(false);
    }
  });
});

describe("validatePayload — missing fields", () => {
  it("rejects when each required field is absent", () => {
    for (const field of ["installId", "version", "installType", "event"] as const) {
      const partial = { ...VALID };
      delete (partial as Record<string, unknown>)[field];
      expect(validate(partial).ok).toBe(false);
    }
  });
});

describe("validatePayload — installId", () => {
  it("rejects a non-UUID string", () => {
    expect(validate({ ...VALID, installId: "not-a-uuid" }).ok).toBe(false);
  });

  it("rejects a UUID v1 (wrong version nibble)", () => {
    expect(validate({ ...VALID, installId: "3f2504e0-4f89-11d3-9a0c-0305e82c3301" }).ok).toBe(false);
  });

  it("rejects a non-string installId", () => {
    expect(validate({ ...VALID, installId: 12345 }).ok).toBe(false);
  });
});

describe("validatePayload — version", () => {
  it("rejects an empty version", () => {
    expect(validate({ ...VALID, version: "" }).ok).toBe(false);
  });

  it("rejects a version over the length cap", () => {
    expect(validate({ ...VALID, version: "1." + "9".repeat(MAX_VERSION_LENGTH) }).ok).toBe(false);
  });

  it("rejects a version not starting with a digit", () => {
    expect(validate({ ...VALID, version: "v1.0.0" }).ok).toBe(false);
  });

  it("rejects a version with whitespace/control characters", () => {
    expect(validate({ ...VALID, version: "1.0 .0" }).ok).toBe(false);
  });
});

describe("validatePayload — enums", () => {
  it("rejects an installType outside the allowed set", () => {
    expect(validate({ ...VALID, installType: "windows" }).ok).toBe(false);
  });

  it("rejects an event outside the allowed set", () => {
    expect(validate({ ...VALID, event: "user_login" }).ok).toBe(false);
  });
});

describe("validatePayload — body shape and size", () => {
  it("rejects malformed JSON", () => {
    const raw = "{ not json";
    const result = validatePayload(raw, raw.length);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe("malformed JSON");
  });

  it("rejects a JSON array", () => {
    expect(validate([VALID]).ok).toBe(false);
  });

  it("rejects a JSON primitive", () => {
    const raw = "42";
    expect(validatePayload(raw, raw.length).ok).toBe(false);
  });

  it("accepts a body at exactly the byte limit", () => {
    const [raw] = encode(VALID);
    const result = validatePayload(raw, MAX_BODY_BYTES);
    expect(result.ok).toBe(true);
  });

  it("rejects a body one byte over the limit before parsing", () => {
    const [raw] = encode(VALID);
    const result = validatePayload(raw, MAX_BODY_BYTES + 1);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe("body exceeds size limit");
  });

  it("rejects an oversized body even when its JSON would otherwise be valid", () => {
    // A padded-but-syntactically-valid blob: size check fires first, no parse needed.
    const raw = " ".repeat(MAX_BODY_BYTES + 10) + JSON.stringify(VALID);
    const byteLength = new TextEncoder().encode(raw).length;
    expect(validatePayload(raw, byteLength).ok).toBe(false);
  });
});
