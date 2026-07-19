// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    globals: true,
    // Vitest owns the unit tests under src/. The Playwright E2E suite under
    // e2e/ uses `*.spec.ts` too — restrict discovery to src/ so Vitest never
    // tries to load Playwright specs (issue #241).
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    // The default 5s per-test timeout is too tight for the heavier
    // `@testing-library/user-event` flows (multi-field dialogs, several
    // `await user.type(...)` + `waitFor` round-trips) once they run on a
    // slower, contended CI runner — they pass locally but time out in CI.
    // 15s gives comfortable headroom without masking genuine hangs (DEV-44).
    testTimeout: 15000,
    // `@mui/material` re-exports `react-transition-group@4`, which ships CJS
    // with an extensionless directory import (`.../TransitionGroupContext`)
    // and no `exports` map. Vitest externalises both by default, and Node's
    // ESM resolver then rejects the directory import, breaking any test file
    // that mounts a MUI transition component. Inlining forces Vite to bundle
    // and resolve them through its own pipeline. See DEV-29.
    server: {
      deps: {
        inline: [/@mui\//, /react-transition-group/],
      },
    },
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      exclude: [
        "src/api/generated/**",
        "src/test/**",
        "**/*.d.ts",
        "vite.config.ts",
        "vitest.config.ts",
      ],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 80,
      },
    },
  },
});
