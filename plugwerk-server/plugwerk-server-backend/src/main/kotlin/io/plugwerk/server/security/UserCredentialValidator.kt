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
package io.plugwerk.server.security

import io.plugwerk.server.PlugwerkProperties
import org.springframework.stereotype.Component

/**
 * Strategy interface for validating username/password credentials.
 *
 * Phase 1: implemented by [DevUserCredentialValidator] using a hardcoded list from config.
 * Phase 2+: replace with a database-backed implementation or delegate to an OIDC provider.
 */
interface UserCredentialValidator {
    fun validate(username: String, password: String): Boolean
}

/**
 * Validates credentials against the hardcoded dev-user list in [PlugwerkProperties.AuthProperties].
 *
 * This is a provisional implementation for Phase 1. Replace with a database-backed
 * validator or OIDC provider in Phase 2.
 */
@Component
class DevUserCredentialValidator(private val props: PlugwerkProperties) : UserCredentialValidator {

    override fun validate(username: String, password: String): Boolean =
        props.auth.devUsers.any { it.username == username && it.password == password }
}
