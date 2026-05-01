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
package io.plugwerk.server.repository

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class UserRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var userRepository: UserRepository

    private fun localUser(username: String, email: String = "$username@example.test") = UserEntity(
        username = username,
        displayName = username,
        email = email,
        source = UserSource.INTERNAL,
        passwordHash = "\$2a\$12\$hash",
    )

    @Test
    fun `findByUsernameAndSource returns user when LOCAL row exists`() {
        userRepository.save(localUser("alice"))

        val found = userRepository.findByUsernameAndSource("alice", UserSource.INTERNAL)

        assertThat(found).isPresent
        assertThat(found.get().username).isEqualTo("alice")
    }

    @Test
    fun `findByUsernameAndSource returns empty when no LOCAL row exists`() {
        val found = userRepository.findByUsernameAndSource("nobody", UserSource.INTERNAL)

        assertThat(found).isEmpty
    }

    @Test
    fun `findByUsernameAndSource is case-sensitive`() {
        userRepository.save(localUser("alice"))

        assertThat(userRepository.findByUsernameAndSource("Alice", UserSource.INTERNAL)).isEmpty
        assertThat(userRepository.findByUsernameAndSource("ALICE", UserSource.INTERNAL)).isEmpty
    }

    @Test
    fun `existsByUsernameAndSource returns true for existing LOCAL row`() {
        userRepository.save(localUser("bob"))

        assertThat(userRepository.existsByUsernameAndSource("bob", UserSource.INTERNAL)).isTrue()
        assertThat(userRepository.existsByUsernameAndSource("notbob", UserSource.INTERNAL)).isFalse()
    }

    // Username-uniqueness for LOCAL rows is enforced via the partial unique
    // index `uq_plugwerk_user_username_local` on PostgreSQL (migration 0017).
    // The H2 test profile does not run Liquibase, and Hibernate does not
    // generate a column-level UNIQUE on `username` (it is nullable for OIDC
    // rows, and the partial-index semantics cannot be expressed at the
    // mapping level). Coverage moved to:
    //   - IdentityHubSplitMigrationIT (Testcontainers PostgreSQL)
    //   - UserServiceTest (write-side conflict detection via existsByUsernameAndSource)

    // Email-uniqueness coverage:
    //   - UserServiceTest covers write-side normalisation.
    //   - UserEmailCaseInsensitiveUniqueMigrationIT (#271) and
    //     IdentityHubSplitMigrationIT (#351) cover the partial functional unique
    //     index `uq_plugwerk_user_email_local` on PostgreSQL via Testcontainers.

    @Test
    fun `passwordChangeRequired defaults to false (LOCAL row created with default constructor)`() {
        val saved = userRepository.save(localUser("newuser"))

        // The default value on UserEntity is now false (it was only true historically
        // because UserService.create flips it to true on admin-created accounts).
        assertThat(saved.passwordChangeRequired).isFalse()
    }

    @Test
    fun `enabled defaults to true`() {
        val saved = userRepository.save(localUser("activeuser"))

        assertThat(saved.enabled).isTrue()
    }
}
