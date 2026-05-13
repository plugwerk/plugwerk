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
import axios, { type AxiosRequestConfig } from "axios";
import { Configuration } from "./generated/configuration";
import { AccessKeysApi } from "./generated/api/access-keys-api";
import { AdminEmailApi } from "./generated/api/admin-email-api";
import { AdminEmailTemplatesApi } from "./generated/api/admin-email-templates-api";
import { AdminConfigurationApi } from "./generated/api/admin-configuration-api";
import { AdminSchedulerApi } from "./generated/api/admin-scheduler-api";
import { AdminSettingsApi } from "./generated/api/admin-settings-api";
import { AdminStorageConsistencyApi } from "./generated/api/admin-storage-consistency-api";
import { AdminUsersApi } from "./generated/api/admin-users-api";
import { AuthApi } from "./generated/api/auth-api";
import { AuthPasswordResetApi } from "./generated/api/auth-password-reset-api";
import { AuthRegistrationApi } from "./generated/api/auth-registration-api";
import { CatalogApi } from "./generated/api/catalog-api";
import { ManagementApi } from "./generated/api/management-api";
import { NamespaceMembersApi } from "./generated/api/namespace-members-api";
import { NamespacesApi } from "./generated/api/namespaces-api";
import { OidcProvidersApi } from "./generated/api/oidc-providers-api";
import { ReviewsApi } from "./generated/api/reviews-api";
import { UserSettingsApi } from "./generated/api/user-settings-api";
import { refreshAccessToken } from "./refresh";

const BASE_PATH = "/api/v1";

const axiosInstance = axios.create({
  baseURL: BASE_PATH,
  // Send the httpOnly refresh cookie on auth-adjacent calls (ADR-0027).
  withCredentials: true,
});

// Import the store lazily to avoid a circular import (store imports this module).
type AuthStoreAccessor = {
  getAccessToken: () => string | null;
  setAuthenticated: (fields: {
    accessToken: string;
    userId: string;
    displayName: string;
    username?: string | null;
    email: string;
    source: "INTERNAL" | "EXTERNAL";
    passwordChangeRequired: boolean;
    isSuperadmin: boolean;
  }) => void;
  onRefreshFailure: () => void;
};

async function loadAuthAccessor(): Promise<AuthStoreAccessor> {
  const { useAuthStore } = await import("../stores/authStore");
  return {
    getAccessToken: () => useAuthStore.getState().accessToken,
    setAuthenticated: (fields) => useAuthStore.getState().setAuth(fields),
    onRefreshFailure: () => {
      useAuthStore.getState().clearAuth();
      if (
        typeof window !== "undefined" &&
        window.location.pathname !== "/login"
      ) {
        window.location.href = "/login";
      }
    },
  };
}

// Request interceptor: attach the in-memory access token as Bearer.
axiosInstance.interceptors.request.use(async (config) => {
  const accessor = await loadAuthAccessor();
  const token = accessor.getAccessToken();
  if (token) {
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  return config;
});

interface RetriableAxiosConfig extends AxiosRequestConfig {
  _retryAttempted?: boolean;
}

// Response interceptor: on 401, try exactly one refresh-then-retry before giving up.
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as RetriableAxiosConfig | undefined;
    const status = error.response?.status;
    // Refresh call itself must never trigger the retry loop.
    if (status !== 401 || !original || original._retryAttempted) {
      return Promise.reject(error);
    }
    if (
      original.url?.includes("/auth/refresh") ||
      original.url?.includes("/auth/login")
    ) {
      return Promise.reject(error);
    }
    original._retryAttempted = true;
    const accessor = await loadAuthAccessor();
    try {
      const refreshed = await refreshAccessToken();
      if (!refreshed) {
        accessor.onRefreshFailure();
        return Promise.reject(error);
      }
      accessor.setAuthenticated(refreshed);
      original.headers = {
        ...(original.headers ?? {}),
        Authorization: `Bearer ${refreshed.accessToken}`,
      };
      return axiosInstance.request(original);
    } catch (refreshError) {
      accessor.onRefreshFailure();
      return Promise.reject(refreshError);
    }
  },
);

const apiConfig = new Configuration({ basePath: BASE_PATH });

export const accessKeysApi = new AccessKeysApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const adminSettingsApi = new AdminSettingsApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const adminEmailApi = new AdminEmailApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const adminEmailTemplatesApi = new AdminEmailTemplatesApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const authApi = new AuthApi(apiConfig, BASE_PATH, axiosInstance);
export const authRegistrationApi = new AuthRegistrationApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const authPasswordResetApi = new AuthPasswordResetApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const adminUsersApi = new AdminUsersApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const adminStorageConsistencyApi = new AdminStorageConsistencyApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const adminSchedulerApi = new AdminSchedulerApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const adminConfigurationApi = new AdminConfigurationApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const catalogApi = new CatalogApi(apiConfig, BASE_PATH, axiosInstance);
export const managementApi = new ManagementApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const namespaceMembersApi = new NamespaceMembersApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const namespacesApi = new NamespacesApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const oidcProvidersApi = new OidcProvidersApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);
export const reviewsApi = new ReviewsApi(apiConfig, BASE_PATH, axiosInstance);
export const userSettingsApi = new UserSettingsApi(
  apiConfig,
  BASE_PATH,
  axiosInstance,
);

export { axiosInstance };
