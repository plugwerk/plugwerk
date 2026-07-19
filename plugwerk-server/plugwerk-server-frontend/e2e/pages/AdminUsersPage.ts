// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, type Locator, type Page } from "@playwright/test";

/** Page object for `/admin/users` (superadmin user management). */
export class AdminUsersPage {
  readonly page: Page;
  readonly addUserButton: Locator;
  readonly dialog: Locator;

  constructor(page: Page) {
    this.page = page;
    this.addUserButton = page.getByRole("button", { name: "Add User" });
    this.dialog = page.getByRole("dialog");
  }

  async goto(): Promise<void> {
    await this.page.goto("/admin/users");
  }

  /** Opens the Add-User dialog, fills it (username/email/initial password), and submits. */
  async createUser(
    username: string,
    email: string,
    password: string = "InitialPass123!",
  ): Promise<void> {
    await this.addUserButton.click();
    await expect(this.dialog).toBeVisible();
    await this.dialog.getByLabel("Username").fill(username);
    await this.dialog.getByLabel("Email").fill(email);
    await this.dialog.getByLabel("Initial Password").fill(password);
    await this.dialog.getByRole("button", { name: "Create User" }).click();
  }

  userRow(username: string): Locator {
    return this.page.getByRole("row").filter({ hasText: username });
  }

  /** Clicks the row's delete action and confirms the dialog. */
  async deleteUser(username: string): Promise<void> {
    await this.userRow(username)
      .getByRole("button", { name: "Delete" })
      .click();
    await expect(this.dialog).toBeVisible();
    await this.dialog.getByRole("button", { name: "Delete" }).click();
  }
}
