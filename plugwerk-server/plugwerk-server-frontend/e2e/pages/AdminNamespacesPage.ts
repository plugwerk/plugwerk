// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, type Locator, type Page } from "@playwright/test";

/** Page object for `/admin/namespaces` (superadmin namespace management). */
export class AdminNamespacesPage {
  readonly page: Page;
  readonly createButton: Locator;
  readonly dialog: Locator;

  constructor(page: Page) {
    this.page = page;
    this.createButton = page.getByRole("button", { name: "Create Namespace" });
    this.dialog = page.getByRole("dialog");
  }

  async goto(): Promise<void> {
    await this.page.goto("/admin/namespaces");
  }

  async createNamespace(slug: string, name: string): Promise<void> {
    await this.createButton.click();
    await expect(this.dialog).toBeVisible();
    await this.dialog.getByLabel("Slug").fill(slug);
    await this.dialog.getByLabel("Name").fill(name);
    await this.dialog.getByRole("button", { name: /create/i }).click();
  }

  namespaceRow(slug: string): Locator {
    return this.page.getByRole("row").filter({ hasText: slug });
  }

  /**
   * Deletes a namespace: the confirm dialog requires typing the slug back in
   * before the Delete button becomes actionable.
   */
  async deleteNamespace(slug: string): Promise<void> {
    await this.namespaceRow(slug)
      .getByRole("button", { name: "Delete" })
      .click();
    await expect(this.dialog).toBeVisible();
    await this.dialog.getByRole("textbox").fill(slug);
    await this.dialog.getByRole("button", { name: "Delete" }).click();
  }
}
