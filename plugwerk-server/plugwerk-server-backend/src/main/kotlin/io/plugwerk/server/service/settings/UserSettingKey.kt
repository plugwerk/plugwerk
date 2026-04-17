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

    fun validate(rawValue: String): String? {
        val typeError = when (valueType) {
            SettingValueType.STRING -> null

            SettingValueType.INTEGER -> if (rawValue.toIntOrNull() == null) "value must be an integer" else null

            SettingValueType.BOOLEAN -> if (rawValue !in BOOLEAN_LITERALS) "value must be 'true' or 'false'" else null

            SettingValueType.ENUM -> {
                val allowed = allowedValues
                when {
                    allowed == null -> "ENUM key '$key' has no allowedValues declared"
                    rawValue !in allowed -> "value must be one of $allowed, got '$rawValue'"
                    else -> null
                }
            }
        }
        return typeError ?: extraValidator?.invoke(rawValue)
    }

    companion object {
        private val BOOLEAN_LITERALS = setOf("true", "false")
        private val BY_KEY: Map<String, UserSettingKey> = entries.associateBy { it.key }

        fun byKey(key: String): UserSettingKey? = BY_KEY[key]
    }
}
