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
 * Verifies migration 0024 — `revoked_token.subject` (free text) becomes
 * `revoked_token.user_id` (uuid FK on plugwerk_user). Issue #422.
 *
 * Setup mirrors [IdentityHubSplitMigrationIT]: the changelog is applied up to
 * just-before 0024 using the rollback-on-update-to-target pattern, the
 * pre-migration fixture is seeded, then 0024 finishes the run. Each @Test
 * inspects a different property of the post-migration state.
 *
 * The HALT precondition (malformed subject) is not exercised here — it would
 * require a second container start and the same defensive pattern is already
 * proven by migration 0019. The cost of a second 30-second container outweighs
 * the marginal coverage win.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevokedTokenUserFkMigrationIT {

    private lateinit var postgres: PostgreSQLContainer

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    // Dedicated to the cascade test so its DELETE does not strip the row that
    // the backfill assertion depends on. JUnit does not guarantee test order
    // under @TestInstance.PER_CLASS and the suite shares one seeded fixture.
    private val cascadeUserId = UUID.randomUUID()
    private val revokedJtiHash = "a".repeat(64)
    private val otherJtiHash = "b".repeat(64)
    private val cascadeJtiHash = "c".repeat(64)

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer("postgres:18-alpine").apply { start() }
        // Liquibase.close() in `runLiquibaseUpTo` cascades to the JDBC connection,
        // so seed and update each open their own short-lived connection rather
        // than sharing one across phases.
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            runLiquibaseUpTo(conn, "0024_revoked_token_user_fk.yaml")
        }
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            seedPreMigrationData(conn)
        }
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            runLiquibaseUpdate(conn)
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    private fun runLiquibaseUpTo(conn: Connection, stopBeforeFile: String) {
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
        Liquibase(
            "db/changelog/db.changelog-master.yaml",
            ClassLoaderResourceAccessor(),
            database,
        ).use { liquibase ->
            liquibase.update(Contexts(), LabelExpression())
            val changesetCount = liquibase.databaseChangeLog
                .changeSets
                .count { it.filePath.endsWith(stopBeforeFile) }
            check(changesetCount > 0) { "no changesets found in $stopBeforeFile" }
            liquibase.rollback(changesetCount, "")
        }
    }

    private fun runLiquibaseUpdate(conn: Connection) {
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
        Liquibase(
            "db/changelog/db.changelog-master.yaml",
            ClassLoaderResourceAccessor(),
            database,
        ).use { liquibase ->
            liquibase.update(Contexts(), LabelExpression())
        }
    }

    /**
     * Pre-migration fixture: two users, two revoked_token rows whose `subject`
     * column already holds canonical UUID strings (the post-#351 invariant).
     * Both rows must carry over to the new `user_id` column intact.
     */
    private fun seedPreMigrationData(conn: Connection) {
        insertUser(conn, userId, "alice")
        insertUser(conn, otherUserId, "bob")
        insertUser(conn, cascadeUserId, "carol")
        insertRevokedToken(conn, revokedJtiHash, userId)
        insertRevokedToken(conn, otherJtiHash, otherUserId)
        insertRevokedToken(conn, cascadeJtiHash, cascadeUserId)
    }

    private fun insertUser(conn: Connection, id: UUID, username: String) {
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, source, display_name,
               enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, ?, ?, '${'$'}2a${'$'}12${'$'}fakebcrypt', 'INTERNAL', ?,
               true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setString(2, username)
            stmt.setString(3, "$username@example.test")
            stmt.setString(4, username)
            stmt.executeUpdate()
        }
    }

    private fun insertRevokedToken(conn: Connection, jtiHash: String, subjectUserId: UUID) {
        conn.prepareStatement(
            """
            INSERT INTO revoked_token (id, jti, subject, expires_at, revoked_at)
            VALUES (?, ?, ?, now() + interval '1 hour', now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setString(2, jtiHash)
            stmt.setString(3, subjectUserId.toString())
            stmt.executeUpdate()
        }
    }

    private inline fun <R> withConn(block: (Connection) -> R): R =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use(block)

    @Test
    fun `0024 drops the legacy subject column`() {
        withConn { conn ->
            conn.prepareStatement(
                """
                SELECT 1 FROM information_schema.columns
                 WHERE table_name = 'revoked_token' AND column_name = 'subject'
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).`as`("subject column should be dropped").isFalse()
                }
            }
        }
    }

    @Test
    fun `0024 adds user_id uuid NOT NULL column`() {
        withConn { conn ->
            conn.prepareStatement(
                """
                SELECT data_type, is_nullable FROM information_schema.columns
                 WHERE table_name = 'revoked_token' AND column_name = 'user_id'
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    check(rs.next()) { "user_id column missing" }
                    assertThat(rs.getString("data_type")).isEqualTo("uuid")
                    assertThat(rs.getString("is_nullable")).isEqualTo("NO")
                }
            }
        }
    }

    @Test
    fun `0024 backfills user_id from the legacy subject value for every existing row`() {
        withConn { conn ->
            // Asserts only on the alice + bob seed rows. The carol row (used by
            // the cascade test) is allowed to be present or absent — its fate
            // depends on test execution order, which JUnit does not guarantee.
            val rowsByJti = mutableMapOf<String, UUID>()
            conn.prepareStatement(
                "SELECT jti, user_id FROM revoked_token WHERE jti IN (?, ?)",
            ).use { stmt ->
                stmt.setString(1, revokedJtiHash)
                stmt.setString(2, otherJtiHash)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rowsByJti[rs.getString("jti")] = rs.getObject("user_id", UUID::class.java)
                    }
                }
            }
            assertThat(rowsByJti).containsOnly(
                java.util.Map.entry(revokedJtiHash, userId),
                java.util.Map.entry(otherJtiHash, otherUserId),
            )
        }
    }

    @Test
    fun `0024 installs fk_revoked_token_user with ON DELETE CASCADE`() {
        withConn { conn ->
            conn.prepareStatement(
                """
                SELECT confdeltype FROM pg_constraint
                 WHERE conname = 'fk_revoked_token_user'
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    check(rs.next()) { "fk_revoked_token_user is missing" }
                    // 'c' = CASCADE in pg_constraint.confdeltype; see Postgres docs.
                    assertThat(rs.getString("confdeltype")).isEqualTo("c")
                }
            }
        }
    }

    @Test
    fun `0024 installs idx_revoked_token_user_id`() {
        withConn { conn ->
            conn.prepareStatement(
                """
                SELECT 1 FROM pg_indexes
                 WHERE tablename = 'revoked_token' AND indexname = 'idx_revoked_token_user_id'
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).`as`("idx_revoked_token_user_id should exist").isTrue()
                }
            }
        }
    }

    @Test
    fun `0024 deleting plugwerk_user cascades to revoked_token rows`() {
        withConn { conn ->
            // Operates on the dedicated cascadeUserId fixture so the DELETE
            // does not affect rows that the backfill test asserts on.
            val before = conn.prepareStatement(
                "SELECT COUNT(*) FROM revoked_token WHERE user_id = ?",
            ).use { stmt ->
                stmt.setObject(1, cascadeUserId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            assertThat(before).isEqualTo(1)

            conn.prepareStatement("DELETE FROM plugwerk_user WHERE id = ?").use { stmt ->
                stmt.setObject(1, cascadeUserId)
                stmt.executeUpdate()
            }

            val after = conn.prepareStatement(
                "SELECT COUNT(*) FROM revoked_token WHERE user_id = ?",
            ).use { stmt ->
                stmt.setObject(1, cascadeUserId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            assertThat(after).`as`("CASCADE should have removed all revoked_token rows for the deleted user").isZero()
        }
    }
}
