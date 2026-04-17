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

/** Hard safety ceiling in MB for [SettingKey.UPLOAD_MAX_FILE_SIZE_MB]. */
const val MAX_ALLOWED_UPLOAD_MB: Int = 1024

/** Validates that [rawValue] is a known IANA timezone identifier. */
private fun validateTimezone(rawValue: String): String? = try {
    ZoneId.of(rawValue)
    null
} catch (_: DateTimeException) {
    "value must be an IANA timezone identifier (e.g. UTC, Europe/Berlin), got '$rawValue'"
}

/**
 * Central registry of every admin-manageable application setting (ADR-0016).
 *
 * Each entry declares its dotted key, its typed default (used as a last-resort fallback if
 * the row is missing — under normal operation the Liquibase seed in
 * `0005_application_settings.yaml` ensures every key has a row), its value type, a
 * `requiresRestart` flag for UX hints in the Admin UI, and an optional validator that the
 * write endpoint runs before persisting a new value.
 *
 * Adding a new setting:
 *  1. Add an entry here.
 *  2. Add a Liquibase insert changeset for it in a new migration (do not edit `0005`).
 *  3. Expose it via `GeneralSettingsService` accessors as needed by the consumers.
 */
enum class SettingKey(
    val key: String,
    val valueType: SettingValueType,
    val defaultValue: String,
    val requiresRestart: Boolean = false,
    val allowedValues: Set<String>? = null,
    val minInt: Int? = null,
    val maxInt: Int? = null,
    val extraValidator: ((String) -> String?)? = null,
) {
    GENERAL_DEFAULT_LANGUAGE(
        key = "general.default_language",
        valueType = SettingValueType.ENUM,
        defaultValue = "en",
        allowedValues = setOf("en"),
    ),
    GENERAL_SITE_NAME(
        key = "general.site_name",
        valueType = SettingValueType.STRING,
        defaultValue = "Plugwerk",
    ),
    GENERAL_TIMEZONE(
        key = "general.timezone",
        valueType = SettingValueType.STRING,
        defaultValue = "UTC",
        extraValidator = ::validateTimezone,
    ),
    UPLOAD_MAX_FILE_SIZE_MB(
        key = "upload.max_file_size_mb",
        valueType = SettingValueType.INTEGER,
        defaultValue = "100",
        requiresRestart = true,
        minInt = 1,
        maxInt = 1024,
    ),
    TRACKING_ENABLED(
        key = "tracking.enabled",
        valueType = SettingValueType.BOOLEAN,
        defaultValue = "true",
    ),
    TRACKING_CAPTURE_IP(
        key = "tracking.capture_ip",
        valueType = SettingValueType.BOOLEAN,
        defaultValue = "true",
    ),
    TRACKING_ANONYMIZE_IP(
        key = "tracking.anonymize_ip",
        valueType = SettingValueType.BOOLEAN,
        defaultValue = "true",
    ),
    TRACKING_CAPTURE_USER_AGENT(
        key = "tracking.capture_user_agent",
        valueType = SettingValueType.BOOLEAN,
        defaultValue = "true",
    ),
    ;

    /**
     * Validates a proposed new raw value for this key.
     *
     * @return `null` if the value is acceptable, or a human-readable error message otherwise.
     */
    fun validate(rawValue: String): String? {
        val typeError = when (valueType) {
            SettingValueType.STRING -> if (rawValue.isBlank()) "value must not be blank" else null

            SettingValueType.INTEGER -> {
                val parsed = rawValue.toIntOrNull()
                    ?: return "value must be an integer, got '$rawValue'"
                val lo = minInt
                val hi = maxInt
                when {
                    lo != null && parsed < lo -> "value must be >= $lo"
                    hi != null && parsed > hi -> "value must be <= $hi"
                    else -> null
                }
            }

            SettingValueType.BOOLEAN -> if (rawValue !in BOOLEAN_LITERALS) {
                "value must be 'true' or 'false', got '$rawValue'"
            } else {
                null
            }

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

        private val BY_KEY: Map<String, SettingKey> = entries.associateBy { it.key }

        /** Looks up a [SettingKey] by its dotted identifier. */
        fun byKey(key: String): SettingKey? = BY_KEY[key]
    }
}
