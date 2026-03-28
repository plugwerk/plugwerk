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

/**
 * Rejects well-known insecure default values for [PlugwerkProperties.AuthProperties].
 *
 * JSR-303 annotations on [PlugwerkProperties.AuthProperties] enforce structural constraints
 * (non-blank, length). This validator adds a semantic blocklist so that copy-paste defaults
 * from documentation or old configuration files are caught at startup.
 *
 * Registered as a Spring [Validator] bean; Spring Boot automatically invokes it during
 * `@ConfigurationProperties` binding because the target type matches [supports].
 */
@Component
@ConfigurationPropertiesBinding
class AuthPropertiesValidator : Validator {

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
    }

    override fun supports(clazz: Class<*>): Boolean = PlugwerkProperties::class.java.isAssignableFrom(clazz)

    override fun validate(target: Any, errors: Errors) {
        val props = target as PlugwerkProperties
        val auth = props.auth

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
}
