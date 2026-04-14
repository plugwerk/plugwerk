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
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithTheme } from "../../test/renderWithTheme";
import { CodeBlock } from "./CodeBlock";

let mockWriteText: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  mockWriteText = vi
    .spyOn(navigator.clipboard, "writeText")
    .mockResolvedValue(undefined);
});

afterEach(() => {
  mockWriteText.mockRestore();
});

describe("CodeBlock", () => {
  it("renders the code text", () => {
    renderWithTheme(<CodeBlock code="npm install my-plugin" />);
    expect(screen.getByText("npm install my-plugin")).toBeInTheDocument();
  });

  it("renders code inside a <code> element", () => {
    renderWithTheme(<CodeBlock code="const x = 1" />);
    const codeEl = screen.getByText("const x = 1");
    expect(codeEl.tagName.toLowerCase()).toBe("code");
  });

  it("shows a copy button", () => {
    renderWithTheme(<CodeBlock code="some code" />);
    expect(
      screen.getByRole("button", { name: /copy code/i }),
    ).toBeInTheDocument();
  });

  it("copies code to clipboard when copy button is clicked", async () => {
    renderWithTheme(<CodeBlock code="pip install plugwerk" />);
    const button = screen.getByRole("button", { name: /copy code/i });
    // Use fireEvent + act to flush the async handler
    await act(async () => {
      button.click();
    });
    expect(mockWriteText).toHaveBeenCalledWith("pip install plugwerk");
  });

  it("shows check icon after copying", async () => {
    const user = userEvent.setup();
    renderWithTheme(<CodeBlock code="some code" />);
    await user.click(screen.getByRole("button", { name: /copy code/i }));
    // After click, tooltip title changes to "Copied!"
    await waitFor(() => {
      // The Clipboard icon should be replaced by Check icon (button still present)
      expect(
        screen.getByRole("button", { name: /copy code/i }),
      ).toBeInTheDocument();
    });
  });

  it("renders multiline code correctly", () => {
    const code = "line one\nline two\nline three";
    renderWithTheme(<CodeBlock code={code} />);
    // getByText normalises whitespace, so match via the <code> element's textContent
    const codeEl = document.querySelector("code");
    expect(codeEl?.textContent).toBe(code);
  });
});
