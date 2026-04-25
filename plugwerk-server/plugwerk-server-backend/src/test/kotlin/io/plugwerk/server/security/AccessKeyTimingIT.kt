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

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Verifies that the access-key lookup path takes approximately the same time
 * whether the presented HMAC matches an existing row or not — the invariant
 * addressed by ADR-0024 / SBS-008 / #291.
 *
 * Runs without any Spring context so it cannot perturb the `@SpringBootTest`
 * context cache on memory-constrained CI runners (see PR #304/#307 for the
 * same flake when two context variants coexisted). The test spins up a
 * dedicated PostgreSQL 18 Testcontainer, applies the full Liquibase changelog,
 * inserts a single access-key row with a known HMAC, and then runs 300
 * interleaved hit/miss `SELECT` round-trips directly via JDBC — that is
 * exactly the DB round-trip whose timing the vulnerability hinged on.
 *
 * The assertion is ratio-based (max/min of the two medians < 2.0) so CI
 * jitter does not produce false positives. Pre-fix this ratio was >10× on
 * the old `key_prefix`-based lookup; post-fix it is close to 1.0.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessKeyTimingIT {

    private lateinit var postgres: PostgreSQLContainer
    private lateinit var existingLookupHash: String

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer("postgres:18-alpine").apply { start() }
        runLiquibaseMigrations()
        existingLookupHash = seedOneAccessKey()
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    private fun runLiquibaseMigrations() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            ).use { liquibase ->
                liquibase.update(Contexts(), LabelExpression())
            }
        }
    }

    private fun seedOneAccessKey(): String {
        val namespaceId = UUID.randomUUID()
        val accessKeyId = UUID.randomUUID()
        val lookupHash = "a".repeat(64)
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement("INSERT INTO namespace (id, slug, name) VALUES (?, ?, ?)").use { stmt ->
                stmt.setObject(1, namespaceId)
                stmt.setString(2, "timing-test-ns")
                stmt.setString(3, "Timing Test")
                stmt.executeUpdate()
            }
            conn.prepareStatement(
                """
                INSERT INTO namespace_access_key
                  (id, namespace_id, key_hash, key_lookup_hash, key_prefix, name, revoked)
                VALUES (?, ?, ?, ?, ?, ?, false)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, accessKeyId)
                stmt.setObject(2, namespaceId)
                stmt.setString(3, "\$2a\$10\$dummyBcryptHashForTimingTest000000000000000000000000000")
                stmt.setString(4, lookupHash)
                stmt.setString(5, "pwk_tmng")
                stmt.setString(6, "timing-test-key")
                stmt.executeUpdate()
            }
        }
        return lookupHash
    }

    @Test
    fun `lookup latency is invariant between existing and non-existing HMAC`() {
        val samples = 300
        val warmup = 50

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            val stmt = conn.prepareStatement(LOOKUP_SQL)
            repeat(warmup) {
                runLookup(stmt, existingLookupHash)
                runLookup(stmt, "0".repeat(64))
            }

            val hitLatencies = LongArray(samples)
            val missLatencies = LongArray(samples)
            for (i in 0 until samples) {
                val missHash = "%064x".format(i.toLong())
                val hitStart = System.nanoTime()
                runLookup(stmt, existingLookupHash)
                hitLatencies[i] = System.nanoTime() - hitStart

                val missStart = System.nanoTime()
                runLookup(stmt, missHash)
                missLatencies[i] = System.nanoTime() - missStart
            }

            val hitMedian = median(hitLatencies)
            val missMedian = median(missLatencies)
            val ratio = maxOf(hitMedian, missMedian).toDouble() / minOf(hitMedian, missMedian).toDouble()

            assertThat(ratio)
                .`as`(
                    "hit median=%d ns, miss median=%d ns, ratio=%.2f — must be < 2.0 (pre-fix ratio was >10× under the key_prefix lookup; post-fix it is close to 1.0)",
                    hitMedian,
                    missMedian,
                    ratio,
                )
                .isLessThan(2.0)
        }
    }

    private fun runLookup(stmt: java.sql.PreparedStatement, lookupHash: String) {
        stmt.setString(1, lookupHash)
        stmt.executeQuery().use { rs ->
            while (rs.next()) rs.getString("id")
        }
    }

    private fun median(values: LongArray): Long {
        val sorted = values.sortedArray()
        return sorted[sorted.size / 2]
    }

    companion object {
        private const val LOOKUP_SQL =
            "SELECT id FROM namespace_access_key WHERE key_lookup_hash = ? AND revoked = false"

        @Suppress("UNUSED_PARAMETER")
        private fun referenceForClassLoader(conn: Connection) = Unit
    }
}
