// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { expect, test } from "../fixtures/test";
import { ProfilePage } from "../pages/ProfilePage";
import { ADMIN_USERNAME } from "../fixtures/config";

test("the profile page shows the user and saves preferences", async ({
  page,
}) => {
  const profile = new ProfilePage(page);
  await profile.goto();

  await expect(
    page.getByText(ADMIN_USERNAME, { exact: true }).first(),
  ).toBeVisible();

  await profile.selectTimezone("Europe/Berlin");
  await profile.save();

  await expect(
    page.getByRole("status").filter({ hasText: "Profile settings saved" }),
  ).toBeVisible();
});
