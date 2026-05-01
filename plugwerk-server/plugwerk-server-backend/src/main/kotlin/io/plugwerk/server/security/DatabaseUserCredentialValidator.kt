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
package io.plugwerk.server.security

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.UserRepository
import org.springframework.context.annotation.Primary
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Validates username/password credentials against `plugwerk_user` for LOCAL users only.
 *
 * After the identity-hub split (#351), OIDC users have `source = 'OIDC'` and a NULL
 * `password_hash` — they cannot pass through this validator. The
 * `findByUsernameAndSource(username, LOCAL)` query also excludes them by construction
 * so the BCrypt comparison never runs against an OIDC row.
 *
 * Validation rules:
 * - The user must exist as a LOCAL row in `plugwerk_user`.
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
        val user = userRepository.findByUsernameAndSource(username, UserSource.INTERNAL).orElse(null) ?: return false
        if (!user.enabled) return false
        val hash = user.passwordHash ?: return false
        return passwordEncoder.matches(password, hash)
    }
}
