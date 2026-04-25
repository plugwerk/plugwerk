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

import io.plugwerk.server.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * Behavioural tests for [UserService.create]. The focus is the email-normalisation
 * contract introduced for DB-013 / #271 — uniqueness in PostgreSQL is enforced via
 * a partial functional index on `LOWER(email) WHERE email IS NOT NULL`, and the
 * service normalises the value on write so that the stored representation is
 * always canonical (trimmed and lowercase) and blank inputs collapse to `null`.
 *
 * Uses an H2 in-memory database via Spring Boot — same pattern as
 * [RefreshTokenServiceTest]. The DB-level functional unique index is verified
 * separately by `UserEmailCaseInsensitiveUniqueMigrationIT` against PostgreSQL,
 * because H2 does not run the Liquibase migration in this profile.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:user-service-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class UserServiceTest {

    @Autowired private lateinit var service: UserService

    @Autowired private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
    }

    @Test
    fun `create normalises mixed-case email to lowercase (DB-013 #271)`() {
        val saved = service.create("alice", "Alice@Example.COM", "password-123")

        assertThat(saved.email).isEqualTo("alice@example.com")
        // Re-read from the repository to confirm the normalised value is what
        // actually landed in the database, not just on the returned entity.
        val reloaded = userRepository.findById(requireNotNull(saved.id)).orElseThrow()
        assertThat(reloaded.email).isEqualTo("alice@example.com")
    }

    @Test
    fun `create trims surrounding whitespace from email`() {
        val saved = service.create("bob", "  Bob@Example.COM  ", "password-123")

        assertThat(saved.email).isEqualTo("bob@example.com")
    }

    @Test
    fun `create stores null when email is null`() {
        val saved = service.create("carol", null, "password-123")

        assertThat(saved.email).isNull()
    }

    @Test
    fun `create stores null when email is empty string`() {
        val saved = service.create("dave", "", "password-123")

        assertThat(saved.email).isNull()
    }

    @Test
    fun `create stores null when email is whitespace only`() {
        // A whitespace-only input must not produce an empty-string email row;
        // collapse to null so it does not collide with the partial unique
        // index's WHERE email IS NOT NULL predicate either.
        val saved = service.create("eve", "   ", "password-123")

        assertThat(saved.email).isNull()
    }

    @Test
    fun `create throws ConflictException when username is already taken`() {
        service.create("frank", "frank@example.com", "password-123")

        assertThatThrownBy { service.create("frank", "frank2@example.com", "password-123") }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("frank")
    }
}
