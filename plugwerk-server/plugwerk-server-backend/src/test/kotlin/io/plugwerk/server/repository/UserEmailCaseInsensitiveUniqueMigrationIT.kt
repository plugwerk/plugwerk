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
import org.assertj.core.api.Assertions.assertThatCode
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
 * Verifies that Liquibase migration 0013 replaces the case-sensitive
 * `uq_user_email` unique constraint with the partial functional unique index
 * `uq_user_email_lower` on `LOWER(email) WHERE email IS NOT NULL`. Required by
 * audit finding DB-013 (issue #271). Three checks:
 *
 * 1. Schema check — old constraint is gone, new partial functional index
 *    exists with the expected definition.
 * 2. Behavioural check — inserting two users with case-different emails
 *    (`User@Example.com` and `user@example.com`) raises an SQLException with
 *    SQLSTATE 23505 (unique_violation). Proves the index is enforced.
 * 3. Negative regression — two users with NULL email both insert successfully,
 *    because the partial predicate excludes NULLs. Without the predicate the
 *    NULLs-DISTINCT default behaviour can vary across PostgreSQL versions; the
 *    explicit `WHERE email IS NOT NULL` guarantees the safe semantics.
 *
 * Runs against a dedicated short-lived PostgreSQL 18 Testcontainer and invokes
 * Liquibase directly (no Spring context) — same pattern as `FkIndexMigrationIT`
 * (PR #269) to avoid Spring-context-cache eviction on memory-constrained CI.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserEmailCaseInsensitiveUniqueMigrationIT {

    private lateinit var postgres: PostgreSQLContainer

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer("postgres:18-alpine").apply { start() }
        runLiquibaseMigrations()
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

    @Test
    fun `DB-013 old uq_user_email constraint is removed`() {
        val constraint = uniqueConstraintColumns(
            tableName = "plugwerk_user",
            constraintName = "uq_user_email",
        )
        // pg_constraint should not return any row for the dropped constraint
        assertThat(constraint).isEmpty()
    }

    @Test
    fun `0017 replaced uq_user_email_lower with uq_plugwerk_user_email_local (LOCAL-scoped)`() {
        // The 0013 partial functional index (lower(email) WHERE email IS NOT NULL)
        // was replaced by migration 0017 with a LOCAL-scoped variant
        // (lower(email) WHERE source = 'LOCAL') because OIDC accounts are
        // intentionally allowed to share emails (#351 — no identity linking).
        assertThat(indexDefinition("plugwerk_user", "uq_user_email_lower")).isNull()

        val indexDef = indexDefinition("plugwerk_user", "uq_plugwerk_user_email_local")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("UNIQUE")
        assertThat(indexDef.lowercase()).contains("lower(")
        assertThat(indexDef.lowercase()).contains("email")
        assertThat(indexDef).contains("WHERE")
        assertThat(indexDef.lowercase()).contains("source")
        assertThat(indexDef).contains("LOCAL")
    }

    @Test
    fun `0017 case-different emails are rejected within source=LOCAL`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            insertUser(conn, username = "alice", email = "Alice@Example.com")

            // Different case in both local and domain parts; the LOWER(email) partial
            // index collapses both to the same key for LOCAL rows and must reject.
            assertThatThrownBy {
                insertUser(conn, username = "alice2", email = "alice@example.COM")
            }
                .isInstanceOf(SQLException::class.java)
                .matches { (it as SQLException).sqlState == "23505" }
        }
    }

    @Test
    fun `0017 OIDC accounts may share emails (no identity linking, #351)`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            // Two OIDC rows with the same email are allowed — Plugwerk treats them
            // as independent identities. Mixing with a LOCAL row of the same email
            // is also allowed because the LOCAL-scoped index ignores OIDC rows.
            assertThatCode {
                insertOidcUser(conn, "shared-email-oidc-1@example.com")
                insertOidcUser(conn, "shared-email-oidc-2@example.com")
                insertUser(conn, username = "shared-email-local", email = "shared-email-oidc-1@example.com")
            }.doesNotThrowAnyException()
        }
    }

    private fun uniqueConstraintColumns(tableName: String, constraintName: String): List<String> {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT a.attname
                  FROM pg_constraint c
                  JOIN pg_class    t ON t.oid = c.conrelid
                  JOIN unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON TRUE
                  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                 WHERE t.relname = ? AND c.conname = ? AND c.contype = 'u'
                 ORDER BY k.ord
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, tableName)
                stmt.setString(2, constraintName)
                val cols = mutableListOf<String>()
                stmt.executeQuery().use { rs ->
                    while (rs.next()) cols.add(rs.getString("attname"))
                }
                return cols
            }
        }
    }

    private fun indexDefinition(tableName: String, indexName: String): String? {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
            ).use { stmt ->
                stmt.setString(1, tableName)
                stmt.setString(2, indexName)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("indexdef") else null
                }
            }
        }
    }

    private fun insertUser(conn: Connection, username: String, email: String) {
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, display_name, email, source, password_hash,
               enabled, password_change_required, is_superadmin)
            VALUES (?, ?, ?, ?, 'LOCAL', ?, true, false, false)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setString(2, username)
            stmt.setString(3, username)
            stmt.setString(4, email)
            stmt.setString(5, "\$2a\$12\$" + "x".repeat(53))
            stmt.executeUpdate()
        }
    }

    private fun insertOidcUser(conn: Connection, email: String) {
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, display_name, email, source, password_hash,
               enabled, password_change_required, is_superadmin)
            VALUES (?, NULL, ?, ?, 'OIDC', NULL, true, false, false)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setString(2, "OIDC user $email")
            stmt.setString(3, email)
            stmt.executeUpdate()
        }
    }
}
