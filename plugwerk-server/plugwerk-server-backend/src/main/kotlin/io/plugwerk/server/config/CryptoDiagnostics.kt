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
package io.plugwerk.server.config

import javax.crypto.BadPaddingException

/** Env-var name operators can grep for in logs after a key rotation goes wrong. */
internal const val ENCRYPTION_KEY_ENV_VAR = "PLUGWERK_AUTH_ENCRYPTION_KEY"

/**
 * Walks the cause chain looking for [BadPaddingException] (covers
 * `AEADBadTagException` for GCM). Returns `true` if any element matches,
 * `false` otherwise. Detection by type is robust to Spring re-wording
 * the wrapping `IllegalStateException` message in a future release.
 *
 * Spring's `Encryptors.delux()` produces an `IllegalStateException` with
 * the literal `"Unable to invoke Cipher due to bad padding"` and the
 * actual `BadPaddingException` as cause. We match the cause, not the
 * message.
 *
 * Bounded by a small depth guard so a self-referential cause cycle (some
 * libraries set `cause` to `this`) does not stack-overflow.
 */
internal fun Throwable.isBadPadding(): Boolean {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (current is BadPaddingException) return true
        if (!seen.add(current)) return false
        current = current.cause
        depth++
    }
    return false
}

private const val MAX_CAUSE_DEPTH = 10

/**
 * Canonical operator-facing message for an encryption-key mismatch. Always
 * mentions [ENCRYPTION_KEY_ENV_VAR] verbatim so it can be grepped from
 * deployment logs, then names [subject] (which row / which setting) and
 * ends with the verb-led [hint] for what to do next.
 */
internal fun encryptionKeyMismatchMessage(subject: String, hint: String): String =
    "Cannot decrypt $subject. The data was encrypted with a different key than the " +
        "current $ENCRYPTION_KEY_ENV_VAR. $hint"
