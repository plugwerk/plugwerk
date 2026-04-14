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
import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter } from "../../test/renderWithTheme";
import { UploadModal } from "./UploadModal";
import { useUiStore } from "../../stores/uiStore";
import { useAuthStore } from "../../stores/authStore";
import { useUploadStore } from "../../stores/uploadStore";
import * as apiConfig from "../../api/config";

vi.mock("../../api/config", () => ({
  axiosInstance: {
    post: vi.fn(),
    get: vi
      .fn()
      .mockResolvedValue({ data: { upload: { maxFileSizeMb: 100 } } }),
  },
  catalogApi: {
    listPlugins: vi.fn().mockResolvedValue({
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        page: 0,
        size: 24,
      },
    }),
  },
}));

describe("UploadModal", () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: "token",
      username: "alice",
      isAuthenticated: true,
      namespace: "acme",
    });
    useUiStore.setState({ uploadModalOpen: false });
    useUploadStore.getState().reset();
    vi.mocked(apiConfig.axiosInstance.post).mockReset();
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: { upload: { maxFileSizeMb: 100 } },
    });
  });

  it("is not visible when uploadModalOpen is false", () => {
    renderWithRouter(<UploadModal />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("is visible when uploadModalOpen is true", () => {
    useUiStore.setState({ uploadModalOpen: true });
    renderWithRouter(<UploadModal />);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText(/upload plugin release/i)).toBeInTheDocument();
  });

  it("closes when Cancel is clicked", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    const user = userEvent.setup();
    renderWithRouter(<UploadModal />);
    await user.click(screen.getByRole("button", { name: /cancel/i }));
    expect(useUiStore.getState().uploadModalOpen).toBe(false);
  });

  it("closes when X button is clicked", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    const user = userEvent.setup();
    renderWithRouter(<UploadModal />);
    await user.click(screen.getByRole("button", { name: /close dialog/i }));
    expect(useUiStore.getState().uploadModalOpen).toBe(false);
  });

  it("Upload button is disabled when no file is selected", () => {
    useUiStore.setState({ uploadModalOpen: true });
    renderWithRouter(<UploadModal />);
    expect(
      screen.getByRole("button", { name: /upload release/i }),
    ).toBeDisabled();
  });

  it("shows selected file in the list after drop", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    const user = userEvent.setup();
    renderWithRouter(<UploadModal />);

    const file = new File(["fake-jar"], "plugin.jar", {
      type: "application/java-archive",
    });
    await user.upload(
      screen.getByLabelText(/select plugin jar or zip files/i),
      file,
    );

    expect(screen.getByText("plugin.jar")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /upload release/i }),
    ).toBeEnabled();
  });

  it("closes modal immediately on upload click and delegates to hook", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    vi.mocked(apiConfig.axiosInstance.post).mockResolvedValue({ data: {} });

    const user = userEvent.setup();
    renderWithRouter(<UploadModal />);

    const file = new File(["fake-jar"], "plugin.jar", {
      type: "application/java-archive",
    });
    await user.upload(
      screen.getByLabelText(/select plugin jar or zip files/i),
      file,
    );
    await user.click(screen.getByRole("button", { name: /upload release/i }));

    await waitFor(
      () => {
        expect(useUiStore.getState().uploadModalOpen).toBe(false);
      },
      { timeout: 15000 },
    );
  }, 20000);

  it("shows error when file exceeds size limit on upload click", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    const user = userEvent.setup();
    renderWithRouter(<UploadModal />);

    const largeFile = new File(
      [new ArrayBuffer(101 * 1024 * 1024)],
      "huge-plugin.jar",
      {
        type: "application/java-archive",
      },
    );
    await user.upload(
      screen.getByLabelText(/select plugin jar or zip files/i),
      largeFile,
    );
    await user.click(screen.getByRole("button", { name: /upload release/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
      expect(screen.getByText(/files too large/i)).toBeInTheDocument();
    });
  });

  it("displays max size hint in dropzone", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    renderWithRouter(<UploadModal />);

    await waitFor(() => {
      expect(screen.getByText(/max\. 100 mb per file/i)).toBeInTheDocument();
    });
  });

  it("fetches config when modal opens", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    renderWithRouter(<UploadModal />);

    await waitFor(() => {
      expect(vi.mocked(apiConfig.axiosInstance.get)).toHaveBeenCalledWith(
        "/config",
      );
    });
  });

  it("shows multi-file action label when multiple files selected", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    const user = userEvent.setup();
    renderWithRouter(<UploadModal />);

    const files = [
      new File(["jar1"], "plugin-a.jar", { type: "application/java-archive" }),
      new File(["jar2"], "plugin-b.jar", { type: "application/java-archive" }),
    ];
    await user.upload(
      screen.getByLabelText(/select plugin jar or zip files/i),
      files,
    );

    expect(screen.getByText("plugin-a.jar")).toBeInTheDocument();
    expect(screen.getByText("plugin-b.jar")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /upload 2 releases/i }),
    ).toBeEnabled();
  });

  it("allows removing individual files from the list", async () => {
    useUiStore.setState({ uploadModalOpen: true });
    const user = userEvent.setup();
    renderWithRouter(<UploadModal />);

    const files = [
      new File(["jar1"], "alpha.jar", { type: "application/java-archive" }),
      new File(["jar2"], "beta.jar", { type: "application/java-archive" }),
    ];
    await user.upload(
      screen.getByLabelText(/select plugin jar or zip files/i),
      files,
    );
    expect(screen.getByText("alpha.jar")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: /remove alpha\.jar/i }),
    );
    expect(screen.queryByText("alpha.jar")).not.toBeInTheDocument();
    expect(screen.getByText("beta.jar")).toBeInTheDocument();
  });
});
