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
import { describe, it, expect, beforeEach } from "vitest";
import { useUploadStore } from "./uploadStore";

function createFile(name: string, size = 1024): File {
  return new File([new ArrayBuffer(size)], name, {
    type: "application/java-archive",
  });
}

describe("uploadStore", () => {
  beforeEach(() => {
    useUploadStore.getState().reset();
  });

  describe("addFiles", () => {
    it("adds valid .jar files as pending entries", () => {
      const files = [createFile("plugin-a.jar"), createFile("plugin-b.jar")];
      useUploadStore.getState().addFiles(files);

      const { entries, panelVisible } = useUploadStore.getState();
      expect(entries).toHaveLength(2);
      expect(entries[0].fileName).toBe("plugin-a.jar");
      expect(entries[0].status).toBe("pending");
      expect(entries[0].progress).toBe(0);
      expect(entries[0].errorMessage).toBeNull();
      expect(panelVisible).toBe(true);
    });

    it("adds valid .zip files as pending entries", () => {
      useUploadStore.getState().addFiles([createFile("plugin.zip")]);
      expect(useUploadStore.getState().entries).toHaveLength(1);
    });

    it("filters out non-.jar/.zip files", () => {
      const files = [
        createFile("plugin.jar"),
        createFile("readme.txt"),
        createFile("image.png"),
      ];
      useUploadStore.getState().addFiles(files);

      const { entries } = useUploadStore.getState();
      expect(entries).toHaveLength(1);
      expect(entries[0].fileName).toBe("plugin.jar");
    });

    it("does not show panel when all files are invalid", () => {
      useUploadStore.getState().addFiles([createFile("readme.txt")]);
      const { entries, panelVisible } = useUploadStore.getState();
      expect(entries).toHaveLength(0);
      expect(panelVisible).toBe(false);
    });

    it("appends to existing entries", () => {
      useUploadStore.getState().addFiles([createFile("first.jar")]);
      useUploadStore.getState().addFiles([createFile("second.jar")]);
      expect(useUploadStore.getState().entries).toHaveLength(2);
    });

    it("assigns unique ids to each entry", () => {
      const files = [createFile("a.jar"), createFile("b.jar")];
      useUploadStore.getState().addFiles(files);
      const ids = useUploadStore.getState().entries.map((e) => e.id);
      expect(new Set(ids).size).toBe(2);
    });
  });

  describe("updateEntry", () => {
    it("updates status and progress of a specific entry", () => {
      useUploadStore.getState().addFiles([createFile("plugin.jar")]);
      const id = useUploadStore.getState().entries[0].id;

      useUploadStore
        .getState()
        .updateEntry(id, { status: "uploading", progress: 42 });

      const entry = useUploadStore.getState().entries[0];
      expect(entry.status).toBe("uploading");
      expect(entry.progress).toBe(42);
    });

    it("updates error message on failure", () => {
      useUploadStore.getState().addFiles([createFile("plugin.jar")]);
      const id = useUploadStore.getState().entries[0].id;

      useUploadStore
        .getState()
        .updateEntry(id, { status: "failed", errorMessage: "Server error" });

      const entry = useUploadStore.getState().entries[0];
      expect(entry.status).toBe("failed");
      expect(entry.errorMessage).toBe("Server error");
    });

    it("does not modify other entries", () => {
      useUploadStore
        .getState()
        .addFiles([createFile("a.jar"), createFile("b.jar")]);
      const [first, second] = useUploadStore.getState().entries;

      useUploadStore
        .getState()
        .updateEntry(first.id, { status: "success", progress: 100 });

      const updated = useUploadStore.getState().entries;
      expect(updated[0].status).toBe("success");
      expect(updated[1].status).toBe("pending");
      expect(updated[1].id).toBe(second.id);
    });

    it("produces a new entries array (immutability)", () => {
      useUploadStore.getState().addFiles([createFile("plugin.jar")]);
      const before = useUploadStore.getState().entries;
      const id = before[0].id;

      useUploadStore.getState().updateEntry(id, { progress: 50 });
      const after = useUploadStore.getState().entries;

      expect(before).not.toBe(after);
    });
  });

  describe("clearCompleted", () => {
    it("removes success and failed entries", () => {
      useUploadStore
        .getState()
        .addFiles([
          createFile("a.jar"),
          createFile("b.jar"),
          createFile("c.jar"),
        ]);
      const [a, b, c] = useUploadStore.getState().entries;

      useUploadStore.getState().updateEntry(a.id, { status: "success" });
      useUploadStore
        .getState()
        .updateEntry(b.id, { status: "failed", errorMessage: "err" });
      // c stays pending

      useUploadStore.getState().clearCompleted();

      const { entries, panelVisible } = useUploadStore.getState();
      expect(entries).toHaveLength(1);
      expect(entries[0].id).toBe(c.id);
      expect(panelVisible).toBe(true);
    });

    it("hides panel when all entries are completed", () => {
      useUploadStore.getState().addFiles([createFile("plugin.jar")]);
      const id = useUploadStore.getState().entries[0].id;
      useUploadStore.getState().updateEntry(id, { status: "success" });

      useUploadStore.getState().clearCompleted();

      const { entries, panelVisible } = useUploadStore.getState();
      expect(entries).toHaveLength(0);
      expect(panelVisible).toBe(false);
    });
  });

  describe("dismissPanel", () => {
    it("hides panel and clears all entries", () => {
      useUploadStore.getState().addFiles([createFile("plugin.jar")]);
      useUploadStore.getState().dismissPanel();

      const { entries, panelVisible } = useUploadStore.getState();
      expect(entries).toHaveLength(0);
      expect(panelVisible).toBe(false);
    });
  });

  describe("setMaxFileSizeMb", () => {
    it("updates the max file size", () => {
      useUploadStore.getState().setMaxFileSizeMb(200);
      expect(useUploadStore.getState().maxFileSizeMb).toBe(200);
    });
  });

  describe("reset", () => {
    it("clears all state", () => {
      useUploadStore.getState().addFiles([createFile("plugin.jar")]);
      useUploadStore.getState().showPanel();
      useUploadStore.getState().reset();

      const { entries, panelVisible } = useUploadStore.getState();
      expect(entries).toHaveLength(0);
      expect(panelVisible).toBe(false);
    });
  });
});
