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
package io.plugwerk.server.service.settings

import io.plugwerk.server.domain.SettingValueType

/**
 * Shared type-validation path for both admin-facing [SettingKey.validate] and user-facing
 * [UserSettingKey.validate] (RC-011 / #281).
 *
 * Before this helper existed, both enums carried a near-identical 30-line `when(valueType)`
 * block, and any new [SettingValueType] variant had to be handled in both — one Compile
 * exhaustiveness error per place, easy to miss the second one during review.
 *
 * The per-key differences (application settings forbid blank strings and support int
 * ranges; user settings allow blanks and have no range support today) are expressed as
 * explicit parameters so the call sites stay readable and the validator stays a single
 * source of truth for type semantics.
 */
internal object ValueValidator {

    private val BOOLEAN_LITERALS = setOf("true", "false")

    /**
     * Runs the type-level validation and returns a human-readable error message or `null`
     * if the value is acceptable. Does NOT run any per-key `extraValidator` — the caller
     * chains that after this.
     *
     * @param rawValue         the raw value as submitted.
     * @param type             the declared [SettingValueType] for this key.
     * @param keyDebugName     the dotted key identifier, used only in ENUM error messages
     *                         where an operator mis-configuration (missing `allowedValues`)
     *                         is worth naming the offending key.
     * @param allowedValues    closed set of admissible ENUM values; required for
     *                         [SettingValueType.ENUM], ignored for other types.
     * @param minInt           inclusive lower bound for INTEGER; `null` = no lower bound.
     * @param maxInt           inclusive upper bound for INTEGER; `null` = no upper bound.
     * @param allowBlankString `true` (default) lets STRING accept blank values — correct
     *                         for user-setting keys like `timezone=""`. Set `false` for
     *                         application-setting keys where blank input is always a bug.
     */
    fun validate(
        rawValue: String,
        type: SettingValueType,
        keyDebugName: String,
        allowedValues: Set<String>? = null,
        minInt: Int? = null,
        maxInt: Int? = null,
        allowBlankString: Boolean = true,
    ): String? = when (type) {
        SettingValueType.STRING ->
            if (!allowBlankString && rawValue.isBlank()) "value must not be blank" else null

        SettingValueType.INTEGER -> {
            val parsed = rawValue.toIntOrNull()
            when {
                parsed == null -> "value must be an integer, got '$rawValue'"
                minInt != null && parsed < minInt -> "value must be >= $minInt"
                maxInt != null && parsed > maxInt -> "value must be <= $maxInt"
                else -> null
            }
        }

        SettingValueType.BOOLEAN ->
            if (rawValue !in BOOLEAN_LITERALS) "value must be 'true' or 'false', got '$rawValue'" else null

        SettingValueType.ENUM -> when {
            allowedValues == null -> "ENUM key '$keyDebugName' has no allowedValues declared"
            rawValue !in allowedValues -> "value must be one of $allowedValues, got '$rawValue'"
            else -> null
        }
    }
}
