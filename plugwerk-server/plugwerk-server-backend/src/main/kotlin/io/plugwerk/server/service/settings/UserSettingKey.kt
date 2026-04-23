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
import java.time.DateTimeException
import java.time.ZoneId

/** Validates that [rawValue] is a known IANA timezone identifier, or empty (= fall back to system default). */
private fun validateTimezoneOrEmpty(rawValue: String): String? {
    if (rawValue.isEmpty()) return null
    return try {
        ZoneId.of(rawValue)
        null
    } catch (_: DateTimeException) {
        "value must be an IANA timezone identifier (e.g. UTC, Europe/Berlin) or empty, got '$rawValue'"
    }
}

/**
 * Registry of per-user settings (ADR-0018).
 *
 * Mirrors the [SettingKey] pattern from ADR-0016 but scoped to individual users.
 * Adding a new user setting requires only a new entry here — no migration needed
 * because rows are created lazily on first write.
 */
enum class UserSettingKey(
    val key: String,
    val valueType: SettingValueType,
    val defaultValue: String,
    val allowedValues: Set<String>? = null,
    val extraValidator: ((String) -> String?)? = null,
) {
    PREFERRED_LANGUAGE(
        key = "preferred_language",
        valueType = SettingValueType.ENUM,
        defaultValue = "en",
        allowedValues = setOf("en"),
    ),
    DEFAULT_NAMESPACE(
        key = "default_namespace",
        valueType = SettingValueType.STRING,
        defaultValue = "",
    ),
    THEME(
        key = "theme",
        valueType = SettingValueType.ENUM,
        defaultValue = "system",
        allowedValues = setOf("light", "dark", "system"),
    ),
    TIMEZONE(
        key = "timezone",
        valueType = SettingValueType.STRING,
        defaultValue = "",
        extraValidator = ::validateTimezoneOrEmpty,
    ),
    ;

    /**
     * Validates a proposed new raw value for this user-setting. Delegates the type-level
     * rules to the shared [ValueValidator] and chains the per-key [extraValidator]
     * afterwards (e.g. IANA timezone check for `timezone`). User settings permit blank
     * strings — `timezone=""` is valid and means "fall back to the system default".
     *
     * @return `null` if the value is acceptable, or a human-readable error message otherwise.
     */
    fun validate(rawValue: String): String? {
        val typeError = ValueValidator.validate(
            rawValue = rawValue,
            type = valueType,
            keyDebugName = key,
            allowedValues = allowedValues,
        )
        return typeError ?: extraValidator?.invoke(rawValue)
    }

    companion object {
        private val BY_KEY: Map<String, UserSettingKey> = entries.associateBy { it.key }

        fun byKey(key: String): UserSettingKey? = BY_KEY[key]
    }
}
