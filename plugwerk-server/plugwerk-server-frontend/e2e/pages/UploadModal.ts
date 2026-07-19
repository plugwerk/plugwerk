// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, type Locator, type Page } from "@playwright/test";
import path from "node:path";
import { FIXTURE_PLUGINS_DIR } from "../fixtures/config";

/**
 * Page object for the plugin-release upload modal, opened from the top-bar
 * "Upload" button. Files are attached to the dropzone's hidden file input
 * (react-dropzone), which is equivalent to a real drag-and-drop for the app.
 */
export class UploadModal {
  readonly page: Page;
  readonly openButton: Locator;
  readonly dialog: Locator;
  readonly fileInput: Locator;
  readonly submitButton: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openButton = page.getByRole("button", { name: "Upload", exact: true });
    this.dialog = page.getByRole("dialog");
    this.fileInput = this.dialog.locator('input[type="file"]');
    this.submitButton = this.dialog.getByRole("button", {
      name: /^Upload \d* ?Releases?$/,
    });
    this.errorAlert = this.dialog.getByRole("alert");
  }

  async open(): Promise<void> {
    await this.openButton.click();
    await expect(this.dialog).toBeVisible();
  }

  /** Attaches one or more fixture JARs (by file name) to the dropzone input. */
  async attach(jarFileNames: string | string[]): Promise<void> {
    const names = Array.isArray(jarFileNames) ? jarFileNames : [jarFileNames];
    await this.fileInput.setInputFiles(
      names.map((n) => path.join(FIXTURE_PLUGINS_DIR, n)),
    );
  }

  async submit(): Promise<void> {
    await this.submitButton.click();
  }
}
