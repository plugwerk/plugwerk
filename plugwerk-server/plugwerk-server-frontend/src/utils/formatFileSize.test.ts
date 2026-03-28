// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
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
