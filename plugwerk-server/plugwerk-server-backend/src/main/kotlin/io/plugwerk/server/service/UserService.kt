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
import io.plugwerk.server.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val namespaceMemberRepository: NamespaceMemberRepository,
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
        if (userRepository.existsByUsernameAndSource(username, UserSource.INTERNAL)) {
            throw ConflictException("Username '$username' is already taken")
        }
        return userRepository.save(
            UserEntity(
                username = username,
                email = normalizeEmail(email),
                displayName = displayName ?: username,
                source = UserSource.INTERNAL,
                passwordHash = hashPassword(password),
                passwordChangeRequired = true,
            ),
        )
    }

    /**
     * Self-registration counterpart to [create] (#420).
     *
     * Differences vs. admin-create:
     *  - `enabled` is a parameter (false when verification is required;
     *    true when the operator has turned verification off).
     *  - `passwordChangeRequired` is always `false` — the user just chose
     *    the password themselves; forcing an immediate change makes no
     *    sense.
     *  - Email uniqueness is checked explicitly so the call surfaces a
     *    [ConflictException] (caller silently swallows for anti-enumeration)
     *    instead of bubbling a `DataIntegrityViolationException` from the
     *    DB unique index.
     *
     * Username + email validation rules are otherwise identical to
     * [create] — both go through the same partial unique indexes for
     * INTERNAL rows.
     */
    fun createSelfRegistered(
        username: String,
        email: String,
        password: String,
        displayName: String?,
        enabled: Boolean,
    ): UserEntity {
        val normalisedEmail = normalizeEmail(email)
        if (userRepository.existsByUsernameAndSource(username, UserSource.INTERNAL)) {
            throw ConflictException("Username '$username' is already taken")
        }
        if (userRepository.existsByEmailAndSourceIgnoreCase(normalisedEmail, UserSource.INTERNAL)) {
            throw ConflictException("Email '$normalisedEmail' is already registered")
        }
        return userRepository.save(
            UserEntity(
                username = username,
                email = normalisedEmail,
                displayName = displayName ?: username,
                source = UserSource.INTERNAL,
                passwordHash = hashPassword(password),
                passwordChangeRequired = false,
                enabled = enabled,
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

    /**
     * Records a fresh, credential-validated login (issue #367). Sole writer for
     * `plugwerk_user.last_login_at`. Callers:
     *   - [io.plugwerk.server.controller.AuthController.login] (LOCAL credential validation succeeds).
     *   - [io.plugwerk.server.service.OidcIdentityService.upsertOnLogin] (OIDC callback succeeds, both first-login and existing-binding paths).
     *
     * Explicitly NOT called from `/auth/refresh`, the resource-server bearer-token
     * filter, or the namespace API-key filter — those would inflate the value
     * into uselessness. Negative-test guarded.
     *
     * @param at injectable for tests; defaults to `OffsetDateTime.now(ZoneOffset.UTC)`.
     */
    fun bumpLastLogin(id: UUID, at: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): UserEntity {
        val user = findById(id)
        user.lastLoginAt = at
        return userRepository.save(user)
    }

    fun resetPassword(id: UUID, newPassword: String): UserEntity {
        val user = findById(id)
        require(user.isInternal()) {
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
        // namespace_member rows cascade via the FK from migration 0017;
        // revoked_token rows cascade via the FK from migration 0024 (#422).
        // The explicit namespaceMemberRepository sweep is kept defensively in
        // case the FK is ever loosened — it is a no-op when CASCADE has
        // already done its work.
        namespaceMemberRepository.deleteAllByUserId(id)
        userRepository.delete(user)
    }

    fun changePassword(userId: UUID, currentPassword: String, newPassword: String): UserEntity {
        val user = findById(userId)
        require(user.isInternal()) {
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
