// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { createBrowserRouter } from 'react-router-dom'
import { AppShell } from '../AppShell'
import { CatalogPage } from '../pages/CatalogPage'
import { PluginDetailPage } from '../pages/PluginDetailPage'
import { UploadPage } from '../pages/UploadPage'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage'
import { ResetPasswordPage } from '../pages/ResetPasswordPage'
import { AdminSettingsPage } from '../pages/AdminSettingsPage'
import { ProfileSettingsPage } from '../pages/ProfileSettingsPage'
import { Error404Page } from '../pages/errors/Error404Page'
import { Error403Page } from '../pages/errors/Error403Page'
import { Error500Page } from '../pages/errors/Error500Page'
import { Error503Page } from '../pages/errors/Error503Page'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true,                              element: <CatalogPage /> },
      { path: ':namespace/plugins/:pluginId',     element: <PluginDetailPage /> },
      { path: 'upload',                           element: <UploadPage /> },
      { path: 'login',                            element: <LoginPage /> },
      { path: 'register',                         element: <RegisterPage /> },
      { path: 'forgot-password',                  element: <ForgotPasswordPage /> },
      { path: 'reset-password',                   element: <ResetPasswordPage /> },
      { path: 'admin/*',                          element: <AdminSettingsPage /> },
      { path: 'profile',                          element: <ProfileSettingsPage /> },
      { path: '403',                              element: <Error403Page /> },
      { path: '500',                              element: <Error500Page /> },
      { path: '503',                              element: <Error503Page /> },
      { path: '*',                                element: <Error404Page /> },
    ],
  },
])
