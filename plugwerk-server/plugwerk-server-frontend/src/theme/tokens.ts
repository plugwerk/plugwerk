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
// Design tokens mirroring docs/design/html-templates/tokens.css

export const tokens = {
  color: {
    primary: '#0F62FE',
    primaryDark: '#0043CE',
    primaryLight: '#D0E2FF',
    secondary: '#6929C4',
    success: '#198038',
    warning: '#F1C21B',
    danger: '#DA1E28',

    // Neutrals
    gray100: '#161616',
    gray80: '#393939',
    gray60: '#6F6F6F',
    gray40: '#A8A8A8',
    gray20: '#E0E0E0',
    gray10: '#F4F4F4',
    white: '#FFFFFF',
  },

  badge: {
    published:     { bg: '#DEFBE6', text: '#198038' },
    draft:         { bg: '#FFE8CC', text: '#A84400' },
    deprecated:    { bg: '#FFF1C7', text: '#8A6A00' },
    yanked:        { bg: '#FFD7D9', text: '#DA1E28' },
    tag:           { bg: '#D0E2FF', text: '#0043CE' },
    version:       { bg: '#F4F4F4', text: '#161616' },
    suspended:     { bg: '#FFD7D9', text: '#DA1E28' },
    archived:      { bg: '#FFF1C7', text: '#8A6A00' },
    pendingReview: { bg: '#FFE8CC', text: '#A84400' },
  },

  radius: {
    btn: '4px',
    card: '8px',
    input: '4px',
  },

  space: {
    1: '4px',
    2: '8px',
    3: '12px',
    4: '16px',
    5: '20px',
    6: '24px',
    7: '32px',
  },
} as const
