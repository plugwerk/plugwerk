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
