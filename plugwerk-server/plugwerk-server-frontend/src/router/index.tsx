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
import { lazy, Suspense } from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";
import { CircularProgress, Box } from "@mui/material";
import { AppShell } from "../AppShell";
import { AdminIndexRedirect } from "../components/admin/AdminIndexRedirect";
import { AdminRoute } from "../components/auth/AdminRoute";
import { ProtectedRoute } from "../components/auth/ProtectedRoute";
import { CatalogPage } from "../pages/CatalogPage";
import { PluginDetailPage } from "../pages/PluginDetailPage";
import { LoginPage } from "../pages/LoginPage";
import { RegisterPage } from "../pages/RegisterPage";
import { VerifyEmailPendingPage } from "../pages/VerifyEmailPendingPage";
import { VerifyEmailCallbackPage } from "../pages/VerifyEmailCallbackPage";
import { ForgotPasswordPage } from "../pages/ForgotPasswordPage";
import { ResetPasswordPage } from "../pages/ResetPasswordPage";
import { AdminSettingsPage } from "../pages/AdminSettingsPage";
import { ChangePasswordPage } from "../pages/ChangePasswordPage";
import { ProfileSettingsPage } from "../pages/ProfileSettingsPage";
import { Error404Page } from "../pages/errors/Error404Page";
import { Error403Page } from "../pages/errors/Error403Page";
import { Error500Page } from "../pages/errors/Error500Page";
import { Error503Page } from "../pages/errors/Error503Page";
import { useAuthStore } from "../stores/authStore";
import { ApiDocsPage } from "../pages/ApiDocsPage";
import { OnboardingPage } from "../pages/OnboardingPage";

// Lazy-loaded admin sections — only loaded when the user navigates to admin
const GeneralSection = lazy(() =>
  import("../pages/admin/GeneralSection").then((m) => ({
    default: m.GeneralSection,
  })),
);
const UsersSection = lazy(() =>
  import("../pages/admin/UsersSection").then((m) => ({
    default: m.UsersSection,
  })),
);
const OidcProvidersSection = lazy(() =>
  import("../pages/admin/OidcProvidersSection").then((m) => ({
    default: m.OidcProvidersSection,
  })),
);
const ReviewsSection = lazy(() =>
  import("../pages/admin/ReviewsSection").then((m) => ({
    default: m.ReviewsSection,
  })),
);
const NamespacesSection = lazy(() =>
  import("../components/admin/NamespacesSection").then((m) => ({
    default: m.NamespacesSection,
  })),
);
const NamespaceDetailPage = lazy(() =>
  import("../components/admin/NamespaceDetailView").then((m) => ({
    default: m.NamespaceDetailView,
  })),
);
const EmailLayout = lazy(() =>
  import("../pages/admin/EmailLayout").then((m) => ({
    default: m.EmailLayout,
  })),
);
const StorageConsistencySection = lazy(() =>
  import("../pages/admin/StorageConsistencySection").then((m) => ({
    default: m.StorageConsistencySection,
  })),
);
const SchedulerSection = lazy(() =>
  import("../pages/admin/scheduler/SchedulerSection").then((m) => ({
    default: m.SchedulerSection,
  })),
);
const EmailServerPage = lazy(() =>
  import("../pages/admin/EmailServerPage").then((m) => ({
    default: m.EmailServerPage,
  })),
);
const EmailTemplatesListPage = lazy(() =>
  import("../pages/admin/EmailTemplatesListPage").then((m) => ({
    default: m.EmailTemplatesListPage,
  })),
);
const EmailTemplateEditPage = lazy(() =>
  import("../pages/admin/EmailTemplateEditPage").then((m) => ({
    default: m.EmailTemplateEditPage,
  })),
);

function LazyFallback() {
  return (
    <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
      <CircularProgress size={24} />
    </Box>
  );
}

function CatalogRedirect() {
  const namespace = useAuthStore((s) => s.namespace);
  // undefined = still loading, null = no namespaces, string = ready
  if (namespace === undefined) return null;
  if (namespace === null) return <Navigate to="/onboarding" replace />;
  return <Navigate to={`/namespaces/${namespace}/plugins`} replace />;
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    children: [
      // Protected routes — require a logged-in user
      {
        index: true,
        element: (
          <ProtectedRoute>
            <CatalogRedirect />
          </ProtectedRoute>
        ),
      },
      {
        path: "namespaces/:namespace/plugins",
        element: (
          <ProtectedRoute>
            <CatalogPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "namespaces/:namespace/plugins/:pluginId",
        element: (
          <ProtectedRoute>
            <PluginDetailPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "admin",
        element: (
          <ProtectedRoute>
            <AdminRoute>
              <AdminSettingsPage />
            </AdminRoute>
          </ProtectedRoute>
        ),
        children: [
          // Role-aware index redirect — see AdminIndexRedirect for why
          // hard-coding `global-settings` here would land namespace admins
          // on a 403 page (issue #411).
          { index: true, element: <AdminIndexRedirect /> },
          {
            path: "global-settings",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <GeneralSection />
              </Suspense>
            ),
          },
          {
            path: "namespaces",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <NamespacesSection />
              </Suspense>
            ),
          },
          {
            path: "namespaces/:slug",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <NamespaceDetailPage />
              </Suspense>
            ),
          },
          {
            path: "users",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <UsersSection />
              </Suspense>
            ),
          },
          {
            path: "oidc-providers",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <OidcProvidersSection />
              </Suspense>
            ),
          },
          {
            // Nested layout for `/admin/email/*` (#253, #438).
            path: "email",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <EmailLayout />
              </Suspense>
            ),
            children: [
              { index: true, element: <Navigate to="server" replace /> },
              {
                path: "server",
                element: (
                  <Suspense fallback={<LazyFallback />}>
                    <EmailServerPage />
                  </Suspense>
                ),
              },
              {
                path: "templates",
                element: (
                  <Suspense fallback={<LazyFallback />}>
                    <EmailTemplatesListPage />
                  </Suspense>
                ),
              },
              {
                path: "templates/:key",
                element: (
                  <Suspense fallback={<LazyFallback />}>
                    <EmailTemplateEditPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            path: "storage-consistency",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <StorageConsistencySection />
              </Suspense>
            ),
          },
          {
            path: "scheduler",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <SchedulerSection />
              </Suspense>
            ),
          },
          {
            path: "reviews",
            element: (
              <Suspense fallback={<LazyFallback />}>
                <ReviewsSection />
              </Suspense>
            ),
          },
        ],
      },
      {
        path: "onboarding",
        element: (
          <ProtectedRoute>
            <OnboardingPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "change-password",
        element: (
          <ProtectedRoute>
            <ChangePasswordPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "profile",
        element: (
          <ProtectedRoute>
            <ProfileSettingsPage />
          </ProtectedRoute>
        ),
      },

      // Public routes — no login required
      { path: "api-docs", element: <ApiDocsPage /> },
      { path: "login", element: <LoginPage /> },
      { path: "register", element: <RegisterPage /> },
      // /onboarding/verify-email — waiting state shown after a
      // successful registration with verification on (#420).
      {
        path: "onboarding/verify-email",
        element: <VerifyEmailPendingPage />,
      },
      // /verify-email — token-callback opened from the link in the
      // verification email; reads ?token=… and calls the backend.
      { path: "verify-email", element: <VerifyEmailCallbackPage /> },
      { path: "forgot-password", element: <ForgotPasswordPage /> },
      { path: "reset-password", element: <ResetPasswordPage /> },
      { path: "403", element: <Error403Page /> },
      { path: "500", element: <Error500Page /> },
      { path: "503", element: <Error503Page /> },
      { path: "*", element: <Error404Page /> },
    ],
  },
]);
