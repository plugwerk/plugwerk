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
package io.plugwerk.server.service

import io.plugwerk.server.SharedPostgresContainer
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertFailsWith

/**
 * Integration tests for the two-phase `PluginService.delete` (#481).
 *
 * Lives in its own `@SpringBootTest` class instead of joining
 * [PluginServiceIntegrationTest] (`@DataJpaTest`) because `delete` runs as
 * `@Transactional(propagation = NOT_SUPPORTED)` so it can guarantee storage
 * I/O happens **outside** any database transaction. Spring's auto-test-TX
 * from `@DataJpaTest` would be suspended for the duration of the call, and
 * any data created in `@BeforeEach` would be invisible to the suspended
 * transaction. `@SpringBootTest` runs against a real Spring context with no
 * implicit test-TX wrapper, which lets `delete` see the seed data exactly
 * as a production caller would.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class PluginDeleteIT {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { SharedPostgresContainer.instance.jdbcUrl }
            registry.add("spring.datasource.username") { SharedPostgresContainer.instance.username }
            registry.add("spring.datasource.password") { SharedPostgresContainer.instance.password }
        }
    }

    @MockitoBean
    lateinit var artifactStorageService: ArtifactStorageService

    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var namespaceService: NamespaceService

    @Autowired
    lateinit var pluginRepository: PluginRepository

    @Autowired
    lateinit var releaseRepository: PluginReleaseRepository

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @BeforeEach
    fun setUp() {
        // Without an auto-test-TX every test must clean up after itself; do
        // a one-shot wipe of the rows the suite touches so a previous failure
        // does not leak into the next run. Other top-level tables are left
        // alone — this IT only owns the namespace it creates.
        if (namespaceRepository.existsBySlug("delete-it-ns")) {
            namespaceService.delete("delete-it-ns")
        }
        namespaceService.create("delete-it-ns", "Delete IT Org")
    }

    @AfterEach
    fun tearDown() {
        if (namespaceRepository.existsBySlug("delete-it-ns")) {
            namespaceService.delete("delete-it-ns")
        }
    }

    @Test
    fun `delete removes plugin from database (basic happy path)`() {
        pluginService.create("delete-it-ns", "del-plugin", "To Delete")

        pluginService.delete("delete-it-ns", "del-plugin")

        assertFailsWith<PluginNotFoundException> {
            pluginService.findByNamespaceAndPluginId("delete-it-ns", "del-plugin")
        }
    }

    @Test
    fun `delete keeps DB consistent and best-effort-cleans storage when one storage delete fails (issue #481)`() {
        // Pre-fix behaviour: storageService.delete runs INSIDE the @Transactional
        // boundary, so a failure on release 2 throws and rolls back the entire
        // DB transaction — the plugin and all releases stay persisted while the
        // file for release 1 has already been removed from disk. Storage and DB
        // diverge, no recovery without manual intervention.
        //
        // Post-fix behaviour: the DB transaction commits independently of
        // storage. All three releases and the plugin are gone from the DB; the
        // storage cleanup is best-effort and runs outside the transaction.
        // storage.delete is invoked for every artifact key (including the
        // failing one); the failure is swallowed and logged.
        val plugin = pluginService.create("delete-it-ns", "tx-plugin", "TX Plugin")
        seedRelease(plugin, "1.0.0")
        seedRelease(plugin, "1.1.0")
        seedRelease(plugin, "2.0.0")

        whenever(artifactStorageService.delete(eq("delete-it-ns/tx-plugin/1.1.0")))
            .thenThrow(ArtifactStorageException("simulated mid-loop storage failure"))

        // Must complete without rethrowing — the storage failure is a
        // best-effort log, never a user-facing exception.
        pluginService.delete("delete-it-ns", "tx-plugin")

        // DB is the source of truth. After the call the plugin and all its
        // releases are gone, regardless of what storage did or did not do.
        assertFailsWith<PluginNotFoundException> {
            pluginService.findByNamespaceAndPluginId("delete-it-ns", "tx-plugin")
        }
        assertThat(pluginRepository.findAllByNamespace(namespaceService.findBySlug("delete-it-ns")))
            .noneMatch { it.pluginId == "tx-plugin" }

        // Storage cleanup ran for every key — including the failing one. The
        // best-effort loop must not short-circuit on the first exception.
        verify(artifactStorageService).delete(eq("delete-it-ns/tx-plugin/1.0.0"))
        verify(artifactStorageService).delete(eq("delete-it-ns/tx-plugin/1.1.0"))
        verify(artifactStorageService).delete(eq("delete-it-ns/tx-plugin/2.0.0"))
    }

    private fun seedRelease(plugin: PluginEntity, version: String): PluginReleaseEntity = releaseRepository.save(
        PluginReleaseEntity(
            plugin = plugin,
            version = version,
            artifactKey = "${plugin.namespace.slug}/${plugin.pluginId}/$version",
            artifactSha256 = "0".repeat(64),
            status = ReleaseStatus.PUBLISHED,
        ),
    )
}
