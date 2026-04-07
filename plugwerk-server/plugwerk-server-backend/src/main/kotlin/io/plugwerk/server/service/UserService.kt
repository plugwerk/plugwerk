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

    fun create(username: String, email: String?, password: String): UserEntity {
        if (userRepository.existsByUsername(username)) {
            throw ConflictException("Username '$username' is already taken")
        }
        return userRepository.save(
            UserEntity(
                username = username,
                email = email,
                passwordHash = passwordEncoder.encode(password)!!,
                passwordChangeRequired = true,
            ),
        )
    }

    fun setEnabled(id: UUID, enabled: Boolean): UserEntity {
        val user = findById(id)
        user.enabled = enabled
        return userRepository.save(user)
    }

    fun resetPassword(id: UUID, newPassword: String): UserEntity {
        val user = findById(id)
        user.passwordHash = passwordEncoder.encode(newPassword)!!
        user.passwordChangeRequired = true
        val saved = userRepository.save(user)
        tokenRevocationService.revokeAllForUser(user.username)
        return saved
    }

    fun delete(id: UUID) {
        val user = findById(id)
        if (user.isSuperadmin) throw ForbiddenException("The superadmin account cannot be deleted")
        namespaceMemberRepository.deleteAllByUserSubject(user.username)
        revokedTokenRepository.deleteByUsername(user.username)
        userRepository.delete(user)
    }

    fun changePassword(username: String, currentPassword: String, newPassword: String): UserEntity {
        val user = userRepository.findByUsername(username)
            .orElseThrow { EntityNotFoundException("User", username) }
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw UnauthorizedException("Current password is incorrect")
        }
        user.passwordHash = passwordEncoder.encode(newPassword)!!
        user.passwordChangeRequired = false
        return userRepository.save(user)
    }
}
