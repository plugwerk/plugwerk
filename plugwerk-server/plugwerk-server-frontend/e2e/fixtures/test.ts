// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { test as base, expect } from "@playwright/test";
import { ADMIN_PASSWORD, ADMIN_USERNAME } from "./config";

/**
 * Test fixture that starts each test with a **fresh** authenticated admin
 * session.
 *
 * The web-UI refresh token is single-use and rotates on every `/auth/refresh`
 * (ADR-0027), so a single shared `storageState` cannot be reused across tests —
 * the first test consumes it and every later test gets logged out. Instead we
 * mint a new session per test by logging in through the API into the test's own
 * browser-context cookie jar (`context.request` shares cookies with the page),
 * so the SPA hydrates authenticated on first load.
 */
export const test = base.extend({
  page: async ({ page, context }, use) => {
    const res = await context.request.post("/api/v1/auth/login", {
      data: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD },
    });
    if (!res.ok()) {
      throw new Error(
        `E2E auth fixture: admin login failed (${res.status()}): ${await res.text()}`,
      );
    }
    await use(page);
  },
});

export { expect };
