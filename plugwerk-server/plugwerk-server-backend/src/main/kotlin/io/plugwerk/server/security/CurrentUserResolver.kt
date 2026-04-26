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
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.UnauthorizedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Resolves the currently-authenticated [UserEntity] from a Spring Security
 * [Authentication]. Single source of truth for "which Plugwerk user is on this
 * request?" introduced as part of the identity-hub split (#351).
 *
 * After #351, JWT-`sub` is the `plugwerk_user.id` UUID — never a username,
 * never the synthetic `<provider-uuid>:<sub>` string from the pre-#350 hack.
 * Everything that used to call `currentAuthentication().name` and pass the
 * result into `userRepository.findByUsername` should call this resolver
 * instead so the eventual JWT-format change does not have to fan out across
 * the codebase a second time.
 */
@Component
class CurrentUserResolver(private val userRepository: UserRepository) {

    /**
     * Returns the UUID encoded in the JWT `sub` claim (= `plugwerk_user.id`)
     * for the current request, or throws [UnauthorizedException] when no auth
     * is present or the `sub` is not a parseable UUID.
     *
     * `IllegalArgumentException` from `UUID.fromString` is caught and remapped:
     * a malformed `sub` is functionally indistinguishable from a forged token,
     * and the caller already wraps in a 401-handler.
     */
    fun currentUserId(): UUID = currentUserIdOf(currentAuthentication())

    /**
     * Returns the [UserEntity] for the currently-authenticated user. Throws
     * [UnauthorizedException] when no auth is present or the user no longer
     * exists in the database (e.g. row was deleted between token issuance
     * and this request).
     */
    fun currentUser(): UserEntity = userRepository.findById(currentUserId())
        .orElseThrow { UnauthorizedException("Authenticated user no longer exists") }

    /**
     * `currentUserId()` from a specific [Authentication] object. Useful in
     * filter chains that already have an [Authentication] in hand and want
     * to avoid a second `SecurityContextHolder` lookup.
     */
    fun currentUserIdOf(authentication: Authentication): UUID {
        val name = authentication.name
            ?: throw UnauthorizedException("Authentication carries no subject")
        return try {
            UUID.fromString(name)
        } catch (_: IllegalArgumentException) {
            throw UnauthorizedException("Authentication subject is not a valid Plugwerk user id")
        }
    }
}
