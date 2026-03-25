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

import io.plugwerk.server.repository.UserRepository
import org.springframework.context.annotation.Primary
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Validates username/password credentials against the [plugwerk_user] database table.
 *
 * This implementation replaces [DevUserCredentialValidator] from Phase 1.
 * It is annotated with [@Primary] so Spring injects this bean wherever
 * [UserCredentialValidator] is required, even if the dev implementation is still present
 * on the classpath during migration.
 *
 * Validation rules:
 * - The user must exist in the database.
 * - The account must be enabled ([UserEntity.enabled] = `true`).
 * - The submitted password must match the stored BCrypt hash.
 */
@Primary
@Component
class DatabaseUserCredentialValidator(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : UserCredentialValidator {

    override fun validate(username: String, password: String): Boolean {
        val user = userRepository.findByUsername(username).orElse(null) ?: return false
        if (!user.enabled) return false
        return passwordEncoder.matches(password, user.passwordHash)
    }
}
