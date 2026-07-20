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
import { act, screen, waitFor } from "@testing-library/react";
import { renderWithTheme } from "../../../test/renderWithTheme";
import {
  MustacheCodeEditor,
  type MustacheCodeEditorHandle,
} from "./MustacheCodeEditor";

// The page suites stub this component out (CodeMirror is expensive under
// jsdom), so the real editor keeps its direct coverage here: one small suite,
// few mounts, no user-event round-trips.
describe("MustacheCodeEditor", () => {
  const PLACEHOLDERS = ["username", "resetLink"] as const;

  it("renders the doc text inside a labelled group (plain language)", () => {
    renderWithTheme(
      <MustacheCodeEditor
        value="Hello {{username}}, welcome!"
        onChange={() => {}}
        placeholders={PLACEHOLDERS}
        language="plain"
        ariaLabel="Plaintext body"
      />,
    );
    const group = screen.getByRole("group", { name: "Plaintext body" });
    expect(group.textContent ?? "").toContain("Hello");
    expect(group.textContent ?? "").toContain("{{username}}");
  });

  it("renders the html language variant with line-number gutters", () => {
    renderWithTheme(
      <MustacheCodeEditor
        value="<p>Hi {{username}}</p>"
        onChange={() => {}}
        placeholders={PLACEHOLDERS}
        language="html"
        ariaLabel="HTML body"
      />,
    );
    const group = screen.getByRole("group", { name: "HTML body" });
    expect(group.textContent ?? "").toContain("{{username}}");
    // lineNumbers/foldGutter are enabled for html only — the gutter element
    // is the observable difference to the plain variant.
    expect(group.querySelector(".cm-gutters")).not.toBeNull();
  });

  it("insertAtCursor dispatches a change and fires onChange", async () => {
    const onChange = vi.fn();
    const editorRef: { current: MustacheCodeEditorHandle | null } = {
      current: null,
    };
    renderWithTheme(
      <MustacheCodeEditor
        value="Hello "
        onChange={onChange}
        placeholders={PLACEHOLDERS}
        language="plain"
        ariaLabel="Plaintext body"
        editorRef={editorRef}
      />,
    );

    expect(editorRef.current).not.toBeNull();
    act(() => {
      editorRef.current?.insertAtCursor("{{username}}");
    });

    await waitFor(() => {
      // react-codemirror passes (value, viewUpdate) to onChange.
      expect(onChange).toHaveBeenCalledWith(
        expect.stringContaining("{{username}}"),
        expect.anything(),
      );
    });
  });

  it("disabled turns off contenteditable", () => {
    renderWithTheme(
      <MustacheCodeEditor
        value="read only"
        onChange={() => {}}
        placeholders={PLACEHOLDERS}
        language="plain"
        ariaLabel="Plaintext body"
        disabled
      />,
    );
    const group = screen.getByRole("group", { name: "Plaintext body" });
    const content = group.querySelector(".cm-content");
    expect(content?.getAttribute("contenteditable")).toBe("false");
  });
});
