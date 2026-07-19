// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

/** Base URL of the backend JAR serving the production SPA. */
export const BASE_URL =
  process.env.PLUGWERK_E2E_BASE_URL ?? "http://localhost:8080";

/** Bootstrap admin account — password is pinned via `PLUGWERK_AUTH_ADMIN_PASSWORD`. */
export const ADMIN_USERNAME = "admin";
export const ADMIN_PASSWORD =
  process.env.PLUGWERK_AUTH_ADMIN_PASSWORD ?? "admin";

/** Deterministic namespace seeded by global-setup so login lands on the catalog. */
export const E2E_NAMESPACE = "e2e";
export const E2E_NAMESPACE_NAME = "E2E Test Namespace";

/** Persisted authenticated session (cookies incl. the httpOnly refresh cookie). */
export const STORAGE_STATE = path.join(here, "..", ".auth", "admin.json");

/** An empty storage state for specs that must start unauthenticated. */
export const ANONYMOUS_STATE = { cookies: [], origins: [] } as const;
