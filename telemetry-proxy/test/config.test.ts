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

import { describe, expect, it } from "vitest";
import { RATE_LIMIT_PERIOD_SECONDS } from "../src/constants";
// Vite `?raw` import: the file's contents as a string. Typed inline to avoid
// pulling @types/node into a config that intentionally scopes types to the
// Cloudflare Workers runtime only.
// @ts-expect-error virtual raw module resolved by Vite/Vitest at test time
import wranglerToml from "../wrangler.toml?raw";

/**
 * Guards the coupling documented in both files: the `Retry-After` seconds the
 * Worker emits (RATE_LIMIT_PERIOD_SECONDS) must equal the `[[ratelimits]]`
 * `simple.period` in wrangler.toml. Editing one without the other would silently
 * desync the advertised retry window from the actual throttle window.
 */
describe("rate-limit period stays in sync with wrangler.toml", () => {
  const toml: string = wranglerToml;

  it("matches simple.period in the [[ratelimits]] binding", () => {
    const match = toml.match(/simple\s*=\s*\{[^}]*\bperiod\s*=\s*(\d+)/);
    expect(match, "could not find simple.period in wrangler.toml").not.toBeNull();
    const tomlPeriod = Number(match?.[1]);
    expect(tomlPeriod).toBe(RATE_LIMIT_PERIOD_SECONDS);
  });

  it("uses a Cloudflare-permitted period (10 or 60)", () => {
    expect([10, 60]).toContain(RATE_LIMIT_PERIOD_SECONDS);
  });
});
