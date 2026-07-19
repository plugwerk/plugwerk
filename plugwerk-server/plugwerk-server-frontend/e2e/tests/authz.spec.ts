// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { adminToken, createReadyNonAdminUser } from "../fixtures/api";

// Starts unauthenticated so it can log in as a freshly-created non-admin user.
test("a non-admin user is bounced to /403 from admin pages", async ({
  page,
  request,
}) => {
  const token = await adminToken(request);
  const user = await createReadyNonAdminUser(request, token);

  const login = new LoginPage(page);
  await login.goto();
  await login.login(user.username, user.password);

  // A non-admin with no namespace lands on onboarding — proving login worked.
  await expect(page).toHaveURL(/\/onboarding/);

  await page.goto("/admin/global-settings");
  await expect(page).toHaveURL(/\/403/);
});
