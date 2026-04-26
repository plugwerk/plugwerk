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
package io.plugwerk.server.service

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.RevokedTokenRepository
import io.plugwerk.server.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val namespaceMemberRepository: NamespaceMemberRepository,
    private val revokedTokenRepository: RevokedTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenRevocationService: TokenRevocationService,
) {

    @Transactional(readOnly = true)
    fun findAll(): List<UserEntity> = userRepository.findAll()

    @Transactional(readOnly = true)
    fun findAllByEnabled(enabled: Boolean): List<UserEntity> = userRepository.findAllByEnabled(enabled)

    @Transactional(readOnly = true)
    fun findById(id: UUID): UserEntity =
        userRepository.findById(id).orElseThrow { EntityNotFoundException("User", id.toString()) }

    /**
     * Creates a LOCAL user (`source = 'LOCAL'`) with username + password. After
     * the identity-hub split (#351), `email` is mandatory and `displayName`
     * defaults to the username when not supplied — both go through the
     * `chk_plugwerk_user_credentials` CHECK constraint and the partial unique
     * indexes for LOCAL rows.
     */
    fun create(username: String, email: String, password: String, displayName: String? = null): UserEntity {
        if (userRepository.existsByUsernameAndSource(username, UserSource.LOCAL)) {
            throw ConflictException("Username '$username' is already taken")
        }
        return userRepository.save(
            UserEntity(
                username = username,
                email = normalizeEmail(email),
                displayName = displayName ?: username,
                source = UserSource.LOCAL,
                passwordHash = hashPassword(password),
                passwordChangeRequired = true,
            ),
        )
    }

    /**
     * Trims surrounding whitespace and lowercases the address. Pairs with the partial
     * functional unique index `uq_plugwerk_user_email_local` on
     * `LOWER(email) WHERE source='LOCAL'` (migration 0017). The DB index is the
     * defence-in-depth — direct SQL or a future second writer cannot bypass it —
     * but normalising on write keeps the stored value canonical and avoids
     * surprising display roundtrips.
     */
    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun setEnabled(id: UUID, enabled: Boolean): UserEntity {
        val user = findById(id)
        user.enabled = enabled
        return userRepository.save(user)
    }

    fun resetPassword(id: UUID, newPassword: String): UserEntity {
        val user = findById(id)
        require(user.isLocal()) {
            "Cannot reset password on OIDC-sourced user — credentials live with the upstream provider"
        }
        user.passwordHash = hashPassword(newPassword)
        user.passwordChangeRequired = true
        val saved = userRepository.save(user)
        tokenRevocationService.revokeAllForUser(id)
        return saved
    }

    fun delete(id: UUID) {
        val user = findById(id)
        if (user.isSuperadmin) throw ForbiddenException("The superadmin account cannot be deleted")
        // namespace_member rows cascade automatically via the FK introduced in
        // migration 0017; revoked_token rows for this subject (now keyed by
        // plugwerk_user.id UUID-string) are wiped explicitly so the audit
        // surface for the deleted user does not linger.
        revokedTokenRepository.deleteBySubject(id.toString())
        namespaceMemberRepository.deleteAllByUserId(id)
        userRepository.delete(user)
    }

    fun changePassword(userId: UUID, currentPassword: String, newPassword: String): UserEntity {
        val user = findById(userId)
        require(user.isLocal()) {
            "Cannot change password on OIDC-sourced user — credentials live with the upstream provider"
        }
        val currentHash = user.passwordHash
            ?: throw UnauthorizedException("Current password is incorrect")
        if (!passwordEncoder.matches(currentPassword, currentHash)) {
            throw UnauthorizedException("Current password is incorrect")
        }
        user.passwordHash = hashPassword(newPassword)
        user.passwordChangeRequired = false
        return userRepository.save(user)
    }

    /**
     * Local wrapper around [PasswordEncoder.encode] whose return type is a Kotlin
     * platform type (Java SDK method). The Spring Security contract specifies a
     * non-null return for any non-null input; [requireNotNull] fails loudly instead
     * of a raw `!!` NullPointerException if that contract is ever violated.
     */
    private fun hashPassword(raw: CharSequence): String = requireNotNull(passwordEncoder.encode(raw)) {
        "PasswordEncoder.encode() returned null — violates Spring Security contract"
    }
}
