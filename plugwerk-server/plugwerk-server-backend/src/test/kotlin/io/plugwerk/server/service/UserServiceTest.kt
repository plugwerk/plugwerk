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

    // Pre-#351 the create signature accepted a nullable email and tests
    // exercised the empty-string/whitespace/null collapse paths. Since #351
    // email is mandatory: the OpenAPI request schema enforces it and the
    // service signature is `email: String`. The collapse paths therefore
    // cannot be exercised through this method anymore — the OpenAPI layer
    // rejects the request before reaching the service.

    @Test
    fun `create defaults displayName to username when not supplied`() {
        val saved = service.create("ash", "ash@example.com", "password-123")

        assertThat(saved.displayName).isEqualTo("ash")
    }

    @Test
    fun `create stores supplied displayName verbatim`() {
        val saved = service.create("blake", "blake@example.com", "password-123", displayName = "Blake the Brave")

        assertThat(saved.displayName).isEqualTo("Blake the Brave")
    }

    @Test
    fun `create throws ConflictException when username is already taken`() {
        service.create("frank", "frank@example.com", "password-123")

        assertThatThrownBy { service.create("frank", "frank2@example.com", "password-123") }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("frank")
    }

    @Test
    fun `bumpLastLogin persists the supplied timestamp and returns the refreshed entity (#367)`() {
        val created = service.create("greta", "greta@example.com", "password-123")
        assertThat(created.lastLoginAt).isNull() // never logged in yet

        val at = java.time.OffsetDateTime.parse("2026-04-15T08:30:00Z")
        val returned = service.bumpLastLogin(requireNotNull(created.id), at)

        assertThat(returned.lastLoginAt).isEqualTo(at)
        val reloaded = userRepository.findById(requireNotNull(created.id)).orElseThrow()
        assertThat(reloaded.lastLoginAt).isEqualTo(at)
    }

    @Test
    fun `bumpLastLogin throws EntityNotFoundException for unknown user id`() {
        assertThatThrownBy { service.bumpLastLogin(java.util.UUID.randomUUID()) }
            .isInstanceOf(EntityNotFoundException::class.java)
    }

    @Test
    fun `applyPasswordReset rehashes the password and leaves passwordChangeRequired false (#421)`() {
        val created = service.create("hank", "hank@example.com", "old-password-1234")

        val updated = service.applyPasswordReset(requireNotNull(created.id), "new-password-1234")

        assertThat(updated.passwordChangeRequired).isFalse()
        // Hash actually changed and is BCrypt-shaped (the format check is what
        // distinguishes "we ran the encoder" from "we stored plaintext", which
        // is the security-relevant property here).
        assertThat(updated.passwordHash).startsWith("\$2a\$")
        assertThat(updated.passwordHash).isNotEqualTo(created.passwordHash)
    }

    @Test
    fun `applyPasswordReset bumps passwordInvalidatedBefore so all existing sessions are revoked (#421)`() {
        val created = service.create("ivan", "ivan@example.com", "old-password-1234")
        assertThat(created.passwordInvalidatedBefore).isNull()

        service.applyPasswordReset(requireNotNull(created.id), "new-password-1234")

        val reloaded = userRepository.findById(requireNotNull(created.id)).orElseThrow()
        assertThat(reloaded.passwordInvalidatedBefore).isNotNull()
    }

    @Test
    fun `applyPasswordReset rejects EXTERNAL OIDC users — credentials live with the provider (#421)`() {
        val internal = service.create("jane", "jane@example.com", "any-password-1234")
        // Force the row to look EXTERNAL by mutating + re-saving. We don't go
        // through OidcIdentityService here because the assertion is purely
        // about UserService's branch on `user.isInternal()`.
        internal.source = io.plugwerk.server.domain.UserSource.EXTERNAL
        userRepository.save(internal)

        assertThatThrownBy {
            service.applyPasswordReset(requireNotNull(internal.id), "new-password-1234")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("OIDC-sourced")
    }

    // ---- Text search (#492) -----------------------------------------------

    @Test
    fun `search returns the unfiltered page when q is blank or null`() {
        // q=null and q="   " must behave identically to no search at all —
        // routes to findAll/findAllByEnabled directly, no LIKE query.
        repeat(3) { i -> service.create("user-$i", "user-$i@example.com", "password12345") }

        val nullQ = service.search(q = null, enabled = null, pageable = pageRequest())
        val blankQ = service.search(q = "   ", enabled = null, pageable = pageRequest())

        assertThat(nullQ.totalElements).isEqualTo(3)
        assertThat(blankQ.totalElements).isEqualTo(3)
    }

    @Test
    fun `search matches case-insensitively across username, displayName, and email`() {
        // One user per searchable field. Each match query must hit exactly
        // the user whose corresponding field contains the substring.
        service.create("alice", "alice@example.com", "password12345", displayName = "Alice")
        service.create("admin", "bob@example.com", "password12345", displayName = "Bob Builder")
        service.create("carol", "carol+plugwerk@example.com", "password12345", displayName = "Carol")

        // Hits username only (and case-insensitively).
        val byUsername = service.search(q = "ALI", enabled = null, pageable = pageRequest())
        assertThat(byUsername.content.map { it.username }).containsExactly("alice")

        // Hits displayName only — `Bob Builder` is the displayName of the
        // `admin` user, so the username column does not match `Builder`.
        val byDisplayName = service.search(q = "builder", enabled = null, pageable = pageRequest())
        assertThat(byDisplayName.content.map { it.username }).containsExactly("admin")

        // Hits email only — `+plugwerk` is in the email of `carol` and not
        // in their username or displayName.
        val byEmail = service.search(q = "+plugwerk", enabled = null, pageable = pageRequest())
        assertThat(byEmail.content.map { it.username }).containsExactly("carol")
    }

    @Test
    fun `search combines q with enabled filter`() {
        // Two users contain "test" in the username; one is enabled, one
        // disabled. q + enabled=true returns only the enabled match.
        val enabled = service.create("test-enabled", "te@example.com", "password12345")
        val disabled = service.create("test-disabled", "td@example.com", "password12345")
        // Disable one through the entity directly.
        disabled.enabled = false
        userRepository.save(disabled)

        val onlyEnabled = service.search(q = "test", enabled = true, pageable = pageRequest())

        assertThat(onlyEnabled.content.map { it.id })
            .containsExactly(requireNotNull(enabled.id))
    }

    @Test
    fun `search escapes SQL wildcard chars in q so percent and underscore are literal`() {
        // Three users whose names contain literal SQL wildcards. A search
        // for "100%" must match only the literal `100%user` row, not the
        // `alice` row (which would match if `%` were treated as a wildcard).
        service.create("alice", "alice@example.com", "password12345")
        service.create("a_b", "a_b@example.com", "password12345")
        // The literal "%" cannot live in a username (validation blocks it).
        // Use displayName to carry the wildcard for this test.
        val pctUser = service.create("pctuser", "pct@example.com", "password12345", displayName = "100%user")

        val pct = service.search(q = "100%", enabled = null, pageable = pageRequest())
        assertThat(pct.content.map { it.id }).containsExactly(requireNotNull(pctUser.id))

        val underscore = service.search(q = "_", enabled = null, pageable = pageRequest())
        assertThat(underscore.content.map { it.username }).containsExactly("a_b")
    }

    @Test
    fun `search handles a result set larger than 100 users (issue #492 reproduce)`() {
        // The 100-cap notnagel that #492 removes assumed nobody would
        // exceed 100 users. Seed 150 and assert that searches actually
        // reach users at indices 100+.
        repeat(150) { i ->
            service.create("user-${"%03d".format(i)}", "u$i@example.com", "password12345")
        }

        // user-149 lives well past index 100. Without server-side search
        // (the old client-side filter on the first 100 results) it would
        // be invisible. The pagination return path proves it is reachable.
        val tail = service.search(q = "user-149", enabled = null, pageable = pageRequest())

        assertThat(tail.totalElements).isEqualTo(1)
        assertThat(tail.content.single().username).isEqualTo("user-149")
    }

    private fun pageRequest() = org.springframework.data.domain.PageRequest.of(0, 50)
}
