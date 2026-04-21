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

import io.plugwerk.server.SharedPostgresContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Verifies that Liquibase migration 0008 installs the foreign-key-coverage indexes
 * required by audit findings DB-008..011 (issue #269) and that the composite unique
 * constraints that provide leading-column coverage for DB-008, DB-009 and DB-010
 * remain intact.
 *
 * Runs against the shared PostgreSQL 18 Testcontainer with the `integration`
 * profile so Liquibase executes the full changelog (including 0008).
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class FkIndexMigrationIT {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun overrideDataSource(registry: DynamicPropertyRegistry) {
            val postgres = SharedPostgresContainer.instance
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `DB-011 idx_access_key_namespace exists on namespace_access_key(namespace_id)`() {
        val indexDef = indexDefinition("namespace_access_key", "idx_access_key_namespace")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("(namespace_id)")
    }

    @Test
    fun `DB-011 idx_access_key_namespace_active is a partial index WHERE revoked is false`() {
        val indexDef = indexDefinition("namespace_access_key", "idx_access_key_namespace_active")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("(namespace_id)")
        assertThat(indexDef).contains("WHERE")
        assertThat(indexDef.lowercase()).contains("revoked")
    }

    @Test
    fun `idx_namespace_member_user_subject exists on namespace_member(user_subject)`() {
        val indexDef = indexDefinition("namespace_member", "idx_namespace_member_user_subject")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("(user_subject)")
    }

    /**
     * DB-008 is intentionally covered by the composite unique constraint, not by a
     * dedicated single-column index. Pin this contract so a future schema change that
     * drops or reorders the composite constraint surfaces here instead of in prod.
     */
    @Test
    fun `DB-008 uq_plugin_namespace_plugin_id still leads on namespace_id`() {
        val indexDef = indexDefinition("plugin", "uq_plugin_namespace_plugin_id")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("(namespace_id, plugin_id)")
    }

    @Test
    fun `DB-009 uq_plugin_release_plugin_version still leads on plugin_id`() {
        val indexDef = indexDefinition("plugin_release", "uq_plugin_release_plugin_version")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("(plugin_id, version)")
    }

    @Test
    fun `DB-010 uq_namespace_member_ns_subject still leads on namespace_id`() {
        val indexDef = indexDefinition("namespace_member", "uq_namespace_member_ns_subject")
        assertThat(indexDef).isNotNull()
        assertThat(indexDef!!).contains("(namespace_id, user_subject)")
    }

    private fun indexDefinition(table: String, indexName: String): String? = jdbcTemplate.query(
        "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
        { rs, _ -> rs.getString("indexdef") },
        table,
        indexName,
    ).firstOrNull()
}
