// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, test } from "../fixtures/test";
import { CatalogPage } from "../pages/CatalogPage";
import { ALPHA_PLUGIN, BETA_PLUGIN } from "../fixtures/config";

test.describe("catalog", () => {
  test("lists the seeded plugins", async ({ page }) => {
    const catalog = new CatalogPage(page);
    await catalog.goto();
    await catalog.expectLoaded();

    await expect(catalog.pluginCard(ALPHA_PLUGIN.name)).toBeVisible();
    await expect(catalog.pluginCard(BETA_PLUGIN.name)).toBeVisible();
  });

  test("filtering by tag narrows the list to matching plugins", async ({
    page,
  }) => {
    const catalog = new CatalogPage(page);
    await catalog.goto();
    await catalog.expectLoaded();
    await expect(catalog.pluginCard(BETA_PLUGIN.name)).toBeVisible();

    await catalog.filterByTag(ALPHA_PLUGIN.tag);

    await expect(catalog.pluginCard(ALPHA_PLUGIN.name)).toBeVisible();
    await expect(catalog.pluginCard(BETA_PLUGIN.name)).toHaveCount(0);
  });
});
