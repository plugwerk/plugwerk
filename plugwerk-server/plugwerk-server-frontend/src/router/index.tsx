// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { createBrowserRouter } from 'react-router-dom'
import { AppShell } from '../AppShell'
import { ProtectedRoute } from '../components/auth/ProtectedRoute'
import { CatalogPage } from '../pages/CatalogPage'
import { PluginDetailPage } from '../pages/PluginDetailPage'
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
      // Protected routes — require a logged-in user
      { index: true,                          element: <ProtectedRoute><CatalogPage /></ProtectedRoute> },
      { path: ':namespace/plugins/:pluginId', element: <ProtectedRoute><PluginDetailPage /></ProtectedRoute> },
      { path: 'admin/*',                      element: <ProtectedRoute><AdminSettingsPage /></ProtectedRoute> },
      { path: 'profile',                      element: <ProtectedRoute><ProfileSettingsPage /></ProtectedRoute> },

      // Public routes — no login required
      { path: 'login',                        element: <LoginPage /> },
      { path: 'register',                     element: <RegisterPage /> },
      { path: 'forgot-password',              element: <ForgotPasswordPage /> },
      { path: 'reset-password',               element: <ResetPasswordPage /> },
      { path: '403',                          element: <Error403Page /> },
      { path: '500',                          element: <Error500Page /> },
      { path: '503',                          element: <Error503Page /> },
      { path: '*',                            element: <Error404Page /> },
    ],
  },
])
