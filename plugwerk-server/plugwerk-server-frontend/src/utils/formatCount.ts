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

/**
 * Short human-readable format for display.
 *
 * Examples: 0 → "0", 999 → "999", 1000 → "1k", 1500 → "1.5k",
 *           45000 → "45k", 33300 → "33.3k", 1234567 → "1.2M"
 */
export function formatCount(n: number | undefined): string {
  if (!n) return '0'
  if (n >= 1_000_000) {
    const m = n / 1_000_000
    return m % 1 === 0 ? `${m}M` : `${m.toFixed(1)}M`
  }
  if (n >= 1_000) {
    const k = n / 1_000
    return k % 1 === 0 ? `${k}k` : `${k.toFixed(1)}k`
  }
  return String(n)
}

/**
 * Full number with dot as thousand separator, for tooltips.
 *
 * Examples: 0 → "0", 999 → "999", 1500 → "1.500",
 *           34343000 → "34.343.000", 34000 → "34.000"
 */
export function formatCountFull(n: number | undefined): string {
  if (!n) return '0'
  return n.toLocaleString('de-DE')
}
