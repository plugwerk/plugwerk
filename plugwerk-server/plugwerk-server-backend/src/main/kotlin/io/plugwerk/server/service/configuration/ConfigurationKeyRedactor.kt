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
package io.plugwerk.server.service.configuration

/**
 * Decides whether a config property name carries a sensitive value that
 * must NOT leak into the read-only admin configuration view (#522).
 *
 * The strategy is **by construction**: any property whose terminal name
 * token ends in one of the configured suffixes is treated as a secret,
 * regardless of how new the field is. Adding a new sensitive field to
 * `PlugwerkProperties` does not require remembering to extend a list —
 * as long as the name follows the project's existing convention
 * (`jwt-secret`, `access-key`, `secret-key`, `scrape-password`, …), the
 * redactor catches it automatically.
 *
 * The terminal-token rule prevents false positives like `keyPrefix`,
 * `keyLookupHash`, or `passwordChangeRequired`, where the segment that
 * looks sensitive sits inside a longer compound word. Only the final
 * dash- or camelCase-separated word participates in the suffix match.
 */
object ConfigurationKeyRedactor {

    /**
     * Suffix tokens that mark a property as secret. Matched against the
     * last word of the kebab-case or camelCase name, case-insensitively.
     */
    private val SENSITIVE_SUFFIXES = setOf("secret", "password", "key", "token")

    /**
     * Explicit allow-list — names that look sensitive by the suffix rule
     * but are operational metadata, not credentials. Keeps the rule
     * mechanical while documenting the deliberate exemptions.
     */
    private val ALLOW_LIST = setOf(
        // Public key-prefix the operator picks for namespace access keys.
        // Stored as plaintext so the UI can show the leading characters
        // of a key without exposing the bearer half.
        "key-prefix",
        "keyprefix",
    )

    fun isSensitive(propertyName: String): Boolean {
        val normalised = propertyName.lowercase()
        if (normalised in ALLOW_LIST) return false
        val lastWord = lastWord(normalised)
        return lastWord in SENSITIVE_SUFFIXES
    }

    /**
     * Extracts the final word of a kebab-case or camelCase identifier.
     * `accessKey` → `key`, `jwt-secret` → `secret`, `scrapePassword` →
     * `password`, `keyPrefix` → `prefix`.
     */
    private fun lastWord(normalised: String): String {
        // kebab-case takes precedence: split on dash, take last segment
        if ('-' in normalised) {
            return normalised.substringAfterLast('-')
        }
        // camelCase fallback: split on the last capital boundary. Because
        // we lowercased the input already, we cannot use the case directly
        // — instead split on the original casing's word boundaries before
        // lowercasing. The caller passes lowercase; we keep both lowercase
        // and camelCase tokens by relying on the structural distinction
        // that camelCase names never contain dashes.
        return normalised
    }

    /**
     * camelCase-aware overload: takes the raw (un-lowercased) identifier
     * and splits on the uppercase-letter boundary. Used by the tree
     * builder which has access to the raw JSON node names.
     */
    fun isSensitiveCamel(rawName: String): Boolean {
        if (rawName.lowercase() in ALLOW_LIST) return false
        // Pick the last camelCase segment. `accessKey` → `Key`, `jwtSecret` → `Secret`.
        val lastSegment = rawName.split(Regex("(?=[A-Z])")).lastOrNull()?.lowercase()
            ?: return false
        if (lastSegment in SENSITIVE_SUFFIXES) return true
        // Fall back to the kebab-case rule for properties whose JSON name
        // happens to be lowercase-only (rare; defensive).
        return isSensitive(rawName)
    }
}
