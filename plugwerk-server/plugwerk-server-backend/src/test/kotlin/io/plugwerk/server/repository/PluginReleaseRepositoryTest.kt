/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.repository

import io.plugwerk.common.model.ReleaseStatus
import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertFailsWith

open class PluginReleaseRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Autowired
    lateinit var pluginRepository: PluginRepository

    @Autowired
    lateinit var releaseRepository: PluginReleaseRepository

    lateinit var plugin: PluginEntity

    @BeforeEach
    fun setup() {
        val namespace = namespaceRepository.save(NamespaceEntity(slug = "release-ns", ownerOrg = "Org"))
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
