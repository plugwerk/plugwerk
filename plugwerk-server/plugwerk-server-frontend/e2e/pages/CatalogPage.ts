// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, type Locator, type Page } from "@playwright/test";
import { E2E_NAMESPACE } from "../fixtures/config";

/**
 * Page object for the namespace-scoped catalog at
 * `/namespaces/:namespace/plugins` (the app's post-login landing page).
 */
export class CatalogPage {
  /** URL shape the index route redirects an authenticated user to. */
  static readonly urlPattern = /\/namespaces\/[^/]+\/plugins/;

  readonly page: Page;
  readonly heading: Locator;
  readonly tagFilter: Locator;
  readonly emptyState: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole("heading", { name: "Plugin Catalog" });
    // MUI puts the "Filter by tag" aria-label on the Autocomplete wrapper; the
    // actual combobox inside carries the (changing) placeholder as its name, so
    // scope to the wrapper and grab the combobox within.
    this.tagFilter = page
      .locator('[aria-label="Filter by tag"]')
      .getByRole("combobox");
    this.emptyState = page.getByText("No plugins found");
  }

  async goto(namespace: string = E2E_NAMESPACE): Promise<void> {
    await this.page.goto(`/namespaces/${namespace}/plugins`);
  }

  /** Asserts the browser has landed on a catalog URL with the catalog rendered. */
  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(CatalogPage.urlPattern);
    await expect(this.heading).toBeVisible();
  }

  /** A plugin card in the list, addressed by its display name. */
  pluginCard(name: string): Locator {
    return this.page.getByRole("listitem", { name: `${name} plugin` });
  }

  async openPlugin(name: string): Promise<void> {
    await this.pluginCard(name).click();
  }

  /** Selects a tag in the "Filter by tag" autocomplete. */
  async filterByTag(tag: string): Promise<void> {
    await this.tagFilter.click();
    await this.page.getByRole("option", { name: tag }).click();
  }
}
