// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, type Locator, type Page } from "@playwright/test";

/** Page object for `/namespaces/:namespace/plugins/:pluginId`. */
export class PluginDetailPage {
  static readonly urlPattern = /\/namespaces\/[^/]+\/plugins\/[^/]+$/;

  readonly page: Page;
  readonly overviewTab: Locator;
  readonly versionsTab: Locator;
  readonly changelogTab: Locator;
  readonly dependenciesTab: Locator;

  constructor(page: Page) {
    this.page = page;
    this.overviewTab = page.getByRole("tab", { name: "Overview" });
    // The Versions tab label carries a release-count badge, so match loosely.
    this.versionsTab = page.getByRole("tab", { name: /Versions/ });
    this.changelogTab = page.getByRole("tab", { name: "Changelog" });
    this.dependenciesTab = page.getByRole("tab", { name: "Dependencies" });
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(PluginDetailPage.urlPattern);
    await expect(this.overviewTab).toBeVisible();
  }

  async openVersionsTab(): Promise<void> {
    await this.versionsTab.click();
  }

  /**
   * A version entry on the Versions tab, addressed by its SemVer string. The
   * table renders each release as a `v<version>` badge inside the DataTable.
   */
  versionEntry(version: string): Locator {
    return this.page
      .getByRole("table")
      .getByText(`v${version}`, { exact: true });
  }
}
