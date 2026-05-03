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
import { useEffect, useState } from "react";

/**
 * Returns [debouncedValue, isPending].
 *
 * `debouncedValue` lags `value` by [delayMs] of inactivity. `isPending` is
 * `true` while the timer is counting down — useful for surfacing a "syncing…"
 * indicator on the consumer side without the consumer needing to track its
 * own dirty flag.
 *
 * Re-running the timer on every change cancels the previous one, so a fast
 * typist does not trigger a burst of intermediate fires.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): [T, boolean] {
  const [debounced, setDebounced] = useState<T>(value);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (Object.is(value, debounced)) {
      setPending(false);
      return;
    }
    setPending(true);
    const id = window.setTimeout(() => {
      setDebounced(value);
      setPending(false);
    }, delayMs);
    return () => window.clearTimeout(id);
    // `debounced` intentionally excluded — including it would re-arm the
    // timer the moment the debounced value lands, defeating the purpose.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, delayMs]);

  return [debounced, pending];
}
