// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, vi, afterEach } from 'vitest'
import { formatDateTime, formatRelativeTime } from './formatDateTime'

describe('formatDateTime', () => {
  it('formats a UTC date string as dd.MM.yyyy HH:mm:ss in local time', () => {
    // Use a fixed date to avoid timezone flakiness
    const result = formatDateTime('2026-03-15T14:30:45Z')
    // Should contain the date parts (exact time depends on local TZ, so check format pattern)
    expect(result).toMatch(/^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}:\d{2}$/)
  })

  it('pads single-digit day and month', () => {
    const result = formatDateTime('2026-01-05T08:03:07Z')
    expect(result).toMatch(/^05\.01\.2026/)
  })

  it('returns "—" for undefined', () => {
    expect(formatDateTime(undefined)).toBe('—')
  })

  it('returns "—" for empty string', () => {
    expect(formatDateTime('')).toBe('—')
  })
})

describe('formatRelativeTime', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns "—" for undefined', () => {
    expect(formatRelativeTime(undefined)).toBe('—')
  })

  it('returns "just now" for very recent dates', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-15T10:00:00Z'))
    expect(formatRelativeTime('2026-01-15T09:59:50Z')).toBe('just now')
  })

  it('returns minutes for dates less than an hour ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-15T10:00:00Z'))
    expect(formatRelativeTime('2026-01-15T09:45:00Z')).toBe('15m ago')
  })

  it('returns hours for dates less than a day ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-15T10:00:00Z'))
    expect(formatRelativeTime('2026-01-15T05:00:00Z')).toBe('5h ago')
  })

  it('returns days for dates less than a week ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-15T10:00:00Z'))
    expect(formatRelativeTime('2026-01-12T10:00:00Z')).toBe('3d ago')
  })

  it('returns weeks for dates less than 5 weeks ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-22T10:00:00Z'))
    expect(formatRelativeTime('2026-01-08T10:00:00Z')).toBe('2w ago')
  })

  it('returns months for dates less than a year ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-15T10:00:00Z'))
    expect(formatRelativeTime('2026-03-15T10:00:00Z')).toBe('3mo ago')
  })

  it('returns years for dates more than a year ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2028-01-15T10:00:00Z'))
    expect(formatRelativeTime('2026-01-15T10:00:00Z')).toBe('2y ago')
  })
})
