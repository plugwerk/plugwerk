// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { type APIRequestContext } from "@playwright/test";
import { ADMIN_PASSWORD, ADMIN_USERNAME } from "./config";

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
