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

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * Pins the contract that [OidcIdentityService.upsertOnLogin] bumps
 * `plugwerk_user.last_login_at` on every successful callback (issue #367) —
 * for the existing-identity branch and the first-login
 * `createNewIdentityAndUser` branch.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:oidc-identity-service-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class OidcIdentityServiceTest {

    @Autowired private lateinit var service: OidcIdentityService

    @Autowired private lateinit var oidcProviderRepository: OidcProviderRepository

    @Autowired private lateinit var oidcIdentityRepository: OidcIdentityRepository

    @Autowired private lateinit var userRepository: UserRepository

    private lateinit var provider: OidcProviderEntity

    @BeforeEach
    fun setUp() {
        oidcIdentityRepository.deleteAll()
        userRepository.deleteAll()
        oidcProviderRepository.deleteAll()
        provider = oidcProviderRepository.save(
            OidcProviderEntity(
                name = "test-provider",
                providerType = OidcProviderType.OIDC,
                enabled = true,
                clientId = "client-id",
                clientSecretEncrypted = "encrypted-placeholder",
                issuerUri = "https://idp.example/",
            ),
        )
    }

    @Test
    fun `first-login creates identity and sets user lastLoginAt (#367)`() {
        val before = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)

        val user = service.upsertOnLogin(
            provider,
            subject = "alice-sub",
            claims = mapOf("email" to "alice@example.com", "name" to "Alice"),
        )

        assertThat(user.lastLoginAt)
            .isNotNull()
            .isAfterOrEqualTo(before.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
        // Reload to confirm the value was persisted, not just set on the
        // returned managed entity. H2 TIMESTAMPTZ truncates to microseconds,
        // OffsetDateTime.now() carries nanos — compare with millisecond fuzz
        // rather than strict equality.
        val reloaded = userRepository.findById(requireNotNull(user.id)).orElseThrow()
        assertThat(reloaded.lastLoginAt)
            .isCloseTo(
                user.lastLoginAt,
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS),
            )
    }

    @Test
    fun `existing-identity bumps plugwerk_user lastLoginAt (#367)`() {
        // First call provisions the identity.
        val initial = service.upsertOnLogin(
            provider,
            subject = "bob-sub",
            claims = mapOf("email" to "bob@example.com", "name" to "Bob"),
        )
        val initialUserStamp = requireNotNull(initial.lastLoginAt)

        // Sleep 10ms to make second-call timestamp strictly newer than the first.
        Thread.sleep(10)

        // Second call must bump both timestamps.
        val refreshed = service.upsertOnLogin(
            provider,
            subject = "bob-sub",
            claims = mapOf("email" to "bob@example.com", "name" to "Bob"),
        )

        assertThat(refreshed.lastLoginAt)
            .isNotNull()
            .isAfter(initialUserStamp)

        // Reload roundtrip: H2 truncates nanos → use millisecond fuzz.
        val reloadedUser = userRepository.findById(requireNotNull(refreshed.id)).orElseThrow()
        assertThat(reloadedUser.lastLoginAt)
            .isCloseTo(
                refreshed.lastLoginAt,
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS),
            )
    }
}
