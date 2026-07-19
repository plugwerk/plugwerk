// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, test } from "../fixtures/test";
import { AdminGlobalSettingsPage } from "../pages/AdminGlobalSettingsPage";
import { AdminUsersPage } from "../pages/AdminUsersPage";
import { AdminNamespacesPage } from "../pages/AdminNamespacesPage";

test.describe("admin global settings", () => {
  // These mutate the shared settings form, so run them one at a time.
  test.describe.configure({ mode: "serial" });

  test("shows the application settings screen", async ({ page }) => {
    const settings = new AdminGlobalSettingsPage(page);
    await settings.goto();

    await expect(settings.siteName).toBeVisible();
    await expect(settings.maxFileSize).toBeVisible();
    await expect(settings.saveButton).toBeVisible();
  });

  test("changing the site name persists across a reload", async ({ page }) => {
    const settings = new AdminGlobalSettingsPage(page);
    await settings.goto();
    const original = await settings.siteName.inputValue();
    const next = `E2E Hub ${Date.now()}`;

    try {
      await settings.siteName.fill(next);
      await settings.save();
      await expect(
        page.getByRole("status").filter({ hasText: "Settings saved" }),
      ).toBeVisible();

      await page.reload();
      await expect(settings.siteName).toHaveValue(next);
    } finally {
      await settings.goto();
      await settings.siteName.fill(original);
      await settings.save();
    }
  });

  test("changing a restart-required setting shows the restart-pending alert", async ({
    page,
  }) => {
    const settings = new AdminGlobalSettingsPage(page);
    await settings.goto();
    const original = await settings.maxFileSize.inputValue();
    const bumped = String(Number(original || "100") + 7);

    try {
      await settings.maxFileSize.fill(bumped);
      await settings.save();
      await expect(settings.restartPendingAlert).toBeVisible();
    } finally {
      await settings.goto();
      await settings.maxFileSize.fill(original);
      await settings.save();
    }
  });
});

test("an admin can create then delete a user", async ({ page }) => {
  const users = new AdminUsersPage(page);
  await users.goto();
  const username = `e2e-user-${Date.now().toString(36)}`;

  await users.createUser(username, `${username}@plugwerk.test`);
  await expect(
    page.getByRole("status").filter({ hasText: "created" }),
  ).toBeVisible();
  await expect(users.userRow(username)).toBeVisible();

  await users.deleteUser(username);
  await expect(
    page.getByRole("status").filter({ hasText: "deleted" }),
  ).toBeVisible();
  await expect(users.userRow(username)).toHaveCount(0);
});

test("an admin can create then delete a namespace", async ({ page }) => {
  const namespaces = new AdminNamespacesPage(page);
  await namespaces.goto();
  const suffix = Date.now().toString(36);
  const slug = `e2e-ns-${suffix}`;

  await namespaces.createNamespace(slug, `E2E NS ${suffix}`);
  await expect(
    page.getByRole("status").filter({ hasText: "created" }),
  ).toBeVisible();
  await expect(namespaces.namespaceRow(slug)).toBeVisible();

  await namespaces.deleteNamespace(slug);
  await expect(
    page.getByRole("status").filter({ hasText: "deleted" }),
  ).toBeVisible();
  await expect(namespaces.namespaceRow(slug)).toHaveCount(0);
});
