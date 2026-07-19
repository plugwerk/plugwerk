// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { type Locator, type Page } from "@playwright/test";

/**
 * Page object for `/admin/global-settings` (the superadmin application-settings
 * screen). Field labels are derived from the setting key by `formatLabel`
 * (`general.site_name` -> "Site Name", `upload.max_file_size_mb` ->
 * "Max File Size Mb").
 */
export class AdminGlobalSettingsPage {
  readonly page: Page;
  readonly siteName: Locator;
  readonly maxFileSize: Locator;
  readonly saveButton: Locator;
  readonly restartPendingAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.siteName = page.getByLabel("Site Name");
    this.maxFileSize = page.getByLabel("Max File Size Mb");
    this.saveButton = page.getByRole("button", { name: "Save Changes" });
    this.restartPendingAlert = page.getByText(/take effect after a restart/i);
  }

  async goto(): Promise<void> {
    await this.page.goto("/admin/global-settings");
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }
}
