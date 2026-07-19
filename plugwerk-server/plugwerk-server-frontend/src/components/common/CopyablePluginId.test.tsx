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
import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import { CopyablePluginId } from "./CopyablePluginId";
import { renderWithTheme } from "../../test/renderWithTheme";

/**
 * `userEvent` installs its own `navigator.clipboard` stub on setup, so — like
 * VersionsTab.test.tsx — we install our own spy and drive the click through
 * `fireEvent` to hit the component's real handler.
 */
let writeText: ReturnType<typeof vi.fn>;

function mockClipboard(impl?: () => Promise<void>) {
  writeText = vi.fn(impl ?? (() => Promise.resolve()));
  Object.defineProperty(navigator, "clipboard", {
    value: { writeText },
    configurable: true,
    writable: true,
  });
}

const copyButton = () => screen.getByRole("button", { name: "Copy plugin ID" });

describe("CopyablePluginId", () => {
  beforeEach(() => {
    mockClipboard();
  });

  it("renders the plugin id", () => {
    renderWithTheme(<CopyablePluginId pluginId="io.example.my-plugin" />);
    expect(screen.getByText("io.example.my-plugin")).toBeInTheDocument();
  });

  it("copies the plugin id to the clipboard and shows the copied icon", async () => {
    const { container } = renderWithTheme(
      <CopyablePluginId pluginId="io.example.my-plugin" />,
    );

    expect(container.querySelector(".lucide-copy")).not.toBeNull();
    expect(container.querySelector(".lucide-check")).toBeNull();

    fireEvent.click(copyButton());

    await waitFor(() =>
      expect(writeText).toHaveBeenCalledWith("io.example.my-plugin"),
    );
    await waitFor(() =>
      expect(container.querySelector(".lucide-check")).not.toBeNull(),
    );
  });

  it("reverts to the copy icon after the 2s timeout", async () => {
    vi.useFakeTimers();
    try {
      const { container } = renderWithTheme(
        <CopyablePluginId pluginId="io.example.my-plugin" />,
      );

      fireEvent.click(copyButton());
      // Flush the awaited clipboard promise so setCopied(true) is applied.
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      expect(container.querySelector(".lucide-check")).not.toBeNull();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000);
      });
      expect(container.querySelector(".lucide-check")).toBeNull();
      expect(container.querySelector(".lucide-copy")).not.toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it("stops the click from propagating to a surrounding clickable row", async () => {
    const onRowClick = vi.fn();
    renderWithTheme(
      <div onClick={onRowClick}>
        <CopyablePluginId pluginId="io.example.my-plugin" />
      </div>,
    );

    fireEvent.click(copyButton());

    await waitFor(() => expect(writeText).toHaveBeenCalledOnce());
    expect(onRowClick).not.toHaveBeenCalled();
  });

  it("swallows clipboard failures without crashing or entering the copied state", async () => {
    mockClipboard(() => Promise.reject(new Error("clipboard blocked")));
    const { container } = renderWithTheme(
      <CopyablePluginId pluginId="io.example.my-plugin" />,
    );

    fireEvent.click(copyButton());

    await waitFor(() => expect(writeText).toHaveBeenCalledOnce());
    // The catch branch means the icon never flips to the check state.
    expect(container.querySelector(".lucide-check")).toBeNull();
    expect(container.querySelector(".lucide-copy")).not.toBeNull();
  });
});
