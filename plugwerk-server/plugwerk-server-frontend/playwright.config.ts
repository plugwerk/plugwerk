// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { defineConfig, devices } from "@playwright/test";
import { BASE_URL, STORAGE_STATE } from "./e2e/fixtures/config";

/**
 * Playwright configuration for the Plugwerk UI E2E suite (issue #241).
 *
 * Tests run against the production frontend bundle served by the backend JAR
 * on {@link BASE_URL} (default http://localhost:8080), backed by a real
 * PostgreSQL — brought up via `docker-compose.e2e.yml` / `scripts/e2e-test.sh`.
 *
 * Phase 1 is Chromium-only for speed (< 5 min budget); Firefox/WebKit are a
 * documented follow-up. `globalSetup` logs in once as admin, seeds a namespace,
 * and persists the session (incl. the httpOnly refresh cookie) to
 * {@link STORAGE_STATE} so authenticated specs skip re-login.
 */
export default defineConfig({
  testDir: "./e2e/tests",
  // Fail the build if a `test.only` is committed.
  forbidOnly: !!process.env.CI,
  fullyParallel: true,
  // One retry in CI to absorb genuine network jitter; none locally so flakes
  // surface immediately. Truly flaky tests must be fixed or `test.fixme()`d.
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI
    ? [["html", { open: "never" }], ["github"], ["list"]]
    : [["html", { open: "never" }], ["list"]],
  globalSetup: "./e2e/fixtures/global-setup.ts",
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        // Authenticated by default; auth specs opt out via
        // `test.use({ storageState: { cookies: [], origins: [] } })`.
        storageState: STORAGE_STATE,
      },
    },
  ],
});
