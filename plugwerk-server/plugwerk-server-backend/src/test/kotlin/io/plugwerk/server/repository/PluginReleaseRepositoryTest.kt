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

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.spi.model.ReleaseStatus
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

open class PluginReleaseRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Autowired
    lateinit var pluginRepository: PluginRepository

    @Autowired
    lateinit var releaseRepository: PluginReleaseRepository

    @Autowired
    lateinit var entityManager: EntityManager

    lateinit var plugin: PluginEntity

    @BeforeEach
    fun setup() {
        val namespace = namespaceRepository.save(NamespaceEntity(slug = "release-ns", name = "Org"))
        plugin = pluginRepository.save(PluginEntity(namespace = namespace, pluginId = "my-plugin", name = "My Plugin"))
    }

    @Test
    fun `findByPluginAndVersion returns release when it exists`() {
        val release =
            releaseRepository.save(
                PluginReleaseEntity(
                    plugin = plugin,
                    version = "1.0.0",
                    artifactSha256 = "abc123",
                    artifactKey = "release-ns/my-plugin/1.0.0",
                ),
            )

        val found = releaseRepository.findByPluginAndVersion(plugin, "1.0.0")

        assertThat(found).isPresent
        assertThat(found.get().id).isEqualTo(release.id!!)
    }

    @Test
    fun `findByPluginAndVersion returns empty for unknown version`() {
        val found = releaseRepository.findByPluginAndVersion(plugin, "9.9.9")

        assertThat(found).isEmpty
    }

    @Test
    fun `findAllByPluginOrderByCreatedAtDesc returns releases newest first`() {
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.0.0",
                artifactSha256 = "sha1",
                artifactKey = "release-ns/my-plugin/1.0.0",
            ),
        )
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.1.0",
                artifactSha256 = "sha2",
                artifactKey = "release-ns/my-plugin/1.1.0",
            ),
        )
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "2.0.0",
                artifactSha256 = "sha3",
                artifactKey = "release-ns/my-plugin/2.0.0",
            ),
        )

        val releases = releaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin)

        assertThat(releases).hasSize(3)
        assertThat(releases.first().version).isEqualTo("2.0.0")
        assertThat(releases.last().version).isEqualTo("1.0.0")
    }

    @Test
    fun `findAllByPluginAndStatus filters by status`() {
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.0.0",
                artifactSha256 = "sha1",
                artifactKey = "release-ns/my-plugin/1.0.0",
                status = ReleaseStatus.PUBLISHED,
            ),
        )
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "2.0.0",
                artifactSha256 = "sha2",
                artifactKey = "release-ns/my-plugin/2.0.0",
                status = ReleaseStatus.DRAFT,
            ),
        )
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "3.0.0",
                artifactSha256 = "sha3",
                artifactKey = "release-ns/my-plugin/3.0.0",
                status = ReleaseStatus.YANKED,
            ),
        )

        val published = releaseRepository.findAllByPluginAndStatus(plugin, ReleaseStatus.PUBLISHED)
        val drafts = releaseRepository.findAllByPluginAndStatus(plugin, ReleaseStatus.DRAFT)

        assertThat(published).hasSize(1)
        assertThat(published.first().version).isEqualTo("1.0.0")
        assertThat(drafts).hasSize(1)
        assertThat(drafts.first().version).isEqualTo("2.0.0")
    }

    @Test
    fun `save persists plugin_dependencies as JSON`() {
        val deps = """[{"pluginId":"other-plugin","versionRange":">=1.0.0"}]"""
        val release =
            releaseRepository.save(
                PluginReleaseEntity(
                    plugin = plugin,
                    version = "1.0.0",
                    artifactSha256 = "sha1",
                    artifactKey = "release-ns/my-plugin/1.0.0",
                    pluginDependencies = deps,
                ),
            )

        val found = releaseRepository.findById(release.id!!).orElseThrow()

        assertThat(found.pluginDependencies).contains("other-plugin")
    }

    @Test
    fun `save fails on duplicate version for same plugin`() {
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.0.0",
                artifactSha256 = "sha1",
                artifactKey = "release-ns/my-plugin/1.0.0",
            ),
        )
        releaseRepository.flush()

        assertFailsWith<DataIntegrityViolationException> {
            releaseRepository.saveAndFlush(
                PluginReleaseEntity(
                    plugin = plugin,
                    version = "1.0.0",
                    artifactSha256 = "sha2",
                    artifactKey = "release-ns/my-plugin/1.0.0-dup",
                ),
            )
        }
    }

    @Test
    fun `findLatestPublishedReleasesForPlugins is deterministic on createdAt ties (#482)`() {
        // Two PUBLISHED releases of the same plugin with EXACTLY the same
        // created_at. Pre-fix the query's correlated MAX(createdAt) subquery
        // returned both rows; the caller's associateBy { plugin.id } collapsed
        // them to a single map entry whose winner is implementation-defined
        // (Hibernate iteration order without an ORDER BY). The catalog's
        // "latest version" therefore flickered between versions on every
        // query — a non-deterministic read, not a missing-plugin one as the
        // original issue body claimed.
        //
        // Post-fix: ROW_NUMBER() OVER (PARTITION BY plugin_id ORDER BY
        // created_at DESC, id DESC) breaks the tie deterministically using
        // id as the secondary sort. UUIDv7 is time-ordered, so id-DESC
        // semantically continues "later wins" past the timestamp resolution.
        val r1 = releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.0.0",
                artifactSha256 = "sha1",
                artifactKey = "release-ns/my-plugin/1.0.0",
                status = ReleaseStatus.PUBLISHED,
            ),
        )
        val r2 = releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.0.1",
                artifactSha256 = "sha2",
                artifactKey = "release-ns/my-plugin/1.0.1",
                status = ReleaseStatus.PUBLISHED,
            ),
        )

        // Force identical created_at via native UPDATE — @CreationTimestamp
        // does not let us set the value at save time. flush()+clear() so the
        // following findLatestPublishedReleasesForPlugins sees the rewritten
        // values, not the stale persistence-context cache.
        val tieTimestamp = OffsetDateTime.parse("2026-05-10T12:00:00Z")
            .withOffsetSameInstant(ZoneOffset.UTC)
        entityManager.createNativeQuery(
            "UPDATE plugin_release SET created_at = :ts WHERE id IN (:r1, :r2)",
        )
            .setParameter("ts", tieTimestamp)
            .setParameter("r1", r1.id)
            .setParameter("r2", r2.id)
            .executeUpdate()
        entityManager.flush()
        entityManager.clear()

        // Run the query 50 times and assert it always returns exactly one
        // row, always with the same id. Without the tiebreaker the result
        // is at the mercy of the underlying row order; the deterministic
        // ORDER BY id DESC always picks the higher UUID.
        val winners = (1..50).map {
            val result = releaseRepository.findLatestPublishedReleasesForPlugins(listOf(plugin.id!!))
            assertThat(result).hasSize(1)
            result.single().id
        }.distinct()

        assertThat(winners).hasSize(1)
        // UUIDv7 ids are time-ordered, so the later-saved release (r2) has
        // the higher id and must win the tiebreaker.
        assertThat(winners.single()).isEqualTo(r2.id)
    }

    @Test
    fun `existsByPluginAndVersion returns correct boolean`() {
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.0.0",
                artifactSha256 = "sha1",
                artifactKey = "release-ns/my-plugin/1.0.0",
            ),
        )

        assertThat(releaseRepository.existsByPluginAndVersion(plugin, "1.0.0")).isTrue()
        assertThat(releaseRepository.existsByPluginAndVersion(plugin, "2.0.0")).isFalse()
    }
}
