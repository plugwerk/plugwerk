// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { request, type FullConfig } from "@playwright/test";
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  ALPHA_PLUGIN,
  BASE_URL,
  BETA_PLUGIN,
  E2E_NAMESPACE,
  E2E_NAMESPACE_NAME,
} from "./config";
import { uploadReleaseFromFile } from "./api";

/**
 * Runs once before the suite. Logs in as the bootstrap admin and seeds the
 * deterministic catalog fixtures (a namespace + two tagged plugins) so
 * authenticated specs land on a populated catalog rather than onboarding.
 * Per-test sessions are established by the auth fixture in `test.ts`, not here.
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

    // Seed two plugins with distinct tags (auto-approved → immediately published)
    // so catalog / tag-filter / detail specs have deterministic data. A 409 means
    // a previous run already published this exact version.
    for (const jar of [ALPHA_PLUGIN.v1Jar, BETA_PLUGIN.v1Jar]) {
      const rel = await uploadReleaseFromFile(
        ctx,
        accessToken,
        E2E_NAMESPACE,
        jar,
      );
      if (!rel.ok() && rel.status() !== 409) {
        throw new Error(
          `E2E global-setup: seeding ${jar} failed (${rel.status()}): ${await rel.text()}`,
        );
      }
    }
  } finally {
    await ctx.dispose();
  }
}

export default globalSetup;
