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
 * Verifies that Liquibase migration 0012 installs the (namespace_id, name)
 * unique constraint on namespace_access_key required by audit finding DB-014
 * (issue #272). Two checks:
 *
 * 1. Schema check — the constraint exists with the expected name and column
 *    set, queried via pg_constraint.
 * 2. Behavioural check — inserting two access-key rows with the same
 *    (namespace_id, name) raises an SQLException with SQLSTATE 23505
 *    (unique_violation). This proves the constraint is actually enforced,
 *    not just listed in the catalog.
 *
 * Runs against a dedicated short-lived PostgreSQL 18 Testcontainer and
 * invokes Liquibase directly (no Spring context) — same pattern as
 * FkIndexMigrationIT (PR #269) to avoid Spring-context-cache eviction on
 * memory-constrained CI runners.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NamespaceAccessKeyUniqueConstraintMigrationIT {

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
    fun `DB-014 uq_namespace_access_key_ns_name exists on (namespace_id, name)`() {
        val constraintColumns = uniqueConstraintColumns(
            tableName = "namespace_access_key",
            constraintName = "uq_namespace_access_key_ns_name",
        )
        assertThat(constraintColumns).containsExactly("namespace_id", "name")
    }

    @Test
    fun `DB-014 duplicate (namespace_id, name) inserts are rejected by the database`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            val namespaceId = insertNamespace(conn, slug = "uq-test-ns")

            insertAccessKey(conn, namespaceId = namespaceId, name = "ci-pipeline", lookupHashSuffix = "a")

            // Second insert with the same (namespace_id, name) but otherwise distinct
            // values must violate the new unique constraint.
            assertThatThrownBy {
                insertAccessKey(conn, namespaceId = namespaceId, name = "ci-pipeline", lookupHashSuffix = "b")
            }
                .isInstanceOf(SQLException::class.java)
                .matches { (it as SQLException).sqlState == "23505" }
        }
    }

    @Test
    fun `DB-014 same name in a different namespace is allowed`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            val nsA = insertNamespace(conn, slug = "uq-test-ns-a")
            val nsB = insertNamespace(conn, slug = "uq-test-ns-b")

            // Same name "ci-pipeline" must be allowed across different namespaces —
            // the constraint is on the pair, not on `name` alone.
            insertAccessKey(conn, namespaceId = nsA, name = "ci-pipeline", lookupHashSuffix = "c")
            insertAccessKey(conn, namespaceId = nsB, name = "ci-pipeline", lookupHashSuffix = "d")
        }
    }

    private fun uniqueConstraintColumns(tableName: String, constraintName: String): List<String> {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            // pg_constraint.conkey is an int2[] of attribute numbers; resolve via pg_attribute
            // and preserve the array order so we can assert column ordering deterministically.
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

    private fun insertNamespace(conn: Connection, slug: String): UUID {
        val id = UUID.randomUUID()
        conn.prepareStatement(
            "INSERT INTO namespace (id, slug, name, auto_approve_releases) VALUES (?, ?, ?, false)",
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setString(2, slug)
            stmt.setString(3, slug)
            stmt.executeUpdate()
        }
        return id
    }

    private fun insertAccessKey(conn: Connection, namespaceId: UUID, name: String, lookupHashSuffix: String) {
        conn.prepareStatement(
            """
            INSERT INTO namespace_access_key
              (id, namespace_id, key_hash, key_lookup_hash, key_prefix, name, revoked)
            VALUES (?, ?, ?, ?, ?, ?, false)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, namespaceId)
            stmt.setString(3, "\$2a\$12\$" + "x".repeat(53))
            // 64-char HMAC-SHA256 hex; pad with the suffix to keep it unique across rows.
            stmt.setString(4, lookupHashSuffix.repeat(64).take(64))
            stmt.setString(5, "pwk_" + lookupHashSuffix.repeat(2))
            stmt.setString(6, name)
            stmt.executeUpdate()
        }
    }
}
