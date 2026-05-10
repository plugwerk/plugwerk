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
import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.repository.UserRepository
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

/**
 * Integration tests for the two-phase `NamespaceService.delete` (#481).
 *
 * `@SpringBootTest` rather than `@DataJpaTest` for the same reason as
 * [PluginDeleteIT]: `NamespaceService.delete` is now annotated
 * `@Transactional(propagation = NOT_SUPPORTED)` so a `@DataJpaTest`
 * auto-test-TX would be suspended for the duration of the call and
 * `@BeforeEach`-seeded data would be invisible.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class NamespaceDeleteIT {

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

    @Autowired lateinit var namespaceService: NamespaceService

    @Autowired lateinit var namespaceRepository: NamespaceRepository

    @Autowired lateinit var pluginRepository: PluginRepository

    @Autowired lateinit var pluginReleaseRepository: PluginReleaseRepository

    @Autowired lateinit var namespaceMemberRepository: NamespaceMemberRepository

    @Autowired lateinit var namespaceAccessKeyRepository: NamespaceAccessKeyRepository

    @Autowired lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        cleanupTestNamespaces()
    }

    @AfterEach
    fun tearDown() {
        cleanupTestNamespaces()
    }

    @Test
    fun `delete removes namespace from database (basic happy path)`() {
        namespaceService.create("delete-it-ns-basic", "Org")

        namespaceService.delete("delete-it-ns-basic")

        assertThat(namespaceRepository.existsBySlug("delete-it-ns-basic")).isFalse()
    }

    @Test
    fun `delete cascades to plugins, releases, members, access keys, and storage`() {
        val namespace = namespaceService.create("delete-it-ns-cascade", "Cascade Org")

        val plugin1 = pluginRepository.save(
            PluginEntity(namespace = namespace, pluginId = "plugin-a", name = "Plugin A"),
        )
        val plugin2 = pluginRepository.save(
            PluginEntity(namespace = namespace, pluginId = "plugin-b", name = "Plugin B"),
        )

        val release1 = pluginReleaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin1,
                version = "1.0.0",
                artifactSha256 = "a".repeat(64),
                artifactKey = "delete-it-ns-cascade/plugin-a/1.0.0.jar",
                status = ReleaseStatus.PUBLISHED,
            ),
        )
        val release2 = pluginReleaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin2,
                version = "2.0.0",
                artifactSha256 = "b".repeat(64),
                artifactKey = "delete-it-ns-cascade/plugin-b/2.0.0.jar",
                status = ReleaseStatus.PUBLISHED,
            ),
        )

        val alice = userRepository.save(
            UserEntity(
                username = "alice-cascade-it",
                displayName = "Alice (Cascade IT)",
                email = "alice-cascade-it@cascade.test",
                source = UserSource.INTERNAL,
                passwordHash = "\$2a\$12\$hash",
            ),
        )
        namespaceMemberRepository.save(
            NamespaceMemberEntity(
                namespace = namespace,
                user = alice,
                role = NamespaceRole.ADMIN,
            ),
        )

        namespaceAccessKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$cascade-it-bcrypt-hash",
                keyLookupHash = "f".repeat(64),
                keyPrefix = "pwk_casc",
                name = "Test key IT",
            ),
        )

        namespaceService.delete("delete-it-ns-cascade")

        assertThat(namespaceRepository.existsBySlug("delete-it-ns-cascade")).isFalse()
        assertThat(pluginRepository.findById(plugin1.id!!)).isEmpty
        assertThat(pluginRepository.findById(plugin2.id!!)).isEmpty
        assertThat(pluginReleaseRepository.findById(release1.id!!)).isEmpty
        assertThat(pluginReleaseRepository.findById(release2.id!!)).isEmpty
        assertThat(namespaceMemberRepository.findAllByNamespaceId(namespace.id!!)).isEmpty()
        assertThat(namespaceAccessKeyRepository.findAllByNamespace(namespace)).isEmpty()

        verify(artifactStorageService).delete(eq("delete-it-ns-cascade/plugin-a/1.0.0.jar"))
        verify(artifactStorageService).delete(eq("delete-it-ns-cascade/plugin-b/2.0.0.jar"))

        // Cleanup the orphaned user the test created (it lives outside the
        // namespace and is not part of the cascade).
        userRepository.delete(alice)
    }

    @Test
    fun `delete commits DB and best-effort-cleans storage when one storage delete fails (issue #481)`() {
        // Vertrag-Test (#481): the storage cleanup is best-effort. A failure
        // on one artifact must not block the DB transaction from committing,
        // and must not stop the loop from attempting the remaining keys.
        val namespace = namespaceService.create("delete-it-ns-storage-fail", "Storage Fail Org")
        val plugin = pluginRepository.save(
            PluginEntity(namespace = namespace, pluginId = "p", name = "P"),
        )
        pluginReleaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.0.0",
                artifactSha256 = "a".repeat(64),
                artifactKey = "delete-it-ns-storage-fail/p/1.0.0",
                status = ReleaseStatus.PUBLISHED,
            ),
        )
        pluginReleaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "2.0.0",
                artifactSha256 = "b".repeat(64),
                artifactKey = "delete-it-ns-storage-fail/p/2.0.0",
                status = ReleaseStatus.PUBLISHED,
            ),
        )

        whenever(artifactStorageService.delete(eq("delete-it-ns-storage-fail/p/1.0.0")))
            .thenThrow(ArtifactStorageException("simulated"))

        // Must not rethrow.
        namespaceService.delete("delete-it-ns-storage-fail")

        // DB is the source of truth — gone, regardless of what storage did.
        assertThat(namespaceRepository.existsBySlug("delete-it-ns-storage-fail")).isFalse()

        // Both keys were attempted — the failing one did NOT short-circuit
        // the loop.
        verify(artifactStorageService).delete(eq("delete-it-ns-storage-fail/p/1.0.0"))
        verify(artifactStorageService).delete(eq("delete-it-ns-storage-fail/p/2.0.0"))
    }

    private fun cleanupTestNamespaces() {
        listOf(
            "delete-it-ns-basic",
            "delete-it-ns-cascade",
            "delete-it-ns-storage-fail",
        ).forEach { slug ->
            if (namespaceRepository.existsBySlug(slug)) {
                namespaceService.delete(slug)
            }
        }
    }
}
