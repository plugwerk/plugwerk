// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { type Locator, type Page } from "@playwright/test";

/** Page object for the authenticated top navigation bar (logout / profile). */
export class TopNav {
  readonly page: Page;
  readonly logoutButton: Locator;
  readonly profileLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.logoutButton = page.getByRole("button", { name: "Log out" });
    this.profileLink = page.getByRole("link", { name: "Profile settings" });
  }

  async logout(): Promise<void> {
    await this.logoutButton.click();
  }
}
