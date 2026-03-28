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

import io.plugwerk.spi.version.isValidSemVer

private val PLUGIN_ID_REGEX = Regex("^[a-zA-Z0-9._-]{1,128}$")

// Each range part: optional operator + version, optionally followed by a hyphen range end
private val SEMVER_RANGE_PART = Regex("(>=?|<=?|~|\\^|=)?v?\\d+\\.\\d+(\\.\\d+)?([-+][\\w.]+)?")
private val SEMVER_RANGE_REGEX = Regex(
    "^\\s*${SEMVER_RANGE_PART.pattern}" +
        "(\\s+-\\s+${SEMVER_RANGE_PART.pattern})?" +
        "(\\s+${SEMVER_RANGE_PART.pattern}(\\s+-\\s+${SEMVER_RANGE_PART.pattern})?)*\\s*$",
)

// Targeted patterns for dangerous HTML/script injection — not a generic "<" ban
private val DANGEROUS_HTML_REGEX = Regex(
    "<\\s*(script|iframe|object|embed|form|link|meta|base)\\b|" +
        "<\\s*svg[^>]*\\bon\\w+\\s*=|" +
        "javascript\\s*:",
    RegexOption.IGNORE_CASE,
)

private val URL_SCHEMES = setOf("http", "https")

object DescriptorValidator {

    const val PLUGIN_ID_MAX_LENGTH = 128
    const val VERSION_MAX_LENGTH = 100
    const val NAME_MAX_LENGTH = 255
    const val DESCRIPTION_MAX_LENGTH = 10_000
    const val AUTHOR_MAX_LENGTH = 255
    const val LICENSE_MAX_LENGTH = 100
    const val URL_MAX_LENGTH = 2048
    const val REQUIRES_SYSTEM_VERSION_MAX_LENGTH = 255
    const val TAG_MAX_LENGTH = 64
    const val DEPENDENCY_VERSION_MAX_LENGTH = 255
    const val MAX_CATEGORIES = 20
    const val MAX_TAGS = 50
    const val MAX_SCREENSHOTS = 20
    const val MAX_DEPENDENCIES = 100

    /**
     * Validates the given [descriptor] and throws [DescriptorValidationException] if any
     * constraint is violated. The exception lists all violations at once.
     */
    fun validate(descriptor: PlugwerkDescriptor) {
        val violations = mutableListOf<String>()

        // --- id ---
        if (!PLUGIN_ID_REGEX.matches(descriptor.id)) {
            violations += "id '${descriptor.id.take(200)}' must match pattern ${PLUGIN_ID_REGEX.pattern}"
        }

        // --- version ---
        if (descriptor.version.length > VERSION_MAX_LENGTH) {
            violations += "version must not exceed $VERSION_MAX_LENGTH characters (got ${descriptor.version.length})"
        }
        if (!descriptor.version.isValidSemVer()) {
            violations += "version '${descriptor.version.take(200)}' is not valid SemVer"
        }

        // --- name ---
        if (descriptor.name.length > NAME_MAX_LENGTH) {
            violations += "name must not exceed $NAME_MAX_LENGTH characters (got ${descriptor.name.length})"
        }
        checkNoHtml("name", descriptor.name, violations)

        // --- description ---
        descriptor.description?.let { value ->
            if (value.length > DESCRIPTION_MAX_LENGTH) {
                violations += "description must not exceed $DESCRIPTION_MAX_LENGTH characters (got ${value.length})"
            }
            checkNoHtml("description", value, violations)
        }

        // --- author ---
        descriptor.author?.let { value ->
            if (value.length > AUTHOR_MAX_LENGTH) {
                violations += "author must not exceed $AUTHOR_MAX_LENGTH characters (got ${value.length})"
            }
            checkNoHtml("author", value, violations)
        }

        // --- license ---
        descriptor.license?.let { value ->
            if (value.length > LICENSE_MAX_LENGTH) {
                violations += "license must not exceed $LICENSE_MAX_LENGTH characters (got ${value.length})"
            }
        }

        // --- URL fields ---
        descriptor.homepage?.let { url ->
            checkUrlField("homepage", url, violations)
        }
        descriptor.repository?.let { url ->
            checkUrlField("repository", url, violations)
        }
        descriptor.icon?.let { url ->
            if (url.length > URL_MAX_LENGTH) {
                violations += "icon must not exceed $URL_MAX_LENGTH characters (got ${url.length})"
            }
            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (!isValidHttpUrl(url)) violations += "icon must be a valid http/https URL or a relative path: '$url'"
            }
        }

        // --- categories ---
        if (descriptor.categories.size > MAX_CATEGORIES) {
            violations += "categories must not exceed $MAX_CATEGORIES entries (got ${descriptor.categories.size})"
        }
        descriptor.categories.forEachIndexed { i, cat ->
            if (cat.length > TAG_MAX_LENGTH) {
                violations += "categories[$i] must not exceed $TAG_MAX_LENGTH characters: '${cat.take(100)}'"
            }
        }

        // --- tags ---
        if (descriptor.tags.size > MAX_TAGS) {
            violations += "tags must not exceed $MAX_TAGS entries (got ${descriptor.tags.size})"
        }
        descriptor.tags.forEachIndexed { i, tag ->
            if (tag.length > TAG_MAX_LENGTH) {
                violations += "tags[$i] must not exceed $TAG_MAX_LENGTH characters: '${tag.take(100)}'"
            }
        }

        // --- screenshots ---
        if (descriptor.screenshots.size > MAX_SCREENSHOTS) {
            violations += "screenshots must not exceed $MAX_SCREENSHOTS entries (got ${descriptor.screenshots.size})"
        }
        descriptor.screenshots.forEachIndexed { i, url ->
            if (url.length > URL_MAX_LENGTH) {
                violations += "screenshots[$i] must not exceed $URL_MAX_LENGTH characters (got ${url.length})"
            }
            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (!isValidHttpUrl(url)) {
                    violations +=
                        "screenshots[$i] must be a valid http/https URL or a relative path: '$url'"
                }
            }
        }

        // --- requiresSystemVersion ---
        descriptor.requiresSystemVersion?.let { range ->
            if (range.length > REQUIRES_SYSTEM_VERSION_MAX_LENGTH) {
                violations +=
                    "requiresSystemVersion must not exceed $REQUIRES_SYSTEM_VERSION_MAX_LENGTH characters (got ${range.length})"
            }
            if (!isValidSemVerRange(range)) {
                violations += "requiresSystemVersion '${range.take(200)}' is not a valid SemVer range"
            }
        }

        // --- pluginDependencies ---
        if (descriptor.pluginDependencies.size > MAX_DEPENDENCIES) {
            violations +=
                "pluginDependencies must not exceed $MAX_DEPENDENCIES entries (got ${descriptor.pluginDependencies.size})"
        }
        descriptor.pluginDependencies.forEachIndexed { i, dep ->
            if (!PLUGIN_ID_REGEX.matches(dep.id)) {
                violations +=
                    "pluginDependencies[$i].id '${dep.id.take(200)}' must match pattern ${PLUGIN_ID_REGEX.pattern}"
            }
            if (dep.version.length > DEPENDENCY_VERSION_MAX_LENGTH) {
                violations +=
                    "pluginDependencies[$i].version must not exceed $DEPENDENCY_VERSION_MAX_LENGTH characters (got ${dep.version.length})"
            }
        }

        if (violations.isNotEmpty()) throw DescriptorValidationException(violations)
    }

    private fun checkUrlField(field: String, url: String, violations: MutableList<String>) {
        if (url.length > URL_MAX_LENGTH) {
            violations += "$field must not exceed $URL_MAX_LENGTH characters (got ${url.length})"
        }
        if (!isValidHttpUrl(url)) violations += "$field must be a valid http/https URL: '${url.take(200)}'"
    }

    private fun checkNoHtml(field: String, value: String, violations: MutableList<String>) {
        if (DANGEROUS_HTML_REGEX.containsMatchIn(value)) {
            violations += "$field must not contain HTML or script tags"
        }
    }

    private fun isValidHttpUrl(url: String): Boolean = try {
        val parsed = java.net.URI(url)
        parsed.scheme in URL_SCHEMES && parsed.host != null && parsed.host.isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private fun isValidSemVerRange(range: String): Boolean {
        // Normalize plugwerk conjunction syntax (">=1.0.0 & <2.0.0") to space-separated form
        val normalized = range.replace(Regex("\\s*&\\s*"), " ")
        return SEMVER_RANGE_REGEX.matches(normalized)
    }
}
