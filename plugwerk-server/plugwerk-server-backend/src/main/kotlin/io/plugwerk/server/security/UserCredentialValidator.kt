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
 * Provisional credential validator for Phase 1 development.
 *
 * @deprecated Replaced by [DatabaseUserCredentialValidator] in Phase 2. This class is kept
 * for reference only and is no longer active — [DatabaseUserCredentialValidator] is annotated
 * with [@Primary] and takes precedence.
 */
@Deprecated("Replaced by DatabaseUserCredentialValidator in Phase 2")
class DevUserCredentialValidator : UserCredentialValidator {
    override fun validate(username: String, password: String): Boolean = false
}
