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
import java.sql.DriverManager

/**
 * Verifies that every Liquibase changeSet has a working rollback (issue #293 /
 * audit row DB-001..007). Applies the full changelog against a fresh PostgreSQL 18
 * Testcontainer, then rolls back to tag `empty` (placed before any changeSet ran)
 * and asserts that every project-owned table has been dropped.
 *
 * Runs without a Spring context — see `FkIndexMigrationIT` for the precedent. A
 * second `@SpringBootTest` with `integration` profile would share the Spring
 * context cache and trigger Testcontainers dialect-detection flakes on
 * memory-constrained CI runners (cf. PR #304).
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiquibaseRollbackIT {

    private lateinit var postgres: PostgreSQLContainer

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer("postgres:18-alpine").apply { start() }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    @Test
    fun `full changelog rolls back to empty schema`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
            val liquibase = Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            )
            // Tag the empty state BEFORE running any changeSet so we can roll back to it.
            liquibase.tag("empty")
            liquibase.update(Contexts(), LabelExpression())
            assertThat(projectTables())
                .`as`("schema must be populated after update")
                .isNotEmpty()
            liquibase.rollback("empty", Contexts(), LabelExpression())
        }

        assertThat(projectTables())
            .`as`("every project-owned table must be dropped by its rollback block")
            .isEmpty()
    }

    /**
     * Returns all public-schema tables the project owns. Opens its own short-lived
     * JDBC connection so it remains usable after [Liquibase] closes the connection
     * owned by its [JdbcConnection] wrapper. Filters out Liquibase's own bookkeeping
     * tables (`databasechangelog`, `databasechangeloglock`) which are managed by
     * Liquibase itself and must survive rollback.
     */
    private fun projectTables(): List<String> {
        val names = mutableListOf<String>()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename NOT IN ('databasechangelog', 'databasechangeloglock')
                ORDER BY tablename
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) names.add(rs.getString("tablename"))
                }
            }
        }
        return names
    }
}
