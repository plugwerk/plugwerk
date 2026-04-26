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
import io.plugwerk.server.repository.RefreshTokenRepository
import io.plugwerk.server.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Behavioural tests for [RefreshTokenService] — issue, rotate, reuse detection, and
 * per-user revocation. Uses an H2 in-memory database via Spring Boot so the JPA /
 * HMAC / repository wiring is exercised end-to-end. Deliberately not an
 * integration test (no `@Tag("integration")`) — it runs in the main `test` task
 * alongside `ActuatorSecurityWith*IT` which use the same H2 setup.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:refresh-service-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        // Short validity so expiry tests do not sleep for real time.
        "plugwerk.auth.refresh-token-validity-hours=1",
    ],
)
class RefreshTokenServiceTest {

    @Autowired private lateinit var service: RefreshTokenService

    @Autowired private lateinit var repository: RefreshTokenRepository

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var username: String
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
        userRepository.deleteAll()
        username = "refresh-user-${UUID.randomUUID()}"
        val user = userRepository.save(
            UserEntity(
                username = username,
                displayName = username,
                email = "$username@refresh.test",
                source = io.plugwerk.server.domain.UserSource.LOCAL,
                passwordHash = passwordEncoder.encode("irrelevant")!!,
                enabled = true,
                passwordChangeRequired = false,
                isSuperadmin = false,
            ),
        )
        userId = requireNotNull(user.id)
    }

    @Test
    fun `issue returns plaintext and persists a lookup hash distinct from plaintext`() {
        val issued = service.issue(userId)

        assertThat(issued.plaintext).isNotBlank()
        assertThat(issued.plaintext.length).isGreaterThanOrEqualTo(40)
        val stored = repository.findAll().single()
        assertThat(stored.tokenLookupHash).isNotEqualTo(issued.plaintext)
        assertThat(stored.tokenLookupHash).hasSize(64)
        assertThat(stored.userId).isEqualTo(userId)
        assertThat(stored.familyId).isEqualTo(issued.familyId)
        assertThat(stored.revokedAt).isNull()
        assertThat(stored.expiresAt).isAfter(OffsetDateTime.now(ZoneOffset.UTC))
    }

    @Test
    fun `rotate revokes old row and issues successor in the same family`() {
        val first = service.issue(userId)

        val result = service.rotate(first.plaintext)

        assertThat(result).isInstanceOf(RefreshTokenService.RotationResult.Success::class.java)
        val success = result as RefreshTokenService.RotationResult.Success
        assertThat(success.issuedToken.plaintext).isNotEqualTo(first.plaintext)
        assertThat(success.issuedToken.familyId).isEqualTo(first.familyId)
        val rows = repository.findAll().sortedBy { it.issuedAt }
        assertThat(rows).hasSize(2)
        assertThat(rows[0].revokedAt).isNotNull
        assertThat(rows[0].revocationReason).isEqualTo("ROTATED")
        assertThat(rows[0].rotatedToId).isEqualTo(rows[1].id)
        assertThat(rows[1].revokedAt).isNull()
    }

    @Test
    fun `rotate with replayed revoked token revokes the whole family (reuse detection)`() {
        val first = service.issue(userId)
        // First rotation: valid, revokes the original and issues a successor.
        val secondResult = service.rotate(first.plaintext) as RefreshTokenService.RotationResult.Success

        // Second rotation with the ALREADY-revoked first plaintext → reuse.
        val replay = service.rotate(first.plaintext)

        assertThat(replay).isEqualTo(RefreshTokenService.RotationResult.Reused)
        val rows = repository.findAll()
        assertThat(rows).hasSize(2)
        // Both rows in the family must now be revoked (the successor gets force-revoked too).
        assertThat(rows.all { it.revokedAt != null }).isTrue()
        val reasons = rows.map { it.revocationReason }.toSet()
        assertThat(reasons).contains("REUSE_DETECTED")
    }

    @Test
    fun `rotate with unknown plaintext returns Unknown`() {
        val result = service.rotate("not-a-real-token-at-all")
        assertThat(result).isEqualTo(RefreshTokenService.RotationResult.Unknown)
    }

    @Test
    fun `rotate with expired row returns Unknown`() {
        // Insert a row directly with already-expired expiresAt — expiresAt is
        // updatable=false on the entity, so we cannot fabricate expiry by editing an
        // existing row; we have to create it expired in the first place.
        val plaintext = "expired-plaintext-for-test-${UUID.randomUUID()}"
        val hash = accessKeyHmac.compute(plaintext)
        repository.save(
            io.plugwerk.server.domain.RefreshTokenEntity(
                familyId = UUID.randomUUID(),
                userId = userId,
                tokenLookupHash = hash,
                issuedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2),
                expiresAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
            ),
        )

        val result = service.rotate(plaintext)

        assertThat(result).isEqualTo(RefreshTokenService.RotationResult.Unknown)
    }

    @Test
    fun `cleanupExpired purges rows past their expiry`() {
        // Direct insert with past expiry (expiresAt is updatable=false on the entity).
        val plaintext = "cleanup-plaintext-${UUID.randomUUID()}"
        val hash = accessKeyHmac.compute(plaintext)
        repository.save(
            io.plugwerk.server.domain.RefreshTokenEntity(
                familyId = UUID.randomUUID(),
                userId = userId,
                tokenLookupHash = hash,
                issuedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2),
                expiresAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
            ),
        )

        service.cleanupExpired()

        assertThat(repository.findAll()).isEmpty()
        // Sanity: issuing a fresh one still works after cleanup.
        assertThat(service.issue(userId).plaintext).isNotBlank()
    }

    /**
     * `AccessKeyHmac` is a Spring component whose HKDF-derived key (SBS-012 / #267)
     * must match the instance the service under test uses. Pulling the bean directly
     * from the context keeps the test honest — constructing a parallel instance with
     * a hand-rolled key would diverge silently if the derivation rules change.
     */
    @Autowired private lateinit var accessKeyHmac: io.plugwerk.server.security.AccessKeyHmac

    @Test
    fun `revokeAllForUser revokes every active row for the user`() {
        service.issue(userId)
        service.issue(userId)
        service.issue(userId)

        service.revokeAllForUser(userId)

        val rows = repository.findAll()
        assertThat(rows).hasSize(3)
        assertThat(rows.all { it.revokedAt != null }).isTrue()
        assertThat(rows.all { it.revocationReason == "LOGOUT" }).isTrue()
    }

    @Test
    fun `revokePresentedFamily revokes the whole family from a single plaintext`() {
        val issued = service.issue(userId)
        service.rotate(issued.plaintext) // successor in same family

        val revoked = service.revokePresentedFamily(issued.plaintext)

        // The original was already revoked by rotate(), but the successor is now also revoked.
        // Return value reflects "did at least one active row exist when we looked?"
        assertThat(revoked).isTrue()
        val rows = repository.findAll()
        assertThat(rows.all { it.revokedAt != null }).isTrue()
    }

    @Test
    fun `issue without upstreamIdToken stores NULL (local-login compatibility) (#352)`() {
        service.issue(userId)

        val row = repository.findAll().single()
        assertThat(row.upstreamIdToken).isNull()
    }

    @Test
    fun `issue with upstreamIdToken persists it on the row (#352)`() {
        val idToken = "eyJ.fake.id-token-payload-${UUID.randomUUID()}"
        service.issue(userId, idToken)

        val row = repository.findAll().single()
        assertThat(row.upstreamIdToken).isEqualTo(idToken)
    }

    @Test
    fun `rotate copies upstreamIdToken forward to the successor (#352)`() {
        val idToken = "eyJ.long-lived.${UUID.randomUUID()}"
        val first = service.issue(userId, idToken)

        val result = service.rotate(first.plaintext) as RefreshTokenService.RotationResult.Success
        val successorId = result.issuedToken.rowId
        val successor = repository.findById(successorId).orElseThrow()

        // The original row keeps its token (it stays on disk until expiry for reuse-detection),
        // and the successor must carry the same hint so a logout after many rotations still
        // has something to send to the IdP as id_token_hint.
        assertThat(successor.upstreamIdToken).isEqualTo(idToken)
    }

    @Test
    fun `findUpstreamIdToken returns the value for a presented OIDC plaintext (#352)`() {
        val idToken = "eyJ.find.${UUID.randomUUID()}"
        val issued = service.issue(userId, idToken)

        assertThat(service.findUpstreamIdToken(issued.plaintext)).isEqualTo(idToken)
    }

    @Test
    fun `findUpstreamIdToken returns null for a local-login plaintext (#352)`() {
        val issued = service.issue(userId) // no upstream ID token

        assertThat(service.findUpstreamIdToken(issued.plaintext)).isNull()
    }

    @Test
    fun `findUpstreamIdToken returns null for an unknown plaintext (#352)`() {
        assertThat(service.findUpstreamIdToken("not-a-real-token")).isNull()
    }
}
