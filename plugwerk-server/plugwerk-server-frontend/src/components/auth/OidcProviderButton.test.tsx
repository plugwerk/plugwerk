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
import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import { OidcProviderButton } from "./OidcProviderButton";
import { renderWithTheme } from "../../test/renderWithTheme";
import type { OidcProviderLoginInfo } from "../../stores/configStore";

function provider(
  overrides: Partial<OidcProviderLoginInfo> = {},
): OidcProviderLoginInfo {
  return {
    id: "11111111-1111-1111-1111-111111111111",
    name: "Test Provider",
    loginUrl: "/oauth2/authorization/11111111-1111-1111-1111-111111111111",
    accountPickerLoginUrl: null,
    accountSwitchHintUrl: null,
    ...overrides,
  };
}

describe("OidcProviderButton (issue #410)", () => {
  it("renders the primary login button with the loginUrl as href", () => {
    renderWithTheme(
      <OidcProviderButton provider={provider({ name: "Google" })} />,
    );

    const primary = screen.getByRole("link", { name: /login with google/i });
    expect(primary).toHaveAttribute(
      "href",
      "/oauth2/authorization/11111111-1111-1111-1111-111111111111",
    );
  });

  it("renders the 'Use a different account' link when accountPickerLoginUrl is set", () => {
    renderWithTheme(
      <OidcProviderButton
        provider={provider({
          name: "Google",
          accountPickerLoginUrl:
            "/oauth2/authorization/11111111-1111-1111-1111-111111111111?prompt=select_account",
        })}
      />,
    );

    const switchLink = screen.getByRole("link", {
      name: /sign in with a different google account/i,
    });
    expect(switchLink).toHaveAttribute(
      "href",
      "/oauth2/authorization/11111111-1111-1111-1111-111111111111?prompt=select_account",
    );
  });

  it("renders the GitHub-style hint with hostname when accountSwitchHintUrl is set", () => {
    renderWithTheme(
      <OidcProviderButton
        provider={provider({
          name: "GitHub",
          accountPickerLoginUrl: null,
          accountSwitchHintUrl: "https://github.com/logout",
        })}
      />,
    );

    // Hint copy is split across DOM nodes ("To switch accounts, sign out
    // at <link>github.com</link> first.") — assert the host link by role
    // + the surrounding text by partial match.
    const hostLink = screen.getByRole("link", { name: /github\.com/i });
    expect(hostLink).toHaveAttribute("href", "https://github.com/logout");
    expect(hostLink).toHaveAttribute("target", "_blank");
    expect(
      screen.getByText(/to switch accounts, sign out at/i),
    ).toBeInTheDocument();
    // No "Use a different account" link in this branch.
    expect(
      screen.queryByRole("link", { name: /sign in with a different/i }),
    ).not.toBeInTheDocument();
  });

  it("renders neither secondary affordance when both fields are null", () => {
    // Defensive shape — backend never sends both null today, but the
    // component must not crash if a future provider type lands without
    // either capability. Primary button still renders.
    renderWithTheme(
      <OidcProviderButton provider={provider({ name: "Mystery" })} />,
    );

    expect(
      screen.getByRole("link", { name: /login with mystery/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /sign in with a different/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/to switch accounts/i)).not.toBeInTheDocument();
  });
});
