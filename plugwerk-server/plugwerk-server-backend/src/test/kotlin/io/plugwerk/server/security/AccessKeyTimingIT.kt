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

import io.plugwerk.server.SharedPostgresContainer
import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import io.plugwerk.server.repository.NamespaceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Verifies that [NamespaceAccessKeyAuthFilter]'s lookup path takes approximately
 * the same time whether the presented key exists or not — the invariant
 * addressed by ADR-0024 / SBS-008 / #291.
 *
 * Runs at the service layer (repository + filter internals) against a real
 * Postgres container so the DB round-trip contributes realistic cost.
 * The assertion is ratio-based (max/min of the two medians < 2.0) rather
 * than absolute, so CI noise does not cause false positives.
 */
@Tag("integration")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AccessKeyHmac::class, AccessKeyTimingIT.TestConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessKeyTimingIT {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val pg = SharedPostgresContainer.instance
            registry.add("spring.datasource.url") { pg.jdbcUrl }
            registry.add("spring.datasource.username") { pg.username }
            registry.add("spring.datasource.password") { pg.password }
            registry.add("plugwerk.auth.jwt-secret") { "timing-test-jwt-secret-min-32-chars!!" }
            registry.add("plugwerk.auth.encryption-key") { "x".repeat(32) }
        }
    }

    @Autowired lateinit var namespaceRepository: NamespaceRepository

    @Autowired lateinit var accessKeyRepository: NamespaceAccessKeyRepository

    @Autowired lateinit var accessKeyHmac: AccessKeyHmac

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    private lateinit var existingPlainKey: String

    @BeforeAll
    fun setUp() {
        val ns = namespaceRepository.save(NamespaceEntity(slug = "timing-test-ns", name = "Timing Test"))
        existingPlainKey = "pwk_" + (1..40).joinToString("") { "abcdefghij"[(it * 3) % 10].toString() }
        accessKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = ns,
                keyHash = requireNotNull(passwordEncoder.encode(existingPlainKey)),
                keyLookupHash = accessKeyHmac.compute(existingPlainKey),
                keyPrefix = existingPlainKey.take(8),
                name = "timing-test-key",
            ),
        )
    }

    @Test
    fun `lookup latency is invariant between existing and non-existing HMAC`() {
        val samples = 300
        val warmup = 50

        // Warm up JIT + connection pool
        repeat(warmup) {
            accessKeyRepository.findByKeyLookupHashAndRevokedFalse(accessKeyHmac.compute(existingPlainKey))
            accessKeyRepository.findByKeyLookupHashAndRevokedFalse(accessKeyHmac.compute("pwk_nonexistent_warmup"))
        }

        val hitLatencies = LongArray(samples)
        val missLatencies = LongArray(samples)

        for (i in 0 until samples) {
            // Interleave hit/miss so wall-clock drift affects both equally.
            val hitStart = System.nanoTime()
            accessKeyRepository.findByKeyLookupHashAndRevokedFalse(accessKeyHmac.compute(existingPlainKey))
            hitLatencies[i] = System.nanoTime() - hitStart

            val missStart = System.nanoTime()
            accessKeyRepository.findByKeyLookupHashAndRevokedFalse(accessKeyHmac.compute("pwk_nonexistent_$i"))
            missLatencies[i] = System.nanoTime() - missStart
        }

        val hitMedian = median(hitLatencies)
        val missMedian = median(missLatencies)
        val ratio = maxOf(hitMedian, missMedian).toDouble() / minOf(hitMedian, missMedian).toDouble()

        // Pre-fix this ratio was >10× because existing-prefix SELECTs returned rows
        // while non-existing-prefix SELECTs returned empty result sets with different
        // cost profiles. After ADR-0024 the DB does a single indexed equality probe
        // with statistically equivalent cost for both cases. We assert < 2× to leave
        // headroom for CI noise while still falsifying the vulnerability.
        assertThat(ratio)
            .`as`("hit median=%d ns, miss median=%d ns, ratio=%.2f — must be < 2.0", hitMedian, missMedian, ratio)
            .isLessThan(2.0)
    }

    private fun median(values: LongArray): Long {
        val sorted = values.sortedArray()
        return sorted[sorted.size / 2]
    }
}
