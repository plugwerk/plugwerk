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
package io.plugwerk.server.config

import io.plugwerk.server.PlugwerkProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.stereotype.Component
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import java.net.URI

/**
 * Semantic validation for [PlugwerkProperties] beyond what JSR-303 annotations can express.
 *
 * - Rejects well-known insecure default values for auth secrets (copy-paste protection).
 * - Validates [PlugwerkProperties.ServerProperties.baseUrl] as a well-formed HTTP(S) URI
 *   to prevent SSRF/open-redirect via `plugins.json` download URLs.
 *
 * Registered as a Spring [Validator] bean; Spring Boot automatically invokes it during
 * `@ConfigurationProperties` binding because the target type matches [supports].
 */
@Component
@ConfigurationPropertiesBinding
class PlugwerkPropertiesValidator : Validator {

    companion object {
        val BLOCKED_JWT_SECRETS = setOf(
            "dev-secret-change-in-production-min32chars!!",
            "change-me-use-openssl-rand-base64-32",
            "my-super-secret-key-at-least-32-chars",
        )

        val BLOCKED_ENCRYPTION_KEYS = setOf(
            "change-me-16char",
            "0123456789abcdef",
        )

        private val ALLOWED_SCHEMES = setOf("http", "https")
    }

    override fun supports(clazz: Class<*>): Boolean = PlugwerkProperties::class.java.isAssignableFrom(clazz)

    override fun validate(target: Any, errors: Errors) {
        val props = target as PlugwerkProperties

        validateAuthSecrets(props.auth, errors)
        validateBaseUrl(props.server.baseUrl, errors)
    }

    private fun validateAuthSecrets(auth: PlugwerkProperties.AuthProperties, errors: Errors) {
        if (auth.jwtSecret in BLOCKED_JWT_SECRETS) {
            errors.rejectValue(
                "auth.jwtSecret",
                "insecure.default",
                "plugwerk.auth.jwt-secret uses a known insecure default — " +
                    "generate a unique value with: openssl rand -base64 32",
            )
        }

        if (auth.encryptionKey in BLOCKED_ENCRYPTION_KEYS) {
            errors.rejectValue(
                "auth.encryptionKey",
                "insecure.default",
                "plugwerk.auth.encryption-key uses a known insecure default — " +
                    "generate a unique value with: openssl rand -hex 8",
            )
        }
    }

    private fun validateBaseUrl(baseUrl: String, errors: Errors) {
        if (baseUrl.isBlank()) {
            errors.rejectValue("server.baseUrl", "invalid.base-url", "plugwerk.server.base-url must not be blank")
            return
        }

        val uri = runCatching { URI(baseUrl) }.getOrElse {
            errors.rejectValue("server.baseUrl", "invalid.base-url", "plugwerk.server.base-url is not a valid URI")
            return
        }

        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) {
            errors.rejectValue(
                "server.baseUrl",
                "invalid.base-url",
                "plugwerk.server.base-url must use http or https scheme",
            )
            return
        }

        if (uri.host.isNullOrBlank()) {
            errors.rejectValue(
                "server.baseUrl",
                "invalid.base-url",
                "plugwerk.server.base-url must contain a valid host",
            )
            return
        }

        if (uri.query != null) {
            errors.rejectValue(
                "server.baseUrl",
                "invalid.base-url",
                "plugwerk.server.base-url must not contain a query string",
            )
        }

        if (uri.fragment != null) {
            errors.rejectValue(
                "server.baseUrl",
                "invalid.base-url",
                "plugwerk.server.base-url must not contain a fragment",
            )
        }

        if (baseUrl.endsWith("/")) {
            errors.rejectValue(
                "server.baseUrl",
                "invalid.base-url",
                "plugwerk.server.base-url must not end with a trailing slash",
            )
        }
    }
}
