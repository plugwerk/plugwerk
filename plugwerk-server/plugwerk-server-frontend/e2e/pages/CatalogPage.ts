// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, type Page } from "@playwright/test";

/**
 * Page object for the namespace-scoped catalog at
 * `/namespaces/:namespace/plugins` (the app's post-login landing page).
 */
export class CatalogPage {
  /** URL shape the index route redirects an authenticated user to. */
  static readonly urlPattern = /\/namespaces\/[^/]+\/plugins/;

  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  /** Asserts the browser has landed on a catalog URL. */
  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(CatalogPage.urlPattern);
  }
}
