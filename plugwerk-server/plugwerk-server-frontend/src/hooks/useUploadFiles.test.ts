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
import { act, renderHook } from "@testing-library/react";
import { createQueryWrapper } from "../test/queryWrapper";
import { useUploadFiles } from "./useUploadFiles";
import { useUploadStore } from "../stores/uploadStore";
import { useUiStore } from "../stores/uiStore";
import { useConfigStore } from "../stores/configStore";
import { axiosInstance } from "../api/config";

vi.mock("../api/config", () => ({
  axiosInstance: { post: vi.fn(), get: vi.fn() },
}));

const post = vi.mocked(axiosInstance.post);

function makeFile(name: string, size?: number): File {
  const file = new File(["plugin-bytes"], name, {
    type: "application/java-archive",
  });
  if (size !== undefined) {
    Object.defineProperty(file, "size", { value: size });
  }
  return file;
}

function toasts() {
  return useUiStore.getState().toasts;
}

function hasToast(predicate: (message: string) => boolean) {
  return toasts().some((t) => predicate(t.message ?? ""));
}

async function upload(files: File[], namespace = "default") {
  const { result } = renderHook(() => useUploadFiles(), {
    wrapper: createQueryWrapper(),
  });
  await act(async () => {
    await result.current.uploadFiles(files, namespace);
  });
}

describe("useUploadFiles", () => {
  beforeEach(() => {
    post.mockReset();
    useUploadStore.getState().reset();
    useUiStore.setState({ toasts: [] });
    // loaded=true short-circuits fetchConfig so the mocked GET is never needed.
    useConfigStore.setState({ loaded: true, maxFileSizeMb: 100 });
  });

  it("warns about and skips files with unsupported extensions", async () => {
    await upload([makeFile("notes.txt"), makeFile("image.png")]);
    expect(hasToast((m) => /only \.jar and \.zip/i.test(m))).toBe(true);
    expect(post).not.toHaveBeenCalled();
  });

  it("uploads a single valid file and reports success", async () => {
    post.mockResolvedValue({ data: {} });
    await upload([makeFile("plugin.jar")]);
    expect(post).toHaveBeenCalledOnce();
    expect(hasToast((m) => /release uploaded successfully/i.test(m))).toBe(
      true,
    );
    expect(
      useUploadStore.getState().entries.every((e) => e.status === "success"),
    ).toBe(true);
  });

  it("pluralises the success toast for multiple files", async () => {
    post.mockResolvedValue({ data: {} });
    await upload([makeFile("a.jar"), makeFile("b.zip")]);
    expect(hasToast((m) => /2 releases uploaded successfully/i.test(m))).toBe(
      true,
    );
  });

  it("forwards upload progress events to the store", async () => {
    post.mockImplementation((_url, _data, config) => {
      config?.onUploadProgress?.({
        loaded: 5,
        total: 10,
        bytes: 5,
        lengthComputable: true,
      });
      // total falsy -> progress branch skipped, must not throw
      config?.onUploadProgress?.({
        loaded: 5,
        total: 0,
        bytes: 5,
        lengthComputable: true,
      });
      return Promise.resolve({ data: {} });
    });
    await upload([makeFile("plugin.jar")]);
    const entry = useUploadStore.getState().entries.at(-1);
    expect(entry?.status).toBe("success");
    expect(entry?.progress).toBe(100);
  });

  it("marks a file that exceeds the size limit as failed without calling the API", async () => {
    useConfigStore.setState({ loaded: true, maxFileSizeMb: 1 });
    await upload([makeFile("huge.jar", 5 * 1024 * 1024)]);
    expect(post).not.toHaveBeenCalled();
    const entry = useUploadStore.getState().entries.at(-1);
    expect(entry?.status).toBe("failed");
    expect(entry?.errorMessage).toMatch(/file too large/i);
    expect(hasToast((m) => /release upload failed/i.test(m))).toBe(true);
  });

  it("surfaces the server message on an axios upload error", async () => {
    post.mockRejectedValue(
      Object.assign(new Error("Network Error"), {
        isAxiosError: true,
        response: { data: { message: "Duplicate version rejected" } },
      }),
    );
    await upload([makeFile("plugin.jar")]);
    const entry = useUploadStore.getState().entries.at(-1);
    expect(entry?.status).toBe("failed");
    expect(entry?.errorMessage).toBe("Duplicate version rejected");
  });

  it("falls back to the axios error message when the body has none", async () => {
    post.mockRejectedValue(
      Object.assign(new Error("boom"), {
        isAxiosError: true,
        response: { data: {} },
      }),
    );
    await upload([makeFile("plugin.jar")]);
    expect(useUploadStore.getState().entries.at(-1)?.errorMessage).toBe("boom");
  });

  it("uses a generic message when the failure is not an Error", async () => {
    post.mockRejectedValue("weird non-error");
    await upload([makeFile("plugin.jar")]);
    expect(useUploadStore.getState().entries.at(-1)?.errorMessage).toBe(
      "Upload failed.",
    );
  });

  it("reports a partial result when some uploads fail", async () => {
    post
      .mockResolvedValueOnce({ data: {} })
      .mockRejectedValueOnce(new Error("nope"));
    await upload([makeFile("ok.jar"), makeFile("bad.zip")]);
    expect(hasToast((m) => /1 succeeded, 1 failed/i.test(m))).toBe(true);
  });

  it("reports an all-failed result with a pluralised message", async () => {
    post.mockRejectedValue(new Error("nope"));
    await upload([makeFile("a.jar"), makeFile("b.zip")]);
    expect(hasToast((m) => /all 2 uploads failed/i.test(m))).toBe(true);
  });
});
