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
import { screen, waitFor, fireEvent } from "@testing-library/react";
import { renderWithRouterAt } from "../test/renderWithTheme";
import { CatalogPage } from "./CatalogPage";
import { usePluginStore } from "../stores/pluginStore";
import { useAuthStore } from "../stores/authStore";
import { useUiStore } from "../stores/uiStore";
import { catalogApi } from "../api/config";
import type { PluginDto, PluginPagedResponse } from "../api/generated/model";

vi.mock("../api/config", () => ({
  catalogApi: { listPlugins: vi.fn() },
  axiosInstance: {
    get: vi.fn().mockResolvedValue({ data: [] }),
    post: vi.fn().mockResolvedValue({ data: {} }),
  },
  namespacesApi: { listNamespaces: vi.fn().mockResolvedValue({ data: [] }) },
  namespaceMembersApi: {
    getMyMembership: vi.fn().mockRejectedValue(new Error("not a member")),
  },
  managementApi: {},
  reviewsApi: {},
}));

const mockListPlugins = catalogApi.listPlugins as unknown as ReturnType<
  typeof vi.fn
>;

const mockPlugin: PluginDto = {
  id: "uuid-1",
  pluginId: "auth-plugin",
  name: "Auth Plugin",
  description: "Authentication support.",
  provider: "ACME Corp",
  status: "active",
  latestRelease: {
    id: "rel-1",
    pluginId: "auth-plugin",
    version: "1.0.0",
    status: "published",
  },
  downloadCount: 42,
  tags: ["auth"],
};

const defaultFilters = {
  search: "",
  tag: "",
  status: "",
  version: "",
  sort: "name,asc",
  page: 0,
  size: 24,
};

function makeResponse(
  overrides: Partial<PluginPagedResponse> = {},
): PluginPagedResponse {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    ...overrides,
  } as PluginPagedResponse;
}

describe("CatalogPage", () => {
  const ROUTE_PATH = "/namespaces/:namespace/plugins";
  const INITIAL_PATH = "/namespaces/acme/plugins";

  function renderCatalog() {
    return renderWithRouterAt(<CatalogPage />, ROUTE_PATH, INITIAL_PATH);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    mockListPlugins.mockResolvedValue({ data: makeResponse() });
    useAuthStore.setState({
      accessToken: null,
      namespace: "acme",
      isAuthenticated: false,
      isHydrating: false,
    });
    useUiStore.setState({ searchQuery: "", toasts: [] });
    usePluginStore.setState({ filters: { ...defaultFilters } });
  });

  it("renders the page heading", () => {
    renderCatalog();
    expect(screen.getByText("Plugin Catalog")).toBeInTheDocument();
  });

  it("shows empty state when the API returns no plugins", async () => {
    renderCatalog();
    expect(await screen.findByText("No plugins found")).toBeInTheDocument();
  });

  it("shows loading skeleton on initial load", () => {
    mockListPlugins.mockReturnValue(new Promise(() => {}));
    renderCatalog();
    expect(screen.getByLabelText("Loading plugins")).toBeInTheDocument();
  });

  it("does not show empty state while loading", () => {
    mockListPlugins.mockReturnValue(new Promise(() => {}));
    renderCatalog();
    expect(screen.queryByText("No plugins found")).not.toBeInTheDocument();
  });

  it("shows error alert when the request fails", async () => {
    mockListPlugins.mockRejectedValue(new Error("Network error"));
    renderCatalog();
    expect(await screen.findByText("Network error")).toBeInTheDocument();
  });

  it("does not show empty state when there is an error", async () => {
    mockListPlugins.mockRejectedValue(new Error("Something failed"));
    renderCatalog();
    await screen.findByText("Something failed");
    expect(screen.queryByText("No plugins found")).not.toBeInTheDocument();
  });

  it("renders plugin cards in card view", async () => {
    mockListPlugins.mockResolvedValue({
      data: makeResponse({
        content: [mockPlugin],
        totalElements: 1,
        totalPages: 1,
      }),
    });
    renderCatalog();
    expect(await screen.findByText("Auth Plugin")).toBeInTheDocument();
  });

  it("shows plugin count when not loading", async () => {
    mockListPlugins.mockResolvedValue({
      data: makeResponse({
        content: [mockPlugin],
        totalElements: 42,
        totalPages: 2,
      }),
    });
    renderCatalog();
    expect(await screen.findByText("42 plugins")).toBeInTheDocument();
  });

  it("does not show plugin count while loading", () => {
    mockListPlugins.mockReturnValue(new Promise(() => {}));
    renderCatalog();
    expect(screen.queryByText(/\d+ plugins/)).not.toBeInTheDocument();
  });

  it("renders filter bar", () => {
    renderCatalog();
    expect(
      screen.getByRole("group", { name: /filter and sort options/i }),
    ).toBeInTheDocument();
  });

  it("shows list view when list toggle is clicked", async () => {
    const { default: userEvent } = await import("@testing-library/user-event");
    const user = userEvent.setup();
    mockListPlugins.mockResolvedValue({
      data: makeResponse({
        content: [mockPlugin],
        totalElements: 1,
        totalPages: 1,
      }),
    });
    renderCatalog();
    await screen.findByText("Auth Plugin");
    await user.click(screen.getByRole("button", { name: /list view/i }));
    expect(
      screen.getByRole("list", { name: /plugin list/i }),
    ).toBeInTheDocument();
  });

  it("shows pending review banner with plugin and release counts", async () => {
    useAuthStore.setState({
      accessToken: "tok",
      isAuthenticated: true,
    });
    mockListPlugins.mockResolvedValue({
      data: makeResponse({
        content: [mockPlugin],
        totalElements: 1,
        totalPages: 1,
        pendingReviewPluginCount: 2,
        pendingReviewReleaseCount: 5,
      }),
    });
    renderCatalog();
    expect(
      await screen.findByText(/2 plugins \(5 releases\) pending review/),
    ).toBeInTheDocument();
  });

  it("does not show pending review banner when count is null", async () => {
    useAuthStore.setState({
      accessToken: "tok",
      isAuthenticated: true,
    });
    renderCatalog();
    // Wait for the initial load to complete so we're past loading state
    await waitFor(() => {
      expect(
        screen.queryByLabelText("Loading plugins"),
      ).not.toBeInTheDocument();
    });
    expect(screen.queryByText(/pending review/)).not.toBeInTheDocument();
  });

  it("syncs namespace from URL param into the auth store", () => {
    renderWithRouterAt(
      <CatalogPage />,
      ROUTE_PATH,
      "/namespaces/other-ns/plugins",
    );
    const { namespace } = useAuthStore.getState();
    expect(namespace).toBe("other-ns");
  });

  it("renders the list-view loading skeleton when list view is selected during the initial load", async () => {
    const { default: userEvent } = await import("@testing-library/user-event");
    const user = userEvent.setup();
    // The query never resolves → `isPending` stays true (no previous data to
    // keep). Switching to list view while pending exercises the list-view
    // skeleton branch (`loading && view === "list"`).
    mockListPlugins.mockReturnValue(new Promise(() => {}));

    renderCatalog();
    // Card-view skeleton is up first.
    expect(screen.getByLabelText("Loading plugins")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /list view/i }));
    // Still loading, now in list view → the list-row skeletons render.
    expect(screen.getByLabelText("Loading plugins")).toBeInTheDocument();
    // The card grid skeleton must be gone (we switched views).
    expect(
      screen.queryByRole("list", { name: /plugin cards/i }),
    ).not.toBeInTheDocument();
  });

  it("resets filters from the empty-state action", async () => {
    const { default: userEvent } = await import("@testing-library/user-event");
    const user = userEvent.setup();
    const resetSpy = vi.fn();
    usePluginStore.setState({
      filters: { ...defaultFilters, tag: "missing" },
      resetFilters: resetSpy,
    });
    renderCatalog();
    await screen.findByText("No plugins found");
    await user.click(screen.getByRole("button", { name: /show all plugins/i }));
    expect(resetSpy).toHaveBeenCalledTimes(1);
  });

  describe("drag-and-drop upload affordance", () => {
    function getMain() {
      return document.getElementById("main-content") as HTMLElement;
    }

    function getOverlay() {
      // The CatalogDropOverlay box carries the aria-hidden toggle.
      return screen
        .getByText("Drop .jar or .zip files to upload")
        .closest("[aria-hidden]") as HTMLElement;
    }

    it("shows the drop overlay on drag-enter when the user can upload", () => {
      useAuthStore.setState({
        accessToken: "tok",
        isAuthenticated: true,
        namespace: "acme",
      });
      renderCatalog();
      expect(getOverlay()).toHaveAttribute("aria-hidden", "true");
      fireEvent.dragEnter(getMain(), { dataTransfer: { files: [] } });
      expect(getOverlay()).toHaveAttribute("aria-hidden", "false");
    });

    it("hides the overlay again after a matching drag-leave", () => {
      useAuthStore.setState({
        accessToken: "tok",
        isAuthenticated: true,
        namespace: "acme",
      });
      renderCatalog();
      const main = getMain();
      fireEvent.dragEnter(main, { dataTransfer: { files: [] } });
      expect(getOverlay()).toHaveAttribute("aria-hidden", "false");
      // dragOver keeps it visible; dragLeave decrements the counter to 0.
      fireEvent.dragOver(main, { dataTransfer: { files: [] } });
      fireEvent.dragLeave(main, { dataTransfer: { files: [] } });
      expect(getOverlay()).toHaveAttribute("aria-hidden", "true");
    });

    it("ignores drag events entirely when the user cannot upload", () => {
      // Unauthenticated → canUpload false → handlers early-return, overlay
      // stays hidden.
      useAuthStore.setState({
        accessToken: null,
        isAuthenticated: false,
        namespace: "acme",
      });
      renderCatalog();
      fireEvent.dragEnter(getMain(), { dataTransfer: { files: [] } });
      expect(getOverlay()).toHaveAttribute("aria-hidden", "true");
    });

    it("clears the overlay on drop even when no files are present", () => {
      useAuthStore.setState({
        accessToken: "tok",
        isAuthenticated: true,
        namespace: "acme",
      });
      renderCatalog();
      const main = getMain();
      fireEvent.dragEnter(main, { dataTransfer: { files: [] } });
      expect(getOverlay()).toHaveAttribute("aria-hidden", "false");
      // Empty drop: resets the overlay, skips the uploadFiles branch.
      fireEvent.drop(main, { dataTransfer: { files: [] } });
      expect(getOverlay()).toHaveAttribute("aria-hidden", "true");
    });

    it("forwards dropped files to the upload pipeline", async () => {
      const { axiosInstance } = await import("../api/config");
      useAuthStore.setState({
        accessToken: "tok",
        isAuthenticated: true,
        namespace: "acme",
      });
      renderCatalog();
      const main = getMain();
      const file = new File(["x"], "plugin.jar", {
        type: "application/java-archive",
      });
      fireEvent.dragEnter(main, { dataTransfer: { files: [file] } });
      fireEvent.drop(main, { dataTransfer: { files: [file] } });

      // The upload hook posts the artifact to the namespace endpoint.
      await waitFor(() => {
        expect(axiosInstance.post).toHaveBeenCalledWith(
          "/namespaces/acme/plugin-releases",
          expect.any(FormData),
          expect.objectContaining({
            headers: { "Content-Type": "multipart/form-data" },
          }),
        );
      });
    });
  });
});
