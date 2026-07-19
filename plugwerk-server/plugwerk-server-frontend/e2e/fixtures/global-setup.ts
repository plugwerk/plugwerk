// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { request, type FullConfig } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  BASE_URL,
  E2E_NAMESPACE,
  E2E_NAMESPACE_NAME,
  STORAGE_STATE,
} from "./config";

/**
 * Runs once before the suite. Logs in as the bootstrap admin, seeds a
 * deterministic namespace (so authenticated specs land on the catalog rather
 * than onboarding), and persists the resulting session — including the
 * httpOnly `plugwerk_refresh` cookie — to {@link STORAGE_STATE}. Authenticated
 * specs reuse that state; the SPA's `hydrate()` re-mints an access token from
 * the cookie on first load (memory-only tokens, ADR-0027).
 */
async function globalSetup(_config: FullConfig): Promise<void> {
  const ctx = await request.newContext({ baseURL: BASE_URL });
  try {
    const login = await ctx.post("/api/v1/auth/login", {
      data: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD },
    });
    if (!login.ok()) {
      throw new Error(
        `E2E global-setup: admin login failed (${login.status()}). ` +
          `Is the stack up on ${BASE_URL} with PLUGWERK_AUTH_ADMIN_PASSWORD set? ` +
          `Body: ${await login.text()}`,
      );
    }
    const { accessToken } = (await login.json()) as { accessToken: string };
    const authHeaders = { Authorization: `Bearer ${accessToken}` };

    // Seed the namespace; a 409 means a previous run already created it.
    const ns = await ctx.post("/api/v1/namespaces", {
      headers: authHeaders,
      data: {
        slug: E2E_NAMESPACE,
        name: E2E_NAMESPACE_NAME,
        autoApproveReleases: true,
      },
    });
    if (!ns.ok() && ns.status() !== 409) {
      throw new Error(
        `E2E global-setup: namespace seed failed (${ns.status()}): ${await ns.text()}`,
      );
    }

    fs.mkdirSync(path.dirname(STORAGE_STATE), { recursive: true });
    await ctx.storageState({ path: STORAGE_STATE });
  } finally {
    await ctx.dispose();
  }
}

export default globalSetup;
