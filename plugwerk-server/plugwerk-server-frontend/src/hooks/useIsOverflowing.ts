// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { useState, useEffect, type RefObject } from 'react'

/**
 * Returns true when the referenced element's content is clipped (either
 * horizontally via text-overflow or vertically via line-clamp).
 */
export function useIsOverflowing(ref: RefObject<HTMLElement | null>): boolean {
  const [isOverflowing, setIsOverflowing] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return

    function check() {
      if (!el) return
      const overflowing =
        el.scrollWidth > el.clientWidth || el.scrollHeight > el.clientHeight
      setIsOverflowing(overflowing)
    }

    check()
    const observer = new ResizeObserver(check)
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref])

  return isOverflowing
}
