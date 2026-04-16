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
import { Configuration } from "./generated/configuration";
import { AccessKeysApi } from "./generated/api/access-keys-api";
import { AdminSettingsApi } from "./generated/api/admin-settings-api";
import { AdminUsersApi } from "./generated/api/admin-users-api";
import { AuthApi } from "./generated/api/auth-api";
import { CatalogApi } from "./generated/api/catalog-api";
import { ManagementApi } from "./generated/api/management-api";
import { NamespaceMembersApi } from "./generated/api/namespace-members-api";
import { NamespacesApi } from "./generated/api/namespaces-api";
import { OidcProvidersApi } from "./generated/api/oidc-providers-api";
import { ReviewsApi } from "./generated/api/reviews-api";
import { UpdatesApi } from "./generated/api/updates-api";

const BASE_PATH = "/api/v1";

const axiosInstance = axios.create({
  baseURL: BASE_PATH,
});

axiosInstance.interceptors.request.use((config) => {
  const token = localStorage.getItem("pw-access-token");
  if (token) {
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  return config;
});

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("pw-access-token");
      localStorage.removeItem("pw-username");
      window.location.href = "/login";
    }
    return Promise.reject(error);
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
export const authApi = new AuthApi(apiConfig, BASE_PATH, axiosInstance);
export const adminUsersApi = new AdminUsersApi(
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
export const updatesApi = new UpdatesApi(apiConfig, BASE_PATH, axiosInstance);

export { axiosInstance };
