// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { type Locator, type Page } from "@playwright/test";

/** Page object for `/profile` (the current user's own settings). */
export class ProfilePage {
  readonly page: Page;
  readonly timezone: Locator;
  readonly saveButton: Locator;

  constructor(page: Page) {
    this.page = page;
    // The timezone control is an MUI Autocomplete labelled "Timezone".
    this.timezone = page.getByRole("combobox", { name: /timezone/i });
    this.saveButton = page.getByRole("button", { name: "Save Changes" });
  }

  async goto(): Promise<void> {
    await this.page.goto("/profile");
  }

  /** The read-only value shown for a labelled info row (e.g. "Username"). */
  infoValue(label: string): Locator {
    return this.page.getByText(label, { exact: true }).locator("..");
  }

  async selectTimezone(zone: string): Promise<void> {
    await this.timezone.click();
    await this.timezone.fill(zone);
    await this.page.getByRole("option", { name: zone }).first().click();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }
}
