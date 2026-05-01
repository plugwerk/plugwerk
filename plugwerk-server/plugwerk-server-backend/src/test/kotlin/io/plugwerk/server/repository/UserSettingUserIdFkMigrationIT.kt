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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

/**
 * Verifies migration 0019 — `user_setting.user_subject` (varchar) is replaced
 * by a `user_id` UUID FK to `plugwerk_user(id)` with `ON DELETE CASCADE`
 * (issue #360). The migration finishes the job migration 0017 started: 0017
 * rewrote the *values* in `user_subject` to UUID strings, 0019 tightens the
 * *schema* to match.
 *
 * Same Testcontainers-without-Spring pattern as `IdentityHubSplitMigrationIT`:
 * we apply Liquibase up through 0018, seed pre-0019 fixture data with
 * UUID-shaped `user_subject` values, then apply 0019 and assert the resulting
 * schema + data + CASCADE behaviour.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserSettingUserIdFkMigrationIT {

    private lateinit var postgres: PostgreSQLContainer

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer("postgres:18-alpine").apply { start() }
        // Apply through 0018, then seed.
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            runLiquibaseUpTo(conn, "0019_user_setting_user_id_fk.yaml")
            seedPreMigrationData(conn)
        }
        // Apply 0019.
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            runLiquibaseUpdate(conn)
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    /**
     * Apply EVERYTHING then roll back the changesets in [stopBeforeFile] —
     * mirrors the pattern from `IdentityHubSplitMigrationIT` and is safe
     * because 0019 only touches `user_setting`, which `runLiquibaseUpdate`
     * later re-applies cleanly.
     */
    private fun runLiquibaseUpTo(conn: Connection, stopBeforeFile: String) {
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
        Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), database).use { liquibase ->
            liquibase.update(Contexts(), LabelExpression())
            val changesetCount = liquibase.databaseChangeLog.changeSets
                .count { it.filePath.endsWith(stopBeforeFile) }
            check(changesetCount > 0) { "no changesets found in $stopBeforeFile" }
            liquibase.rollback(changesetCount, "")
        }
    }

    private fun runLiquibaseUpdate(conn: Connection) {
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
        Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), database).use { liquibase ->
            liquibase.update(Contexts(), LabelExpression())
        }
    }

    /**
     * Seeds two `plugwerk_user` rows (FK target) and four `user_setting` rows:
     * three for [userId] (covering the multi-row case the unique constraint
     * has to handle) and one for [otherUserId] (to assert the CASCADE delete
     * is scoped, not global).
     */
    private fun seedPreMigrationData(conn: Connection) {
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, source, display_name,
               enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, 'alice', 'alice@example.test', 'hash', 'INTERNAL', 'Alice',
              true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, userId)
            stmt.executeUpdate()
        }
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, source, display_name,
               enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, 'bob', 'bob@example.test', 'hash', 'INTERNAL', 'Bob',
              true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, otherUserId)
            stmt.executeUpdate()
        }

        // Three settings for alice — uses UUID-shaped strings exactly like
        // the post-0017 production data shape.
        listOf("timezone" to "UTC", "default-namespace" to "ns1", "theme" to "dark").forEach { (k, v) ->
            conn.prepareStatement(
                """
                INSERT INTO user_setting (id, user_subject, setting_key, setting_value, updated_at)
                VALUES (?, ?, ?, ?, now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setString(2, userId.toString())
                stmt.setString(3, k)
                stmt.setString(4, v)
                stmt.executeUpdate()
            }
        }

        // One setting for bob — survives if alice is deleted; gets removed if
        // bob himself is deleted.
        conn.prepareStatement(
            """
            INSERT INTO user_setting (id, user_subject, setting_key, setting_value, updated_at)
            VALUES (?, ?, 'timezone', 'Europe/Berlin', now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setString(2, otherUserId.toString())
            stmt.executeUpdate()
        }
    }

    private inline fun <R> withConn(block: (Connection) -> R): R =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use(block)

    @Test
    fun `0019 drops the legacy user_subject column`() = withConn { conn ->
        conn.prepareStatement(
            """
            SELECT 1 FROM information_schema.columns
             WHERE table_name = 'user_setting' AND column_name = 'user_subject'
            """.trimIndent(),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                assertThat(rs.next()).`as`("user_subject column should be dropped").isFalse()
            }
        }
    }

    @Test
    fun `0019 backfills user_id from the previous user_subject UUID strings`() = withConn { conn ->
        conn.prepareStatement(
            "SELECT user_id FROM user_setting WHERE setting_key = 'timezone' AND setting_value = 'UTC'",
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                check(rs.next())
                assertThat(rs.getObject("user_id", UUID::class.java)).isEqualTo(userId)
            }
        }
    }

    @Test
    fun `0019 user_id column is NOT NULL with the expected uuid type`() = withConn { conn ->
        conn.prepareStatement(
            """
            SELECT is_nullable, data_type FROM information_schema.columns
             WHERE table_name = 'user_setting' AND column_name = 'user_id'
            """.trimIndent(),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                check(rs.next())
                assertThat(rs.getString("is_nullable")).isEqualTo("NO")
                assertThat(rs.getString("data_type")).isEqualTo("uuid")
            }
        }
    }

    @Test
    fun `0019 installs ON DELETE CASCADE FK on plugwerk_user`() = withConn { conn ->
        conn.prepareStatement(
            """
            SELECT confdeltype FROM pg_constraint
             WHERE conname = 'fk_user_setting_user'
            """.trimIndent(),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                check(rs.next()) { "fk_user_setting_user constraint should exist" }
                // 'c' = CASCADE in pg_constraint.confdeltype.
                assertThat(rs.getString("confdeltype")).isEqualTo("c")
            }
        }
    }

    @Test
    fun `0019 unique constraint on user_id+setting_key blocks duplicates`() = withConn { conn ->
        assertThatThrownBy {
            conn.prepareStatement(
                """
                INSERT INTO user_setting (id, user_id, setting_key, setting_value, updated_at)
                VALUES (?, ?, 'timezone', 'America/New_York', now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, userId)
                stmt.executeUpdate()
            }
        }.isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `0019 deleting a plugwerk_user CASCADE-removes their user_setting rows`() = withConn { conn ->
        // Sanity: alice has 3 settings before delete.
        val before = countSettingsFor(conn, userId)
        assertThat(before).isEqualTo(3)

        conn.prepareStatement("DELETE FROM plugwerk_user WHERE id = ?").use { stmt ->
            stmt.setObject(1, userId)
            stmt.executeUpdate()
        }

        // After delete: alice has 0 settings, bob still has his 1.
        assertThat(countSettingsFor(conn, userId)).isEqualTo(0)
        assertThat(countSettingsFor(conn, otherUserId)).isEqualTo(1)
    }

    private fun countSettingsFor(conn: Connection, uid: UUID): Int {
        conn.prepareStatement("SELECT count(*) FROM user_setting WHERE user_id = ?").use { stmt ->
            stmt.setObject(1, uid)
            stmt.executeQuery().use { rs ->
                rs.next()
                return rs.getInt(1)
            }
        }
    }
}
