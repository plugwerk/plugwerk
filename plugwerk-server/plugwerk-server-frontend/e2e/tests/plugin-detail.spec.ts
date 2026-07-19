// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, test } from "../fixtures/test";
import { CatalogPage } from "../pages/CatalogPage";
import { PluginDetailPage } from "../pages/PluginDetailPage";
import { ALPHA_PLUGIN } from "../fixtures/config";

test("opening a plugin shows its detail page with the info tabs and versions", async ({
  page,
}) => {
  const catalog = new CatalogPage(page);
  await catalog.goto();
  await catalog.expectLoaded();

  await catalog.openPlugin(ALPHA_PLUGIN.name);

  const detail = new PluginDetailPage(page);
  await detail.expectLoaded();
  await expect(detail.versionsTab).toBeVisible();
  await expect(detail.changelogTab).toBeVisible();
  await expect(detail.dependenciesTab).toBeVisible();

  await detail.openVersionsTab();
  await expect(detail.versionEntry("1.0.0")).toBeVisible();
});
