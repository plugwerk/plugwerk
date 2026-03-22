// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import axios from 'axios'
import { Configuration } from './generated/configuration'
import { CatalogApi } from './generated/api/catalog-api'
import { ManagementApi } from './generated/api/management-api'
import { ReviewsApi } from './generated/api/reviews-api'
import { UpdatesApi } from './generated/api/updates-api'

const BASE_PATH = '/api/v1'

const axiosInstance = axios.create({
  baseURL: BASE_PATH,
})

axiosInstance.interceptors.request.use((config) => {
  const token = localStorage.getItem('pw-access-token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('pw-access-token')
      localStorage.removeItem('pw-username')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

const apiConfig = new Configuration({ basePath: BASE_PATH })

export const catalogApi = new CatalogApi(apiConfig, BASE_PATH, axiosInstance)
export const managementApi = new ManagementApi(apiConfig, BASE_PATH, axiosInstance)
export const reviewsApi = new ReviewsApi(apiConfig, BASE_PATH, axiosInstance)
export const updatesApi = new UpdatesApi(apiConfig, BASE_PATH, axiosInstance)

export { axiosInstance }
