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
 * Verifies migration 0017 — identity-hub split (issue #351). The migration is
 * tricky enough that we exercise it against a populated pre-state instead of
 * just running it against an empty schema:
 *
 *  - One LOCAL row (`alice`) with email + password.
 *  - One synthetic-OIDC row (`<provider-uuid>:<sub>`) that mirrors what
 *    PR #350's OidcLoginSuccessHandler used to write, including `email = NULL`
 *    (the legacy hack that dodged the case-insensitive uniqueness from #271).
 *  - Two namespace_member rows pointing at each user via user_subject.
 *  - One refresh_token row (active) per user.
 *
 * After update: every assertion below must hold. The test then exercises the
 * new CHECK constraints and unique indexes by attempting violating writes and
 * confirming they're rejected.
 *
 * Same Testcontainers-without-Spring pattern as `RefreshTokenUpstreamIdTokenMigrationIT`.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityHubSplitMigrationIT {

    private lateinit var postgres: PostgreSQLContainer

    private val localUserId = UUID.randomUUID()
    private val oidcUserId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val oidcSub = "alice-keycloak-sub-12345"
    private val namespaceId = UUID.randomUUID()

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer("postgres:18-alpine").apply { start() }
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            // Run schema up to migration 0016 (everything before identity-hub split).
            runLiquibaseUpTo(conn, "0017_identity_hub_split.yaml")
            seedPreMigrationData(conn)
        }
        // Now apply the rest (i.e. 0017).
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            runLiquibaseUpdate(conn)
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    /**
     * Runs Liquibase changelogs in order, stopping just before [stopBeforeFile]
     * — used to install everything up to 0016 so we can seed the data that
     * migration 0017 is supposed to transform.
     *
     * Liquibase has no first-class "run up to but not including this changeset"
     * primitive, so we use the rollback-on-update-to-target pattern: apply
     * EVERYTHING, then roll back every changeset from [stopBeforeFile] onward
     * (the target file plus all later migrations). The resulting state is
     * identical to "applied through the file just before [stopBeforeFile]".
     *
     * Note: `liquibase.rollback(N, "")` rolls back the last N applied
     * changesets *globally*, not the last N from the target file. So we
     * compute N as `(total changesets) - (index of first target changeset)`,
     * which stays correct as new migrations are added after the target.
     */
    private fun runLiquibaseUpTo(conn: Connection, stopBeforeFile: String) {
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
        Liquibase(
            "db/changelog/db.changelog-master.yaml",
            ClassLoaderResourceAccessor(),
            database,
        ).use { liquibase ->
            liquibase.update(Contexts(), LabelExpression())
            val changesets = liquibase.databaseChangeLog.changeSets
            val firstIdx = changesets.indexOfFirst { it.filePath.endsWith(stopBeforeFile) }
            check(firstIdx >= 0) { "no changesets found in $stopBeforeFile" }
            val rollbackCount = changesets.size - firstIdx
            liquibase.rollback(rollbackCount, "")
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
     * Pre-migration fixture: mirrors what PR #350's OidcLoginSuccessHandler
     * leaves in the DB (synthetic username, NULL email, sentinel password
     * hash) plus a pure-LOCAL row plus a namespace_member each.
     */
    private fun seedPreMigrationData(conn: Connection) {
        // OIDC provider row (referenced by the synthetic username). 'KEYCLOAK'
        // is the historical enum value that existed before migration 0018 folded
        // it into 'EXTERNAL'; the seed runs after 0018 has already executed (against
        // an empty table), so the value survives untouched and exercises 0017's
        // logic exactly as it would have run pre-consolidation.
        conn.prepareStatement(
            """
            INSERT INTO oidc_provider
              (id, name, provider_type, enabled, client_id, client_secret_encrypted, scope, created_at, updated_at)
            VALUES (?, 'Test KC', 'KEYCLOAK', true, 'pw', 'encrypted', 'openid email profile', now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, providerId)
            stmt.executeUpdate()
        }
        // Local user.
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, 'alice', 'Alice@Example.com', '$2a${'$'}12${'$'}fakebcrypt', true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, localUserId)
            stmt.executeUpdate()
        }
        // OIDC sentinel user — username is "<provider-uuid>:<sub>", email NULL,
        // password_hash is the OIDC:no-password sentinel from #350.
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, ?, NULL, 'OIDC:no-password', true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, oidcUserId)
            stmt.setString(2, "$providerId:$oidcSub")
            stmt.executeUpdate()
        }
        // Namespace + two namespace_member rows.
        conn.prepareStatement(
            """
            INSERT INTO namespace (id, slug, name, created_at, updated_at)
            VALUES (?, 'test-ns', 'Test NS', now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, namespaceId)
            stmt.executeUpdate()
        }
        conn.prepareStatement(
            """
            INSERT INTO namespace_member (id, namespace_id, user_subject, role, created_at)
            VALUES (?, ?, 'alice', 'ADMIN', now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, namespaceId)
            stmt.executeUpdate()
        }
        conn.prepareStatement(
            """
            INSERT INTO namespace_member (id, namespace_id, user_subject, role, created_at)
            VALUES (?, ?, ?, 'MEMBER', now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, namespaceId)
            stmt.setString(3, "$providerId:$oidcSub")
            stmt.executeUpdate()
        }
        // One active refresh_token per user — both must be revoked by 4f.
        conn.prepareStatement(
            """
            INSERT INTO refresh_token
              (id, family_id, user_id, token_lookup_hash, expires_at)
            VALUES (?, ?, ?, ?, now() + interval '1 hour')
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, UUID.randomUUID())
            stmt.setObject(3, localUserId)
            stmt.setString(4, "h-local-".padEnd(64, '0'))
            stmt.executeUpdate()
        }
        conn.prepareStatement(
            """
            INSERT INTO refresh_token
              (id, family_id, user_id, token_lookup_hash, expires_at)
            VALUES (?, ?, ?, ?, now() + interval '1 hour')
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, UUID.randomUUID())
            stmt.setObject(3, oidcUserId)
            stmt.setString(4, "h-oidc-".padEnd(64, '0'))
            stmt.executeUpdate()
        }
    }

    private inline fun <R> withConn(block: (Connection) -> R): R =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use(block)

    @Test
    fun `0017 LOCAL row is preserved with username, password, source=LOCAL, display_name from username`() =
        withConn { conn ->
            conn.prepareStatement(
                "SELECT username, password_hash, source, display_name, email FROM plugwerk_user WHERE id = ?",
            ).use { stmt ->
                stmt.setObject(1, localUserId)
                stmt.executeQuery().use { rs ->
                    check(rs.next())
                    assertThat(rs.getString("username")).isEqualTo("alice")
                    assertThat(rs.getString("password_hash")).isNotBlank()
                    assertThat(rs.getString("source")).isEqualTo("INTERNAL")
                    assertThat(rs.getString("display_name")).isEqualTo("alice")
                    assertThat(rs.getString("email")).isEqualTo("Alice@Example.com")
                }
            }
        }

    @Test
    fun `0017 OIDC sentinel row is split into plugwerk_user with cleared credentials and oidc_identity row`() =
        withConn { conn ->
            conn.prepareStatement(
                "SELECT username, password_hash, source, display_name, email FROM plugwerk_user WHERE id = ?",
            ).use { stmt ->
                stmt.setObject(1, oidcUserId)
                stmt.executeQuery().use { rs ->
                    check(rs.next())
                    assertThat(rs.getString("username")).isNull()
                    assertThat(rs.getString("password_hash")).isNull()
                    assertThat(rs.getString("source")).isEqualTo("EXTERNAL")
                    assertThat(rs.getString("display_name")).isEqualTo(oidcSub)
                    assertThat(rs.getString("email")).startsWith("migrated-")
                    assertThat(rs.getString("email")).endsWith("@plugwerk-migration.local")
                }
            }
            conn.prepareStatement(
                "SELECT oidc_provider_id, subject, user_id FROM oidc_identity WHERE user_id = ?",
            ).use { stmt ->
                stmt.setObject(1, oidcUserId)
                stmt.executeQuery().use { rs ->
                    check(rs.next())
                    assertThat(rs.getObject("oidc_provider_id", UUID::class.java)).isEqualTo(providerId)
                    assertThat(rs.getString("subject")).isEqualTo(oidcSub)
                    assertThat(rs.getObject("user_id", UUID::class.java)).isEqualTo(oidcUserId)
                }
            }
        }

    @Test
    fun `0017 namespace_member user_subject is replaced by user_id FK for both LOCAL and OIDC rows`() =
        withConn { conn ->
            // user_subject column should be gone.
            conn.prepareStatement(
                """
            SELECT 1 FROM information_schema.columns
             WHERE table_name = 'namespace_member' AND column_name = 'user_subject'
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).`as`("user_subject column should be dropped").isFalse()
                }
            }
            // Both rows have user_id populated.
            conn.prepareStatement(
                "SELECT user_id, role FROM namespace_member WHERE namespace_id = ? ORDER BY role",
            ).use { stmt ->
                stmt.setObject(1, namespaceId)
                stmt.executeQuery().use { rs ->
                    check(rs.next())
                    assertThat(rs.getString("role")).isEqualTo("ADMIN")
                    assertThat(rs.getObject("user_id", UUID::class.java)).isEqualTo(localUserId)
                    check(rs.next())
                    assertThat(rs.getString("role")).isEqualTo("MEMBER")
                    assertThat(rs.getObject("user_id", UUID::class.java)).isEqualTo(oidcUserId)
                }
            }
        }

    @Test
    fun `0017 active refresh tokens are force-revoked with reason SCHEMA_MIGRATION_0017`() = withConn { conn ->
        conn.prepareStatement(
            "SELECT revoked_at, revocation_reason FROM refresh_token",
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                var rowCount = 0
                while (rs.next()) {
                    rowCount++
                    assertThat(rs.getTimestamp("revoked_at")).isNotNull()
                    assertThat(rs.getString("revocation_reason")).isEqualTo("SCHEMA_MIGRATION_0017")
                }
                assertThat(rowCount).isEqualTo(2)
            }
        }
    }

    @Test
    fun `0017 CHECK constraint rejects LOCAL row with NULL username`() = withConn { conn ->
        assertThatThrownBy {
            conn.prepareStatement(
                """
                INSERT INTO plugwerk_user
                  (id, username, email, password_hash, source, display_name,
                   enabled, password_change_required, is_superadmin, created_at, updated_at)
                VALUES (?, NULL, 'x@y.test', '${'$'}2a${'$'}12${'$'}h', 'INTERNAL', 'x',
                   true, false, false, now(), now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.executeUpdate()
            }
        }.isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `0017 CHECK constraint rejects OIDC row with non-null username`() = withConn { conn ->
        assertThatThrownBy {
            conn.prepareStatement(
                """
                INSERT INTO plugwerk_user
                  (id, username, email, password_hash, source, display_name,
                   enabled, password_change_required, is_superadmin, created_at, updated_at)
                VALUES (?, 'shouldnotbeset', 'x@y.test', NULL, 'EXTERNAL', 'x',
                   true, false, false, now(), now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.executeUpdate()
            }
        }.isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `0017 CHECK constraint rejects unknown source value`() = withConn { conn ->
        assertThatThrownBy {
            conn.prepareStatement(
                """
                INSERT INTO plugwerk_user
                  (id, username, email, password_hash, source, display_name,
                   enabled, password_change_required, is_superadmin, created_at, updated_at)
                VALUES (?, 'x', 'x@y.test', '${'$'}2a${'$'}12${'$'}h', 'WEBAUTHN', 'x',
                   true, false, false, now(), now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.executeUpdate()
            }
        }.isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `0017 LOCAL email uniqueness is case-insensitive only within source=LOCAL`() = withConn { conn ->
        // First LOCAL with email — succeeds.
        val firstId = UUID.randomUUID()
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, source, display_name,
               enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, 'bob1', 'bob@example.com', 'h', 'INTERNAL', 'Bob 1',
               true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, firstId)
            stmt.executeUpdate()
        }
        // Second LOCAL with the same email differing only in case — rejected.
        assertThatThrownBy {
            conn.prepareStatement(
                """
                INSERT INTO plugwerk_user
                  (id, username, email, password_hash, source, display_name,
                   enabled, password_change_required, is_superadmin, created_at, updated_at)
                VALUES (?, 'bob2', 'BOB@example.COM', 'h', 'INTERNAL', 'Bob 2',
                   true, false, false, now(), now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.executeUpdate()
            }
        }
            .isInstanceOf(SQLException::class.java)
            .matches { (it as SQLException).sqlState == "23505" }
        // OIDC row with the same email is allowed (no linking → same email is a coincidence).
        val oidcId = UUID.randomUUID()
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, source, display_name,
               enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, NULL, 'bob@example.com', NULL, 'EXTERNAL', 'Bob via Keycloak',
               true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, oidcId)
            stmt.executeUpdate()
        }
        // And another OIDC with the same email — also allowed.
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, source, display_name,
               enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, NULL, 'bob@example.com', NULL, 'EXTERNAL', 'Bob via GitHub',
               true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.executeUpdate()
        }
    }

    @Test
    fun `0017 oidc_identity UNIQUE(user_id) enforces no-linking policy`() = withConn { conn ->
        val userId = UUID.randomUUID()
        conn.prepareStatement(
            """
            INSERT INTO plugwerk_user
              (id, username, email, password_hash, source, display_name,
               enabled, password_change_required, is_superadmin, created_at, updated_at)
            VALUES (?, NULL, 'multi@y.test', NULL, 'EXTERNAL', 'Multi',
               true, false, false, now(), now())
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, userId)
            stmt.executeUpdate()
        }
        // First identity for that user — succeeds.
        conn.prepareStatement(
            """
            INSERT INTO oidc_identity (id, oidc_provider_id, subject, user_id)
            VALUES (?, ?, 'sub-1', ?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, providerId)
            stmt.setObject(3, userId)
            stmt.executeUpdate()
        }
        // Second identity for the SAME user — rejected by UNIQUE(user_id).
        assertThatThrownBy {
            conn.prepareStatement(
                """
                INSERT INTO oidc_identity (id, oidc_provider_id, subject, user_id)
                VALUES (?, ?, 'sub-2', ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, providerId)
                stmt.setObject(3, userId)
                stmt.executeUpdate()
            }
        }
            .isInstanceOf(SQLException::class.java)
            .matches { (it as SQLException).sqlState == "23505" }
    }

    @Test
    fun `0017 namespace_member UNIQUE(namespace_id, user_id) prevents duplicate role assignments`() = withConn { conn ->
        assertThatThrownBy {
            conn.prepareStatement(
                """
                INSERT INTO namespace_member (id, namespace_id, user_id, role, created_at)
                VALUES (?, ?, ?, 'MEMBER', now())
                """.trimIndent(),
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, namespaceId)
                stmt.setObject(3, localUserId) // already ADMIN in this namespace
                stmt.executeUpdate()
            }
        }
            .isInstanceOf(SQLException::class.java)
            .matches { (it as SQLException).sqlState == "23505" }
    }

    @Test
    fun `0017 deleting plugwerk_user cascades to oidc_identity and namespace_member`() = withConn { conn ->
        // Ensure the OIDC row exists with both an identity and a namespace_member.
        val oidcIdentityCount = conn.prepareStatement(
            "SELECT COUNT(*) FROM oidc_identity WHERE user_id = ?",
        ).use { stmt ->
            stmt.setObject(1, oidcUserId)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        assertThat(oidcIdentityCount).isEqualTo(1)
        // Cascade-delete the user.
        conn.prepareStatement("DELETE FROM plugwerk_user WHERE id = ?").use { stmt ->
            stmt.setObject(1, oidcUserId)
            stmt.executeUpdate()
        }
        val identitiesAfter = conn.prepareStatement(
            "SELECT COUNT(*) FROM oidc_identity WHERE user_id = ?",
        ).use { stmt ->
            stmt.setObject(1, oidcUserId)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        assertThat(identitiesAfter).isEqualTo(0)
        val membersAfter = conn.prepareStatement(
            "SELECT COUNT(*) FROM namespace_member WHERE user_id = ?",
        ).use { stmt ->
            stmt.setObject(1, oidcUserId)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        assertThat(membersAfter).isEqualTo(0)
    }
}
