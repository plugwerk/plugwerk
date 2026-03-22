// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
// Shim for the Node.js 'url' module used by openapi-generator-cli's generated common.ts.
// In browsers, URL and URLSearchParams are built-in globals — re-export them here so Vite
// can resolve the import without externalising the module.
export const URL = globalThis.URL
export const URLSearchParams = globalThis.URLSearchParams
