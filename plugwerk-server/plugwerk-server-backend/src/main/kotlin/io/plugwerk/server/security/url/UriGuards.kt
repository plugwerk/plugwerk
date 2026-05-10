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
package io.plugwerk.server.security.url

import java.net.URI
import java.net.URISyntaxException

/**
 * SSRF-aware URI validation primitives. Composes [HostClassifier] with
 * URI-syntax + scheme checks. The naming is intentional: [requirePublicHttpUri]
 * (vs. a generically-named "requireValidHttpUri") signals at every call-site
 * that this path actively rejects private/loopback/link-local/metadata hosts.
 */
object UriGuards {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /**
     * Validates that [value] parses as an `http(s)://host[/...]` URI whose
     * host is public-routable per [HostClassifier]. Throws
     * [IllegalArgumentException] with a stable, field-prefixed message on
     * any violation so the caller can include the message in user-facing
     * validation errors (write-time) or log it (read-time).
     *
     * `null` or blank input passes when [required] is `false` (matching the
     * existing patch-semantics in `OidcProviderService.update`).
     */
    fun requirePublicHttpUri(value: String?, fieldName: String, required: Boolean) {
        val host = parseHttpUriHostOrNull(value, fieldName, required) ?: return
        HostClassifier.requirePublicHost(host, fieldName)
    }

    /**
     * Same syntax + scheme + non-blank-host checks as [requirePublicHttpUri]
     * but **without** the SSRF host-class guard. Use only behind an explicit
     * dev/test escape hatch (#479: `plugwerk.security.oidc.allow-private-discovery-uris`).
     */
    fun requireHttpUri(value: String?, fieldName: String, required: Boolean) {
        parseHttpUriHostOrNull(value, fieldName, required)
    }

    private fun parseHttpUriHostOrNull(value: String?, fieldName: String, required: Boolean): String? {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) {
            require(!required) { "$fieldName is required" }
            return null
        }

        val parsed = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("$fieldName is not a valid URI: ${e.message}")
        }

        require(parsed.scheme?.lowercase() in ALLOWED_SCHEMES) {
            "$fieldName must use http or https scheme"
        }
        val host = parsed.host
        require(!host.isNullOrBlank()) {
            "$fieldName must include a host"
        }
        return host
    }
}
