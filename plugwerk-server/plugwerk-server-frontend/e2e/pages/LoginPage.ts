// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { type Locator, type Page } from "@playwright/test";

/** Page object for `/login`. */
export class LoginPage {
  readonly page: Page;
  readonly username: Locator;
  readonly password: Locator;
  readonly submit: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    // Anchored regexes match the MUI field labels (which carry a trailing
    // required "*") while excluding the "Show password" / "Hide password"
    // adornment button, whose accessible name starts with a different word.
    this.username = page.getByLabel(/^Username/);
    this.password = page.getByLabel(/^Password/);
    this.submit = page.getByRole("button", { name: "Sign In" });
    this.errorAlert = page.getByRole("alert");
  }

  async goto(): Promise<void> {
    await this.page.goto("/login");
  }

  async login(username: string, password: string): Promise<void> {
    await this.username.fill(username);
    await this.password.fill(password);
    await this.submit.click();
  }
}
