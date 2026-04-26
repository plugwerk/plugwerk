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
 * Verifies migration 0016 — `refresh_token.upstream_id_token` (issue #352, RP-Initiated
 * Logout for OIDC sessions). Three checks:
 *
 *  1. Schema check — the column exists on `refresh_token` with `text` type and is
 *     nullable. Nullability is critical: local-login rows must be insertable without
 *     supplying any upstream ID token.
 *  2. Round-trip check — insert a row with a long-ish text payload (Keycloak ID
 *     tokens are typically ~1.5 KB; we use a 4 KB sample to assert the unbounded
 *     `text` choice), read it back verbatim. Catches any silent truncation that a
 *     `varchar(N)` typo would introduce.
 *  3. Backwards-compat — the existing `refresh_token` insert path with no value for
 *     the new column still succeeds and yields NULL. Mirrors what
 *     `RefreshTokenService.issue(username)` (no `upstreamIdToken` overload) does for
 *     local-login sessions.
 *
 * Same Testcontainers-without-Spring pattern as `UserEmailCaseInsensitiveUniqueMigrationIT`
 * to keep the IT fast and avoid Spring-context-cache eviction on memory-constrained CI.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefreshTokenUpstreamIdTokenMigrationIT {

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
    fun `0016 upstream_id_token column exists as nullable text on refresh_token`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT data_type, is_nullable
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'refresh_token'
                   AND column_name = 'upstream_id_token'
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).`as`("column should exist after 0016 migration").isTrue()
                    assertThat(rs.getString("data_type")).isEqualTo("text")
                    assertThat(rs.getString("is_nullable")).isEqualTo("YES")
                }
            }
        }
    }

    @Test
    fun `0016 upstream_id_token round-trips a multi-kilobyte payload without truncation`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            val userId = insertUser(conn)
            // 4 KB payload — well above any varchar(N) we might have accidentally typed.
            val payload = "header.payload.signature".repeat(170)
            val tokenId = insertRefreshToken(conn, userId, upstreamIdToken = payload)

            val read = readUpstreamIdToken(conn, tokenId)
            assertThat(read).isEqualTo(payload)
            assertThat(read!!.length).isGreaterThan(2048)
        }
    }

    @Test
    fun `0016 refresh_token insert without upstream_id_token still succeeds (nullable)`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            val userId = insertUser(conn)
            val tokenId = insertRefreshToken(conn, userId, upstreamIdToken = null)

            assertThat(readUpstreamIdToken(conn, tokenId)).isNull()
        }
    }

    private fun insertUser(conn: Connection): UUID {
        val userId = UUID.randomUUID()
        val username = "u-${userId.toString().take(8)}"
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, enabled, password_change_required, is_superadmin)
            VALUES (?, ?, NULL, ?, true, false, false)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, userId)
            stmt.setString(2, username)
            stmt.setString(3, "\$2a\$12\$" + "x".repeat(53))
            stmt.executeUpdate()
        }
        return userId
    }

    private fun insertRefreshToken(conn: Connection, userId: UUID, upstreamIdToken: String?): UUID {
        val tokenId = UUID.randomUUID()
        // Keep the lookup-hash unique per insert to avoid colliding with parallel inserts.
        val lookupHash = ("h" + tokenId.toString().replace("-", "")).take(64).padEnd(64, '0')
        conn.prepareStatement(
            """
            INSERT INTO refresh_token
              (id, family_id, user_id, token_lookup_hash, expires_at, upstream_id_token)
            VALUES (?, ?, ?, ?, now() + interval '1 hour', ?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, tokenId)
            stmt.setObject(2, UUID.randomUUID())
            stmt.setObject(3, userId)
            stmt.setString(4, lookupHash)
            if (upstreamIdToken ==
                null
            ) {
                stmt.setNull(5, java.sql.Types.LONGVARCHAR)
            } else {
                stmt.setString(5, upstreamIdToken)
            }
            stmt.executeUpdate()
        }
        return tokenId
    }

    private fun readUpstreamIdToken(conn: Connection, tokenId: UUID): String? {
        conn.prepareStatement("SELECT upstream_id_token FROM refresh_token WHERE id = ?").use { stmt ->
            stmt.setObject(1, tokenId)
            stmt.executeQuery().use { rs ->
                check(rs.next()) { "row $tokenId not found" }
                return rs.getString("upstream_id_token")
            }
        }
    }
}
