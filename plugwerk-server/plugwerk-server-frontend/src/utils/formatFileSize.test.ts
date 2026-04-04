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
import { describe, it, expect } from 'vitest'
import { formatFileSize } from './formatFileSize'

describe('formatFileSize', () => {
  it('formats bytes below 1 KB as bytes', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(1023)).toBe('1023 B')
  })

  it('formats exactly 1 KB', () => {
    expect(formatFileSize(1024)).toBe('1.0 KB')
  })

  it('formats kilobytes below 1 MB', () => {
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(512 * 1024)).toBe('512.0 KB')
    expect(formatFileSize(1024 * 1024 - 1)).toBe('1024.0 KB')
  })

  it('formats exactly 1 MB', () => {
    expect(formatFileSize(1024 * 1024)).toBe('1.0 MB')
  })

  it('formats megabytes', () => {
    expect(formatFileSize(2.5 * 1024 * 1024)).toBe('2.5 MB')
    expect(formatFileSize(100 * 1024 * 1024)).toBe('100.0 MB')
  })
})
