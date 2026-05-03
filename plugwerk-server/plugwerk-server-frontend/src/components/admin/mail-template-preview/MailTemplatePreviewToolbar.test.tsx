/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MailTemplatePreviewToolbar } from "./MailTemplatePreviewToolbar";
import { renderWithTheme } from "../../../test/renderWithTheme";

const PLACEHOLDERS = ["username", "resetLink", "expiresAtHuman"];
const VARS = {
  username: "Alice",
  resetLink: "https://example.com/r",
  expiresAtHuman: "in 30 minutes",
};

describe("MailTemplatePreviewToolbar", () => {
  it("renders the Live status badge when status=live", () => {
    renderWithTheme(
      <MailTemplatePreviewToolbar
        status="live"
        sampleVars={VARS}
        onSampleVarsChange={() => {}}
        onRefresh={() => {}}
        placeholders={PLACEHOLDERS}
      />,
    );
    expect(
      screen.getByRole("status", { name: /Preview status: Live/i }),
    ).toBeInTheDocument();
  });

  it("disables the refresh button while syncing", () => {
    renderWithTheme(
      <MailTemplatePreviewToolbar
        status="syncing"
        sampleVars={VARS}
        onSampleVarsChange={() => {}}
        onRefresh={() => {}}
        placeholders={PLACEHOLDERS}
      />,
    );
    expect(
      screen.getByRole("button", { name: /Refresh preview/i }),
    ).toBeDisabled();
  });

  it("calls onRefresh when the refresh button is clicked", async () => {
    const user = userEvent.setup();
    const onRefresh = vi.fn();
    renderWithTheme(
      <MailTemplatePreviewToolbar
        status="live"
        sampleVars={VARS}
        onSampleVarsChange={() => {}}
        onRefresh={onRefresh}
        placeholders={PLACEHOLDERS}
      />,
    );
    await user.click(screen.getByRole("button", { name: /Refresh preview/i }));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it("expanding sample variables and editing one calls onSampleVarsChange with the merged map", async () => {
    const user = userEvent.setup();
    const onSampleVarsChange = vi.fn();
    renderWithTheme(
      <MailTemplatePreviewToolbar
        status="live"
        sampleVars={VARS}
        onSampleVarsChange={onSampleVarsChange}
        onRefresh={() => {}}
        placeholders={PLACEHOLDERS}
      />,
    );
    await user.click(screen.getByRole("button", { name: /Sample variables/i }));
    const usernameField = await screen.findByLabelText("{{username}}");
    // Type a single character so the test is not order-dependent on
    // userEvent.clear() / type() race conditions.
    await user.type(usernameField, "X");
    expect(onSampleVarsChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ username: "AliceX" }),
    );
  });
});
