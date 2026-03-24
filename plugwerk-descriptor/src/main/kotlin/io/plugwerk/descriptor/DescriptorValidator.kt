/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.descriptor

private val PLUGIN_ID_REGEX = Regex("^[a-zA-Z0-9._-]{1,128}$")

// Each range part: optional operator + version, optionally followed by a hyphen range end
private val SEMVER_RANGE_PART = Regex("(>=?|<=?|~|\\^|=)?v?\\d+\\.\\d+(\\.\\d+)?([-+][\\w.]+)?")
private val SEMVER_RANGE_REGEX = Regex(
    "^\\s*${SEMVER_RANGE_PART.pattern}" +
        "(\\s+-\\s+${SEMVER_RANGE_PART.pattern})?" +
        "(\\s+${SEMVER_RANGE_PART.pattern}(\\s+-\\s+${SEMVER_RANGE_PART.pattern})?)*\\s*$",
)
private const val NAME_MAX_LENGTH = 255
private const val TAG_MAX_LENGTH = 64
private val URL_SCHEMES = setOf("http", "https")

internal object DescriptorValidator {

    /**
     * Validates the given [descriptor] and throws [DescriptorValidationException] if any
     * constraint is violated. The exception lists all violations at once.
     */
    fun validate(descriptor: PlugwerkDescriptor) {
        val violations = mutableListOf<String>()

        if (!PLUGIN_ID_REGEX.matches(descriptor.id)) {
            violations += "id '${descriptor.id}' must match pattern ${PLUGIN_ID_REGEX.pattern}"
        }

        if (descriptor.name.length > NAME_MAX_LENGTH) {
            violations += "name must not exceed $NAME_MAX_LENGTH characters (got ${descriptor.name.length})"
        }

        descriptor.homepage?.let { url ->
            if (!isValidHttpUrl(url)) violations += "homepage must be a valid http/https URL: '$url'"
        }
        descriptor.repository?.let { url ->
            if (!isValidHttpUrl(url)) violations += "repository must be a valid http/https URL: '$url'"
        }
        descriptor.icon?.let { url ->
            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (!isValidHttpUrl(url)) violations += "icon must be a valid http/https URL or a relative path: '$url'"
            }
        }

        descriptor.categories.forEachIndexed { i, cat ->
            if (cat.length > TAG_MAX_LENGTH) {
                violations += "categories[$i] must not exceed $TAG_MAX_LENGTH characters: '$cat'"
            }
        }
        descriptor.tags.forEachIndexed { i, tag ->
            if (tag.length > TAG_MAX_LENGTH) {
                violations += "tags[$i] must not exceed $TAG_MAX_LENGTH characters: '$tag'"
            }
        }

        descriptor.requiresSystemVersion?.let { range ->
            if (!isValidSemVerRange(range)) {
                violations += "requiresSystemVersion '$range' is not a valid SemVer range"
            }
        }

        if (violations.isNotEmpty()) throw DescriptorValidationException(violations)
    }

    private fun isValidHttpUrl(url: String): Boolean {
        return try {
            val parsed = java.net.URI(url)
            parsed.scheme in URL_SCHEMES && parsed.host != null && parsed.host.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidSemVerRange(range: String): Boolean {
        // Normalize plugwerk conjunction syntax (">=1.0.0 & <2.0.0") to space-separated form
        val normalized = range.replace(Regex("\\s*&\\s*"), " ")
        return SEMVER_RANGE_REGEX.matches(normalized)
    }
}
