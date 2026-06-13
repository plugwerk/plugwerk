import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import { defineConfig, globalIgnores } from "eslint/config";

export default defineConfig([
  // Machine-generated / build output — never linted. The OpenAPI client under
  // `src/api/generated` is regenerated wholesale by `npm run generate:api`, so
  // its `eslint-disable` directives would otherwise surface as "unused
  // directive" noise on every run.
  globalIgnores(["dist", "build", "coverage", "src/api/generated"]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    rules: {
      // Honour the `_`-prefix convention for intentionally-unused bindings
      // (API-symmetry params, caught-and-ignored errors) and the
      // destructure-to-omit idiom (`const { secret: _x, ...rest } = obj`).
      "@typescript-eslint/no-unused-vars": [
        "error",
        {
          argsIgnorePattern: "^_",
          varsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
          ignoreRestSiblings: true,
        },
      ],
      // `react-hooks/set-state-in-effect` arrived as an *error* via the
      // eslint-plugin-react-hooks v7 recommended config (Renovate bump); the
      // team never opted into it. Every current hit is a recognised, correct
      // idiom — debounce timers (useDebouncedValue), mount-time data loaders,
      // syncing form drafts from freshly-loaded server entities, and
      // populating/resetting dialog state on open/close — each already
      // annotated in place. Downgrading to a warning keeps the signal visible
      // for incremental migration (tracked in DEV-29 follow-up) without
      // blocking CI or hiding it behind a dozen scattered inline disables.
      "react-hooks/set-state-in-effect": "warn",
    },
  },
  {
    // `react-refresh/only-export-components` guards Fast Refresh (HMR), which
    // only matters for component modules. The app entry point, the router
    // definition (route data + lazy route components), and test helpers
    // legitimately export non-components, so the rule does not apply to them.
    files: [
      "src/main.tsx",
      "src/router/**/*.{ts,tsx}",
      "src/test/**/*.{ts,tsx}",
      "**/*.test.{ts,tsx}",
    ],
    rules: {
      "react-refresh/only-export-components": "off",
    },
  },
]);
