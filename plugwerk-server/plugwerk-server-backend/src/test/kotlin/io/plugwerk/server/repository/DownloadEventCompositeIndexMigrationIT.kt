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
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

/**
 * Verifies that Liquibase migration 0011 installs the composite index required by
 * audit finding DB-012 (issue #270) for time-windowed per-release analytics on
 * download_event, and that the existing single-column indexes remain intact.
 *
 * Runs against a dedicated short-lived PostgreSQL 18 Testcontainer and invokes
 * Liquibase directly (no Spring context) — same pattern as FkIndexMigrationIT
 * (PR #269) to avoid Spring-context-cache eviction on memory-constrained CI.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DownloadEventCompositeIndexMigrationIT {

    private lateinit var postgres: PostgreSQLContainer<*>

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
    fun `DB-012 idx_download_event_release_downloaded_at exists with descending downloaded_at`() {
        val indexDef = indexDefinition("download_event", "idx_download_event_release_downloaded_at")
        assertThat(indexDef).isNotNull()
        // Postgres pg_indexes.indexdef shape:
        //   CREATE INDEX idx_download_event_release_downloaded_at
        //     ON public.download_event USING btree (release_id, downloaded_at DESC)
        assertThat(indexDef!!).contains("(release_id, downloaded_at DESC)")
    }

    @Test
    fun `idx_download_event_release_id is preserved (FK cascade hot path)`() {
        // Kept alongside the composite because it is narrower and cheaper for
        // ON DELETE CASCADE bulk delete by release_id when a plugin_release
        // is removed. Removing it would silently regress that hot path.
        val indexDef = indexDefinition("download_event", "idx_download_event_release_id")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("(release_id)")
    }

    @Test
    fun `idx_download_event_downloaded_at is preserved (cross-release time-window analytics)`() {
        // Kept because cross-release queries ("downloads in the last month
        // across ALL releases") cannot use the composite via the leading-column
        // rule and would degrade to a sequential scan without this index.
        val indexDef = indexDefinition("download_event", "idx_download_event_downloaded_at")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("(downloaded_at)")
    }

    private fun indexDefinition(table: String, indexName: String): String? {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
            ).use { stmt ->
                stmt.setString(1, table)
                stmt.setString(2, indexName)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("indexdef") else null
                }
            }
        }
    }
}
