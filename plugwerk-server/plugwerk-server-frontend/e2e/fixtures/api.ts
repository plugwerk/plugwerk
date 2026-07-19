// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { type APIRequestContext, type APIResponse } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import { ADMIN_PASSWORD, ADMIN_USERNAME, FIXTURE_PLUGINS_DIR } from "./config";

/** Logs in as the bootstrap admin over the REST API and returns the access token. */
export async function adminToken(request: APIRequestContext): Promise<string> {
  const res = await request.post("/api/v1/auth/login", {
    data: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD },
  });
  if (!res.ok()) {
    throw new Error(`admin login failed: ${res.status()} ${await res.text()}`);
  }
  return ((await res.json()) as { accessToken: string }).accessToken;
}

export interface CreatedUser {
  username: string;
  email: string;
  password: string;
}

/**
 * Creates a fresh INTERNAL user via the admin API. Admin-created users always
 * have `passwordChangeRequired = true` (see `UserService.create`), which the
 * forced-password-change journey relies on. The username/email are made unique
 * per call so repeated runs never collide.
 */
export async function createInternalUser(
  request: APIRequestContext,
  token: string,
  overrides: Partial<CreatedUser> = {},
): Promise<CreatedUser> {
  const suffix = Date.now().toString(36);
  const user: CreatedUser = {
    username: overrides.username ?? `e2e-user-${suffix}`,
    email: overrides.email ?? `e2e-user-${suffix}@plugwerk.test`,
    password: overrides.password ?? "ChangeMe123!",
  };
  const res = await request.post("/api/v1/admin/users", {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      username: user.username,
      email: user.email,
      password: user.password,
    },
  });
  if (!res.ok()) {
    throw new Error(`create user failed: ${res.status()} ${await res.text()}`);
  }
  return user;
}

/**
 * Deletes a specific plugin release if it exists, so an upload journey that
 * publishes that version stays idempotent across re-runs and Playwright
 * retries (a fresh CI database would not need this, but retries reuse it).
 */
export async function deleteReleaseIfPresent(
  request: APIRequestContext,
  token: string,
  namespace: string,
  pluginId: string,
  version: string,
): Promise<void> {
  const res = await request.delete(
    `/api/v1/namespaces/${namespace}/plugins/${pluginId}/releases/${version}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!res.ok() && res.status() !== 404) {
    throw new Error(
      `delete release failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/**
 * Uploads a plugin artifact fixture (from {@link FIXTURE_PLUGINS_DIR}) to a
 * namespace via the multipart `plugin-releases` endpoint. Returns the raw
 * response so callers can assert on / tolerate specific statuses (e.g. a 409
 * when re-seeding). The plugin metadata is read server-side from the archive's
 * MANIFEST.MF, so the caller only supplies the file name.
 */
export async function uploadReleaseFromFile(
  request: APIRequestContext,
  token: string,
  namespace: string,
  jarFileName: string,
): Promise<APIResponse> {
  const jarPath = path.join(FIXTURE_PLUGINS_DIR, jarFileName);
  return request.post(`/api/v1/namespaces/${namespace}/plugin-releases`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      artifact: {
        name: jarFileName,
        mimeType: "application/java-archive",
        buffer: fs.readFileSync(jarPath),
      },
    },
  });
}
