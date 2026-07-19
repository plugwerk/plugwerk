// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/LoginPage";
import { CatalogPage } from "../pages/CatalogPage";
import { TopNav } from "../pages/TopNav";
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  ANONYMOUS_STATE,
} from "../fixtures/config";
import { adminToken, createInternalUser } from "../fixtures/api";

test.describe("authentication — unauthenticated flows", () => {
  // These specs must start with no session so they see the login page.
  test.use({ storageState: ANONYMOUS_STATE });

  test("valid credentials land the user on the catalog", async ({ page }) => {
    const login = new LoginPage(page);
    await login.goto();
    await login.login(ADMIN_USERNAME, ADMIN_PASSWORD);

    await new CatalogPage(page).expectLoaded();
  });

  test("invalid credentials show an error and stay on the login page", async ({
    page,
  }) => {
    const login = new LoginPage(page);
    await login.goto();
    await login.login(ADMIN_USERNAME, "definitely-the-wrong-password");

    await expect(login.errorAlert).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test("a user flagged passwordChangeRequired is forced to /change-password", async ({
    page,
    request,
  }) => {
    const token = await adminToken(request);
    const user = await createInternalUser(request, token);

    const login = new LoginPage(page);
    await login.goto();
    await login.login(user.username, user.password);

    await expect(page).toHaveURL(/\/change-password/);
  });

  test("logout returns the user to the login page", async ({ page }) => {
    const login = new LoginPage(page);
    await login.goto();
    await login.login(ADMIN_USERNAME, ADMIN_PASSWORD);
    await new CatalogPage(page).expectLoaded();

    await new TopNav(page).logout();

    await expect(page).toHaveURL(/\/login/);
  });
});
