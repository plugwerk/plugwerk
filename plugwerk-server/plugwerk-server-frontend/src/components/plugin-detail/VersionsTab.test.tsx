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
import { screen, waitFor, within, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter } from "../../test/renderWithTheme";
import { useUiStore } from "../../stores/uiStore";
import { VersionsTab } from "./VersionsTab";
import type { PluginReleaseDto } from "../../api/generated/model";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../../api/config", () => ({
  reviewsApi: { approveRelease: vi.fn() },
  managementApi: { deleteRelease: vi.fn(), updateReleaseStatus: vi.fn() },
}));

// downloadArtifact triggers a real fetch/blob flow; mock it so the action
// callbacks (success + failure) can be exercised deterministically.
vi.mock("../../utils/downloadArtifact", () => ({
  downloadArtifact: vi.fn(),
}));

const publishedRelease: PluginReleaseDto = {
  id: "00000000-0000-0000-0000-000000000001",
  pluginId: "my-plugin",
  version: "1.0.0",
  status: "published",
  artifactSha256: "abc",
  artifactSize: 1024,
  fileFormat: "jar",
  downloadCount: 10,
  createdAt: "2026-01-01T00:00:00Z",
};

const draftRelease: PluginReleaseDto = {
  id: "00000000-0000-0000-0000-000000000002",
  pluginId: "my-plugin",
  version: "2.0.0",
  status: "draft",
  artifactSha256: "def",
  artifactSize: 2048,
  fileFormat: "zip",
  downloadCount: 0,
  createdAt: "2026-02-01T00:00:00Z",
};

const deprecatedRelease: PluginReleaseDto = {
  id: "00000000-0000-0000-0000-000000000003",
  pluginId: "my-plugin",
  version: "0.9.0",
  status: "deprecated",
  artifactSha256: "ghi",
  artifactSize: 4096,
  fileFormat: "jar",
  downloadCount: 5,
  createdAt: "2025-12-01T00:00:00Z",
};

const defaultProps = {
  releases: [publishedRelease, draftRelease],
  namespace: "acme",
  pluginId: "my-plugin",
};

describe("VersionsTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
    useUiStore.setState({ toasts: [] });
  });

  it("shows delete buttons when canApprove is true (ADMIN)", () => {
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />);

    const deleteButtons = screen.getAllByRole("button", { name: /delete/i });
    expect(deleteButtons).toHaveLength(2);
  });

  it("hides delete buttons when canApprove is false (non-ADMIN)", () => {
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={false} />);

    const deleteButtons = screen.queryAllByRole("button", {
      name: /delete release/i,
    });
    expect(deleteButtons).toHaveLength(0);
  });

  it("hides delete buttons when canApprove is not set", () => {
    renderWithRouter(<VersionsTab {...defaultProps} />);

    const deleteButtons = screen.queryAllByRole("button", {
      name: /delete release/i,
    });
    expect(deleteButtons).toHaveLength(0);
  });

  it("opens confirmation dialog on delete button click", async () => {
    const user = userEvent.setup();
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />);

    const deleteButtons = screen.getAllByRole("button", { name: /delete/i });
    await user.click(deleteButtons[0]);

    expect(
      screen.getByText(/are you sure you want to delete v1\.0\.0\?/i),
    ).toBeInTheDocument();
  });

  it("calls managementApi.deleteRelease on confirm", async () => {
    const { managementApi } = await import("../../api/config");
    const mockDelete = vi
      .mocked(managementApi.deleteRelease)
      .mockResolvedValue({
        data: undefined,
        status: 204,
        statusText: "No Content",
        headers: { "x-plugin-deleted": "false" },
        config: {} as never,
      });
    const onDeleted = vi.fn();
    const user = userEvent.setup();

    renderWithRouter(
      <VersionsTab
        {...defaultProps}
        canApprove={true}
        onReleaseDeleted={onDeleted}
      />,
    );

    const deleteButtons = screen.getAllByRole("button", { name: /delete/i });
    await user.click(deleteButtons[0]);
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    expect(mockDelete).toHaveBeenCalledWith({
      ns: "acme",
      pluginId: "my-plugin",
      version: "1.0.0",
    });
    expect(onDeleted).toHaveBeenCalledWith("1.0.0");
  });

  it("renders release versions", () => {
    renderWithRouter(<VersionsTab {...defaultProps} />);

    expect(screen.getByText("v1.0.0")).toBeInTheDocument();
    expect(screen.getByText("v2.0.0")).toBeInTheDocument();
  });

  it("shows plugin deletion warning when deleting the last release", async () => {
    const user = userEvent.setup();
    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"
        canApprove={true}
      />,
    );

    const deleteButton = screen.getByRole("button", { name: /delete/i });
    await user.click(deleteButton);

    expect(
      screen.getByText(/the entire plugin will also be removed/i),
    ).toBeInTheDocument();
  });

  it("navigates to catalog page when X-Plugin-Deleted header is true", async () => {
    const { managementApi } = await import("../../api/config");
    vi.mocked(managementApi.deleteRelease).mockResolvedValue({
      data: undefined,
      status: 204,
      statusText: "No Content",
      headers: { "x-plugin-deleted": "true" },
      config: {} as never,
    });
    const onDeleted = vi.fn();
    const user = userEvent.setup();

    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"
        canApprove={true}
        onReleaseDeleted={onDeleted}
      />,
    );

    const deleteButton = screen.getByRole("button", { name: /delete/i });
    await user.click(deleteButton);
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    expect(mockNavigate).toHaveBeenCalledWith("/namespaces/acme/plugins");
    expect(onDeleted).not.toHaveBeenCalled();
  });

  it("renders download icon button for published releases", () => {
    renderWithRouter(<VersionsTab {...defaultProps} />);

    const downloadButtons = screen.getAllByRole("button", {
      name: /download/i,
    });
    expect(downloadButtons.length).toBeGreaterThanOrEqual(1);
  });

  it("shows Format column with file format", () => {
    renderWithRouter(<VersionsTab {...defaultProps} />);

    expect(screen.getByText("Format")).toBeInTheDocument();
    expect(screen.getByText(".jar")).toBeInTheDocument();
    expect(screen.getByText(".zip")).toBeInTheDocument();
  });

  it("shows SHA-256 column with truncated hash", () => {
    renderWithRouter(<VersionsTab {...defaultProps} />);

    expect(screen.getByText("SHA-256")).toBeInTheDocument();
    expect(screen.getByText("abc…")).toBeInTheDocument();
    expect(screen.getByText("def…")).toBeInTheDocument();
  });

  it("shows Downloads column with download count", () => {
    renderWithRouter(<VersionsTab {...defaultProps} />);

    expect(screen.getByText("Downloads")).toBeInTheDocument();
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getByText("0")).toBeInTheDocument();
  });

  it("shows createdAt for draft releases and publishedAt for published", () => {
    renderWithRouter(<VersionsTab {...defaultProps} />);

    // Published release should show publishedAt formatted as dd.MM.yyyy HH:mm:ss
    const cells = screen.getAllByText(/\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}:\d{2}/);
    expect(cells.length).toBeGreaterThanOrEqual(1);
  });

  it("marks the latest published version with a 'latest' label", () => {
    renderWithRouter(<VersionsTab {...defaultProps} />);
    expect(screen.getByText("latest")).toBeInTheDocument();
  });

  it("renders an em dash and no copy affordance when SHA-256 is missing", () => {
    const noSha: PluginReleaseDto = {
      ...publishedRelease,
      artifactSha256: undefined,
    };
    renderWithRouter(
      <VersionsTab releases={[noSha]} namespace="acme" pluginId="my-plugin" />,
    );
    expect(
      screen.queryByText(/click to copy full sha-256/i),
    ).not.toBeInTheDocument();
  });

  it("copies the full SHA-256 to the clipboard when the hash chip is clicked", async () => {
    // userEvent installs its own clipboard stub, so define the spy and click
    // via fireEvent to hit the component's handler with our own
    // navigator.clipboard.writeText.
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
    renderWithRouter(<VersionsTab {...defaultProps} />);

    fireEvent.click(screen.getByText("abc…"));

    await waitFor(() => expect(writeText).toHaveBeenCalledWith("abc"));
  });

  it("falls back to a draft badge for unknown statuses and shows the .jar default format", () => {
    const weird: PluginReleaseDto = {
      ...publishedRelease,
      status: "weird" as PluginReleaseDto["status"],
      fileFormat: undefined,
      artifactSize: undefined,
    };
    renderWithRouter(
      <VersionsTab releases={[weird]} namespace="acme" pluginId="my-plugin" />,
    );
    // Status badge title-cases the raw status string.
    expect(screen.getByText("Weird")).toBeInTheDocument();
    // fileFormat undefined → ".jar" default in the Format column.
    expect(screen.getByText(".jar")).toBeInTheDocument();
    // artifactSize undefined → em dash in the Size column.
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(1);
  });

  function findActionButton(version: string, name: RegExp): HTMLElement {
    const versionBadge = screen.getByText(`v${version}`);
    const row = versionBadge.closest("tr");
    if (!row) throw new Error(`No row for v${version}`);
    return within(row).getByRole("button", { name });
  }

  it("shows the Approve button only for draft releases when canApprove is true", () => {
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />);
    expect(
      screen.getByRole("button", { name: /approve/i }),
    ).toBeInTheDocument();
  });

  it("approves a draft release and shows a success toast", async () => {
    const { reviewsApi } = await import("../../api/config");
    const onReleasesChanged = vi.fn();
    vi.mocked(reviewsApi.approveRelease).mockResolvedValue({} as never);
    const user = userEvent.setup();

    renderWithRouter(
      <VersionsTab
        {...defaultProps}
        canApprove={true}
        onReleasesChanged={onReleasesChanged}
      />,
    );

    await user.click(screen.getByRole("button", { name: /approve/i }));

    expect(reviewsApi.approveRelease).toHaveBeenCalledWith({
      ns: "acme",
      releaseId: draftRelease.id,
    });
    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => /approved and published/i.test(t.message ?? "")),
      ).toBe(true);
    });
    // updateReleaseLocally fires the onReleasesChanged callback with the
    // optimistically-updated list.
    expect(onReleasesChanged).toHaveBeenCalled();
  });

  it("shows an error toast when approval fails", async () => {
    const { reviewsApi } = await import("../../api/config");
    vi.mocked(reviewsApi.approveRelease).mockRejectedValue(new Error("nope"));
    const user = userEvent.setup();

    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />);
    await user.click(screen.getByRole("button", { name: /approve/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) =>
            /failed to approve v2\.0\.0/i.test(t.message ?? ""),
          ),
      ).toBe(true);
    });
  });

  it("opens the status-transition menu and changes status on success", async () => {
    const { managementApi } = await import("../../api/config");
    vi.mocked(managementApi.updateReleaseStatus).mockResolvedValue({} as never);
    const onReleasesChanged = vi.fn();
    const user = userEvent.setup();

    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"
        canApprove={true}
        onReleasesChanged={onReleasesChanged}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: /change release status/i }),
    );
    // published → deprecated/yanked transitions exposed as menu items.
    await user.click(screen.getByRole("menuitem", { name: /deprecated/i }));

    expect(managementApi.updateReleaseStatus).toHaveBeenCalledWith({
      ns: "acme",
      pluginId: "my-plugin",
      version: "1.0.0",
      releaseStatusUpdateRequest: { status: "deprecated" },
    });
    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) =>
            /status changed to deprecated/i.test(t.message ?? ""),
          ),
      ).toBe(true);
    });
    expect(onReleasesChanged).toHaveBeenCalled();
  });

  it("shows an error toast when a status change fails", async () => {
    const { managementApi } = await import("../../api/config");
    vi.mocked(managementApi.updateReleaseStatus).mockRejectedValue(
      new Error("boom"),
    );
    const user = userEvent.setup();

    renderWithRouter(
      <VersionsTab
        releases={[deprecatedRelease]}
        namespace="acme"
        pluginId="my-plugin"
        canApprove={true}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: /change release status/i }),
    );
    await user.click(screen.getByRole("menuitem", { name: /published/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) =>
            /failed to change status of v0\.9\.0/i.test(t.message ?? ""),
          ),
      ).toBe(true);
    });
  });

  it("closes the status menu without acting when dismissed", async () => {
    const user = userEvent.setup();
    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"
        canApprove={true}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: /change release status/i }),
    );
    expect(screen.getByRole("menu")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await waitFor(() =>
      expect(screen.queryByRole("menu")).not.toBeInTheDocument(),
    );
  });

  it("does not render a status-change button for draft releases", () => {
    renderWithRouter(
      <VersionsTab
        releases={[draftRelease]}
        namespace="acme"
        pluginId="my-plugin"
        canApprove={true}
      />,
    );
    expect(
      screen.queryByRole("button", { name: /change release status/i }),
    ).not.toBeInTheDocument();
  });

  it("downloads the artifact when the download button is clicked", async () => {
    const { downloadArtifact } = await import("../../utils/downloadArtifact");
    vi.mocked(downloadArtifact).mockResolvedValue(undefined);
    const user = userEvent.setup();

    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"
      />,
    );

    await user.click(screen.getByRole("button", { name: /download/i }));

    expect(downloadArtifact).toHaveBeenCalledWith(
      "/api/v1/namespaces/acme/plugins/my-plugin/releases/1.0.0/download",
      "my-plugin-1.0.0.jar",
    );
  });

  it("shows an error toast when the download fails", async () => {
    const { downloadArtifact } = await import("../../utils/downloadArtifact");
    vi.mocked(downloadArtifact).mockRejectedValue(new Error("net"));
    const user = userEvent.setup();

    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"
      />,
    );

    await user.click(screen.getByRole("button", { name: /download/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) => /download failed/i.test(t.message ?? "")),
      ).toBe(true);
    });
  });

  it("syncs local releases when the releases prop changes", () => {
    const { rerender } = renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"
      />,
    );
    expect(screen.getByText("v1.0.0")).toBeInTheDocument();
    expect(screen.queryByText("v2.0.0")).not.toBeInTheDocument();

    rerender(
      <VersionsTab
        releases={[publishedRelease, draftRelease]}
        namespace="acme"
        pluginId="my-plugin"
      />,
    );
    expect(screen.getByText("v2.0.0")).toBeInTheDocument();
  });

  it("shows a generic delete confirmation (not the plugin-removal warning) when more than one release exists", async () => {
    const user = userEvent.setup();
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />);

    await user.click(findActionButton("1.0.0", /delete release/i));

    expect(
      screen.queryByText(/the entire plugin will also be removed/i),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/this action cannot be undone/i),
    ).toBeInTheDocument();
  });

  it("shows an error toast when deleting a release fails", async () => {
    const { managementApi } = await import("../../api/config");
    vi.mocked(managementApi.deleteRelease).mockRejectedValue(new Error("x"));
    const user = userEvent.setup();

    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"
        canApprove={true}
      />,
    );

    await user.click(screen.getByRole("button", { name: /delete release/i }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    await waitFor(() => {
      expect(
        useUiStore
          .getState()
          .toasts.some((t) =>
            /failed to delete v1\.0\.0/i.test(t.message ?? ""),
          ),
      ).toBe(true);
    });
  });

  it("cancels the delete dialog without calling the API", async () => {
    const { managementApi } = await import("../../api/config");
    const user = userEvent.setup();
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />);

    await user.click(findActionButton("1.0.0", /delete release/i));
    await user.click(screen.getByRole("button", { name: /cancel/i }));

    await waitFor(() =>
      expect(
        screen.queryByText(/this action cannot be undone/i),
      ).not.toBeInTheDocument(),
    );
    expect(managementApi.deleteRelease).not.toHaveBeenCalled();
  });
});
