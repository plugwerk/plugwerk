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
import { MailTemplatePreviewPane } from "./MailTemplatePreviewPane";
import { renderWithTheme } from "../../../test/renderWithTheme";

const RESULT = {
  subject: "Reset for Alice",
  bodyPlain: "Hi Alice, click https://example.com/reset",
  bodyHtml: '<p>Hi Alice, <a href="https://example.com/reset">reset</a></p>',
  sampleVars: {
    username: "Alice",
    resetLink: "https://example.com/reset",
    expiresAtHuman: "in 30 minutes",
  },
};

describe("MailTemplatePreviewPane", () => {
  it("renders the rendered subject in subject mode", () => {
    renderWithTheme(
      <MailTemplatePreviewPane
        mode="subject"
        result={RESULT}
        status="live"
        error={null}
        minHeight="auto"
        ariaLabel="Subject preview"
      />,
    );
    expect(
      screen.getByRole("region", { name: "Subject preview" }),
    ).toHaveTextContent("Reset for Alice");
  });

  it("renders the rendered plaintext body in a <pre> in plain mode", () => {
    renderWithTheme(
      <MailTemplatePreviewPane
        mode="plain"
        result={RESULT}
        status="live"
        error={null}
        minHeight="300px"
        ariaLabel="Plaintext body preview"
      />,
    );
    const region = screen.getByRole("region", {
      name: "Plaintext body preview",
    });
    expect(region.textContent ?? "").toContain(
      "Hi Alice, click https://example.com/reset",
    );
  });

  it("renders the HTML body inside a sandboxed iframe in html mode", () => {
    renderWithTheme(
      <MailTemplatePreviewPane
        mode="html"
        result={RESULT}
        status="live"
        error={null}
        minHeight="380px"
        ariaLabel="HTML body preview"
      />,
    );
    const iframe = screen.getByTitle("HTML body preview") as HTMLIFrameElement;
    // sandbox="" → most restrictive, no scripts allowed.
    expect(iframe.getAttribute("sandbox")).toBe("");
    expect(iframe.getAttribute("srcdoc") ?? "").toContain("Hi Alice");
  });

  it("renders an error alert when status is error", () => {
    renderWithTheme(
      <MailTemplatePreviewPane
        mode="plain"
        result={null}
        status="error"
        error="undocumented placeholder badVar"
        minHeight="300px"
        ariaLabel="Plaintext body preview"
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      /undocumented placeholder/,
    );
  });

  it("renders a 'Rendering…' placeholder when no result is available yet", () => {
    renderWithTheme(
      <MailTemplatePreviewPane
        mode="plain"
        result={null}
        status="syncing"
        error={null}
        minHeight="300px"
        ariaLabel="Plaintext body preview"
      />,
    );
    expect(
      screen.getByRole("region", { name: "Plaintext body preview" }),
    ).toHaveTextContent(/Rendering…/);
  });

  it("renders a 'No HTML body to preview.' message when html mode has a null bodyHtml", () => {
    renderWithTheme(
      <MailTemplatePreviewPane
        mode="html"
        result={{ ...RESULT, bodyHtml: undefined }}
        status="live"
        error={null}
        minHeight="380px"
        ariaLabel="HTML body preview"
      />,
    );
    expect(
      screen.getByRole("region", { name: "HTML body preview" }),
    ).toHaveTextContent(/No HTML body to preview/);
  });
});
