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
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppShell } from '../AppShell'
import { AdminRoute } from '../components/auth/AdminRoute'
import { ProtectedRoute } from '../components/auth/ProtectedRoute'
import { CatalogPage } from '../pages/CatalogPage'
import { PluginDetailPage } from '../pages/PluginDetailPage'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage'
import { ResetPasswordPage } from '../pages/ResetPasswordPage'
import { AdminSettingsPage, GeneralSection, UsersSection, OidcProvidersSection, ReviewsSection } from '../pages/AdminSettingsPage'
import { NamespacesSection } from '../components/admin/NamespacesSection'
import { ChangePasswordPage } from '../pages/ChangePasswordPage'
import { ProfileSettingsPage } from '../pages/ProfileSettingsPage'
import { Error404Page } from '../pages/errors/Error404Page'
import { Error403Page } from '../pages/errors/Error403Page'
import { Error500Page } from '../pages/errors/Error500Page'
import { Error503Page } from '../pages/errors/Error503Page'
import { useAuthStore } from '../stores/authStore'
import { ApiDocsPage } from '../pages/ApiDocsPage'
import { OnboardingPage } from '../pages/OnboardingPage'

function CatalogRedirect() {
  const namespace = useAuthStore((s) => s.namespace)
  // undefined = still loading, null = no namespaces, string = ready
  if (namespace === undefined) return null
  if (namespace === null) return <Navigate to="/onboarding" replace />
  return <Navigate to={`/namespaces/${namespace}/plugins`} replace />
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    children: [
      // Protected routes — require a logged-in user
      { index: true,                                                    element: <ProtectedRoute><CatalogRedirect /></ProtectedRoute> },
      { path: 'namespaces/:namespace/plugins',                          element: <ProtectedRoute><CatalogPage /></ProtectedRoute> },
      { path: 'namespaces/:namespace/plugins/:pluginId',                element: <ProtectedRoute><PluginDetailPage /></ProtectedRoute> },
      {
        path: 'admin',
        element: <ProtectedRoute><AdminRoute><AdminSettingsPage /></AdminRoute></ProtectedRoute>,
        children: [
          { index: true,              element: <Navigate to="global-settings" replace /> },
          { path: 'global-settings',  element: <GeneralSection /> },
          { path: 'namespaces',       element: <NamespacesSection /> },
          { path: 'users',            element: <UsersSection /> },
          { path: 'oidc-providers',   element: <OidcProvidersSection /> },
          { path: 'reviews',          element: <ReviewsSection /> },
        ],
      },
      { path: 'onboarding',                                              element: <ProtectedRoute><OnboardingPage /></ProtectedRoute> },
      { path: 'change-password',                                        element: <ProtectedRoute><ChangePasswordPage /></ProtectedRoute> },
      { path: 'profile',                                                element: <ProtectedRoute><ProfileSettingsPage /></ProtectedRoute> },

      // Public routes — no login required
      { path: 'api-docs',                     element: <ApiDocsPage /> },
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
