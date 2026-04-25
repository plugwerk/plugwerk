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
    fun `DB-013 uq_user_email_lower exists as a partial functional index`() {
        val indexDef = indexDefinition(
            tableName = "plugwerk_user",
            indexName = "uq_user_email_lower",
        )
        assertThat(indexDef).isNotNull()
        // Postgres pg_indexes.indexdef shape:
        //   CREATE UNIQUE INDEX uq_user_email_lower
        //     ON public.plugwerk_user USING btree (lower((email)::text))
        //     WHERE (email IS NOT NULL)
        assertThat(indexDef!!).contains("UNIQUE")
        assertThat(indexDef.lowercase()).contains("lower(")
        assertThat(indexDef.lowercase()).contains("email")
        assertThat(indexDef).contains("WHERE")
        assertThat(indexDef.lowercase()).contains("is not null")
    }

    @Test
    fun `DB-013 case-different emails are rejected by the partial functional unique index`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            insertUser(conn, username = "alice", email = "Alice@Example.com")

            // Different case in both local and domain parts; the LOWER(email) index
            // collapses both to the same key and must reject the second insert.
            assertThatThrownBy {
                insertUser(conn, username = "alice2", email = "alice@example.COM")
            }
                .isInstanceOf(SQLException::class.java)
                .matches { (it as SQLException).sqlState == "23505" }
        }
    }

    @Test
    fun `DB-013 multiple users with NULL email are allowed (partial predicate excludes NULLs)`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            // Without the WHERE email IS NOT NULL predicate, the NULLS-DISTINCT
            // default behaviour can vary across PostgreSQL versions. The partial
            // index pins the safe semantics: NULLs are simply not in the index.
            assertThatCode {
                insertUser(conn, username = "no-email-1", email = null)
                insertUser(conn, username = "no-email-2", email = null)
                insertUser(conn, username = "no-email-3", email = null)
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

    private fun insertUser(conn: Connection, username: String, email: String?) {
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, enabled, password_change_required, is_superadmin)
            VALUES (?, ?, ?, ?, true, false, false)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setString(2, username)
            if (email == null) stmt.setNull(3, java.sql.Types.VARCHAR) else stmt.setString(3, email)
            stmt.setString(4, "\$2a\$12\$" + "x".repeat(53))
            stmt.executeUpdate()
        }
    }
}
