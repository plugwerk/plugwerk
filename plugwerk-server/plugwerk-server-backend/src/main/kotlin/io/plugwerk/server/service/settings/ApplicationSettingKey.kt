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

/** Hard safety ceiling in MB for [ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB]. */
const val MAX_ALLOWED_UPLOAD_MB: Int = 1024

/** Validates that [rawValue] is a known IANA timezone identifier. */
private fun validateTimezone(rawValue: String): String? = try {
    ZoneId.of(rawValue)
    null
} catch (_: DateTimeException) {
    "value must be an IANA timezone identifier (e.g. UTC, Europe/Berlin), got '$rawValue'"
}

/**
 * Conservative RFC-5322-ish email check used for `smtp.from_address` (#253).
 *
 * We deliberately allow blank because the SMTP config can legitimately be
 * empty when the operator has not yet set it up — the cross-key invariant
 * "from_address must be set when smtp.enabled=true" is enforced at send
 * time by the mail layer, not at per-key write time.
 */
private fun validateEmailAllowingBlank(rawValue: String): String? {
    if (rawValue.isBlank()) return null
    // Single-line, must contain exactly one @, local + domain non-empty,
    // domain has at least one dot. Good enough for an internal admin field;
    // RFC 5322 in full would require a 100-line parser nobody asked for.
    val emailPattern = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    return if (emailPattern.matches(rawValue)) {
        null
    } else {
        "value must be a valid email address, got '$rawValue'"
    }
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
 *  3. Expose it via `ApplicationSettingsService` accessors as needed by the consumers.
 */
enum class ApplicationSettingKey(
    val key: String,
    val valueType: SettingValueType,
    val defaultValue: String,
    val requiresRestart: Boolean = false,
    val allowedValues: Set<String>? = null,
    val minInt: Int? = null,
    val maxInt: Int? = null,
    /**
     * `true` lets the type-validator accept blank strings for STRING/PASSWORD keys.
     * Default is `false` because most application settings are mandatory once
     * configured. SMTP keys override this so an unconfigured operator is not
     * forced to invent placeholder values (#253).
     */
    val allowBlank: Boolean = false,
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
    GENERAL_DEFAULT_TIMEZONE(
        key = "general.default_timezone",
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

    // ---- SMTP / Email (#253) ------------------------------------------------
    // All SMTP keys allow blank because the operator may not have configured
    // anything yet. Cross-key invariants ("host required when enabled=true")
    // are enforced at send time by the mail layer, not per-key.

    SMTP_ENABLED(
        key = "smtp.enabled",
        valueType = SettingValueType.BOOLEAN,
        defaultValue = "false",
    ),
    SMTP_HOST(
        key = "smtp.host",
        valueType = SettingValueType.STRING,
        defaultValue = "",
        allowBlank = true,
    ),
    SMTP_PORT(
        key = "smtp.port",
        valueType = SettingValueType.INTEGER,
        defaultValue = "587",
        minInt = 1,
        maxInt = 65535,
    ),
    SMTP_USERNAME(
        key = "smtp.username",
        valueType = SettingValueType.STRING,
        defaultValue = "",
        allowBlank = true,
    ),
    SMTP_PASSWORD(
        key = "smtp.password",
        valueType = SettingValueType.PASSWORD,
        defaultValue = "",
        allowBlank = true,
    ),
    SMTP_ENCRYPTION(
        key = "smtp.encryption",
        valueType = SettingValueType.ENUM,
        defaultValue = "starttls",
        allowedValues = setOf("none", "starttls", "tls"),
    ),
    SMTP_FROM_ADDRESS(
        key = "smtp.from_address",
        valueType = SettingValueType.STRING,
        defaultValue = "",
        allowBlank = true,
        extraValidator = ::validateEmailAllowingBlank,
    ),
    SMTP_FROM_NAME(
        key = "smtp.from_name",
        valueType = SettingValueType.STRING,
        defaultValue = "Plugwerk",
        allowBlank = true,
    ),
    ;

    /**
     * Validates a proposed new raw value for this key. Application settings forbid blank
     * strings by default (override per key via [allowBlank]) and support integer range
     * constraints; the shared [ValueValidator] implements the type-level rules, and
     * per-key [extraValidator] runs afterwards on a valid raw value (e.g. IANA timezone
     * check for `general.default_timezone`).
     *
     * @return `null` if the value is acceptable, or a human-readable error message otherwise.
     */
    fun validate(rawValue: String): String? {
        val typeError = ValueValidator.validate(
            rawValue = rawValue,
            type = valueType,
            keyDebugName = key,
            allowedValues = allowedValues,
            minInt = minInt,
            maxInt = maxInt,
            allowBlankString = allowBlank,
        )
        return typeError ?: extraValidator?.invoke(rawValue)
    }

    companion object {
        private val BY_KEY: Map<String, ApplicationSettingKey> = entries.associateBy { it.key }

        /** Looks up a [ApplicationSettingKey] by its dotted identifier. */
        fun byKey(key: String): ApplicationSettingKey? = BY_KEY[key]
    }
}
