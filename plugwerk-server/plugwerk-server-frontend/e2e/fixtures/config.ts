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

/** An empty storage state for specs that must start unauthenticated. */
export const ANONYMOUS_STATE = { cookies: [], origins: [] } as const;

/** Committed plugin-artifact fixtures used to seed the catalog and drive uploads. */
export const FIXTURE_PLUGINS_DIR = path.join(here, "..", "fixtures", "plugins");

/** Two plugins with distinct tags — seeded by global-setup so filter/detail specs have data. */
export const ALPHA_PLUGIN = {
  pluginId: "io.plugwerk.e2e.alpha",
  name: "Alpha Plugin",
  tag: "alpha",
  v1Jar: "alpha-1.0.0.jar",
  v2Jar: "alpha-2.0.0.jar",
} as const;

export const BETA_PLUGIN = {
  pluginId: "io.plugwerk.e2e.beta",
  name: "Beta Plugin",
  tag: "beta",
  v1Jar: "beta-1.0.0.jar",
} as const;

/** A file with a .jar name but invalid archive bytes — the server rejects it. */
export const MALFORMED_JAR = "malformed.jar";
