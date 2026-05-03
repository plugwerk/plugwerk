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
import axios from "axios";
import { create } from "zustand";
import { adminEmailTemplatesApi } from "../api/config";
import type { MailTemplateResponse } from "../api/generated/model/mail-template-response";
import type { MailTemplateUpdateRequest } from "../api/generated/model/mail-template-update-request";

interface EmailTemplatesState {
  readonly templates: MailTemplateResponse[];
  readonly loaded: boolean;
  readonly loading: boolean;
  readonly saving: boolean;
  readonly error: string | null;
  load: () => Promise<void>;
  update: (
    key: string,
    request: MailTemplateUpdateRequest,
  ) => Promise<MailTemplateResponse>;
  reset: (key: string) => Promise<MailTemplateResponse>;
  clear: () => void;
}

function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (typeof message === "string" && message.length > 0) {
      return message;
    }
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unknown error";
}

function replaceTemplate(
  templates: MailTemplateResponse[],
  next: MailTemplateResponse,
): MailTemplateResponse[] {
  return templates.map((t) => (t.key === next.key ? next : t));
}

export const useEmailTemplatesStore = create<EmailTemplatesState>((set) => ({
  templates: [],
  loaded: false,
  loading: false,
  saving: false,
  error: null,

  async load() {
    set({ loading: true, error: null });
    try {
      const response = await adminEmailTemplatesApi.listMailTemplates();
      set({
        templates: response.data.templates,
        loaded: true,
        loading: false,
      });
    } catch (err) {
      set({ loaded: true, loading: false, error: extractErrorMessage(err) });
      throw err;
    }
  },

  async update(key, request) {
    set({ saving: true, error: null });
    try {
      const response = await adminEmailTemplatesApi.updateMailTemplate({
        key,
        mailTemplateUpdateRequest: request,
      });
      set((s) => ({
        templates: replaceTemplate(s.templates, response.data),
        saving: false,
      }));
      return response.data;
    } catch (err) {
      set({ saving: false, error: extractErrorMessage(err) });
      throw err;
    }
  },

  async reset(key) {
    set({ saving: true, error: null });
    try {
      await adminEmailTemplatesApi.resetMailTemplate({ key });
      // Re-fetch the single key to get the post-reset (default) view back
      // into the cache. Cheaper than reloading every template; mirrors how
      // EmailServerPage refreshes only the slice it touched.
      const response = await adminEmailTemplatesApi.getMailTemplate({ key });
      set((s) => ({
        templates: replaceTemplate(s.templates, response.data),
        saving: false,
      }));
      // Returned so the edit page can re-seed its local draft state from
      // the post-reset snapshot — without this, the form keeps showing
      // the now-stale customised values even though the store is correct.
      return response.data;
    } catch (err) {
      set({ saving: false, error: extractErrorMessage(err) });
      throw err;
    }
  },

  clear() {
    set({
      templates: [],
      loaded: false,
      loading: false,
      saving: false,
      error: null,
    });
  },
}));
