// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, test } from "../fixtures/test";
import { CatalogPage } from "../pages/CatalogPage";
import { PluginDetailPage } from "../pages/PluginDetailPage";
import { UploadModal } from "../pages/UploadModal";
import { ALPHA_PLUGIN, E2E_NAMESPACE, MALFORMED_JAR } from "../fixtures/config";
import { adminToken, deleteReleaseIfPresent } from "../fixtures/api";

test.describe("plugin upload", () => {
  test("a valid upload appears in the plugin's versions list", async ({
    page,
    request,
  }) => {
    // Keep the journey idempotent across re-runs / retries: remove the version
    // we are about to publish if a previous attempt already created it.
    const token = await adminToken(request);
    await deleteReleaseIfPresent(
      request,
      token,
      E2E_NAMESPACE,
      ALPHA_PLUGIN.pluginId,
      "2.0.0",
    );

    await page.goto("/");
    await new CatalogPage(page).expectLoaded();

    const modal = new UploadModal(page);
    await modal.open();
    await modal.attach(ALPHA_PLUGIN.v2Jar);
    await modal.submit();

    // The upload runs asynchronously after the modal closes; the summary toast
    // confirms it completed.
    await expect(
      page.getByRole("status").filter({ hasText: "Upload complete" }),
    ).toBeVisible();

    // The new version is now published on the plugin's detail page.
    const detail = new PluginDetailPage(page);
    await page.goto(
      `/namespaces/${E2E_NAMESPACE}/plugins/${ALPHA_PLUGIN.pluginId}`,
    );
    await detail.expectLoaded();
    await detail.openVersionsTab();
    await expect(detail.versionEntry("2.0.0")).toBeVisible();
  });

  test("a malformed archive is rejected with an error", async ({ page }) => {
    await page.goto("/");
    await new CatalogPage(page).expectLoaded();

    const modal = new UploadModal(page);
    await modal.open();
    await modal.attach(MALFORMED_JAR);
    await modal.submit();

    // The server cannot read a descriptor from the bad archive, so the upload
    // fails and the failure surfaces as an error toast.
    await expect(
      page.getByRole("status").filter({ hasText: "Upload failed" }),
    ).toBeVisible();
  });

  // `upload.max_file_size_mb` is a restart-required application setting and its
  // default (100 MB) is impractical to exceed in CI, so a real 413 / "file too
  // large" journey needs a dedicated low-limit stack. Deferred (see #241).
  test.fixme("an oversized upload is rejected with a size error", () => {});
});
