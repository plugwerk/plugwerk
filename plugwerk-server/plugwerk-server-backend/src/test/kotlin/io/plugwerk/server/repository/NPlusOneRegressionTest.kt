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
import io.plugwerk.server.countingStatements
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSettingEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.service.settings.UserSettingKey
import io.plugwerk.server.service.settings.UserSettingsService
import io.plugwerk.spi.model.PluginStatus
import io.plugwerk.spi.model.ReleaseStatus
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

/**
 * Regression tests pinning the fixes for audit rows DB-015..018 (issue #273).
 *
 * Each test creates two fixture sizes (N=3 and N=10) and asserts that the number of
 * JDBC prepared-statement executions observed via Hibernate `Statistics` does not
 * scale with N — the invariant that distinguishes a proper batch query from an
 * N+1 pattern. Runs under the default `@DataJpaTest` profile against H2 (no
 * Postgres-specific features required — the new queries use standard JPQL with
 * `IN (:plugins)` plus `JOIN FETCH`).
 *
 * The tests intentionally assert lower/upper bounds rather than exact counts, because
 * Hibernate occasionally issues implicit housekeeping statements that vary by driver
 * and Hibernate version. The invariant `countAtN3 == countAtN10` is the load-bearing
 * one — it falsifies N+1 regardless of absolute count.
 */
@Import(UserSettingsService::class)
class NPlusOneRegressionTest : AbstractRepositoryTest() {

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var pluginRepository: PluginRepository

    @Autowired lateinit var pluginReleaseRepository: PluginReleaseRepository

    @Autowired lateinit var namespaceRepository: NamespaceRepository

    @Autowired lateinit var userSettingsService: UserSettingsService

    private fun seedPluginsWithReleases(
        slugSuffix: String,
        pluginCount: Int,
        releasesPerPlugin: Int,
    ): List<PluginEntity> {
        val namespace = namespaceRepository.save(
            NamespaceEntity(slug = "ns-$slugSuffix", name = "Namespace $slugSuffix"),
        )
        val plugins = (1..pluginCount).map { i ->
            pluginRepository.save(
                PluginEntity(
                    namespace = namespace,
                    pluginId = "plugin-$i",
                    name = "Plugin $i",
                    status = PluginStatus.ACTIVE,
                    tags = arrayOf("tag-${i % 3}", "tag-common"),
                ),
            )
        }
        plugins.forEach { plugin ->
            (1..releasesPerPlugin).forEach { v ->
                pluginReleaseRepository.save(
                    PluginReleaseEntity(
                        plugin = plugin,
                        version = "1.$v.0",
                        artifactSha256 = "sha-${plugin.pluginId}-$v",
                        artifactKey = "k/${plugin.pluginId}/1.$v.0",
                        status = ReleaseStatus.PUBLISHED,
                    ),
                )
            }
        }
        entityManager.flush()
        entityManager.clear()
        return plugins
    }

    @Test
    fun `DB-016 findAllByPluginInAndStatus uses constant number of statements regardless of N`() {
        val smallPlugins = seedPluginsWithReleases("db016-small", pluginCount = 3, releasesPerPlugin = 2)
        val (_, smallCount) = entityManager.countingStatements {
            pluginReleaseRepository.findAllByPluginInAndStatus(smallPlugins, ReleaseStatus.PUBLISHED)
        }

        val largePlugins = seedPluginsWithReleases("db016-large", pluginCount = 10, releasesPerPlugin = 2)
        val (results, largeCount) = entityManager.countingStatements {
            pluginReleaseRepository.findAllByPluginInAndStatus(largePlugins, ReleaseStatus.PUBLISHED)
        }

        assertThat(results).hasSize(20)
        assertThat(smallCount)
            .`as`("statement count for N=3")
            .isEqualTo(1)
        assertThat(largeCount)
            .`as`("statement count must not scale with N — was %s at N=3, %s at N=10", smallCount, largeCount)
            .isEqualTo(smallCount)
    }

    @Test
    fun `DB-017 findAllByPluginInOrderByCreatedAtDesc uses constant number of statements regardless of N`() {
        val smallPlugins = seedPluginsWithReleases("db017-small", pluginCount = 3, releasesPerPlugin = 3)
        val (_, smallCount) = entityManager.countingStatements {
            pluginReleaseRepository.findAllByPluginInOrderByCreatedAtDesc(smallPlugins)
        }

        val largePlugins = seedPluginsWithReleases("db017-large", pluginCount = 10, releasesPerPlugin = 3)
        val (results, largeCount) = entityManager.countingStatements {
            pluginReleaseRepository.findAllByPluginInOrderByCreatedAtDesc(largePlugins)
        }

        assertThat(results).hasSize(30)
        assertThat(smallCount).isEqualTo(1)
        assertThat(largeCount).isEqualTo(smallCount)
    }

    @Test
    fun `DB-018 UserSettingsService update uses statement count independent of number of keys`() {
        val userSmall = seedTestUser("user-small")
        val userLarge = seedTestUser("user-large")
        val allKeys = UserSettingKey.entries

        // Pre-seed so both updates hit the UPDATE path uniformly
        allKeys.forEach { key ->
            entityManager.persist(
                UserSettingEntity(userId = userSmall, settingKey = key.key, settingValue = "x"),
            )
            entityManager.persist(
                UserSettingEntity(userId = userLarge, settingKey = key.key, settingValue = "x"),
            )
        }
        entityManager.flush()
        entityManager.clear()

        val smallInput = allKeys.take(2).associate { it.key to it.defaultValue }
        val largeInput = allKeys.associate { it.key to it.defaultValue }

        val (_, smallCount) = entityManager.countingStatements {
            userSettingsService.update(userSmall, smallInput)
        }
        val (_, largeCount) = entityManager.countingStatements {
            userSettingsService.update(userLarge, largeInput)
        }

        assertThat(smallCount)
            .`as`("statement count with 2 keys")
            .isLessThanOrEqualTo(5)
        assertThat(largeCount)
            .`as`(
                "statement count must not scale with M — was %s with 2 keys, %s with %s keys",
                smallCount,
                largeCount,
                allKeys.size,
            )
            .isEqualTo(smallCount)
    }

    /**
     * Persists a minimal LOCAL-source [UserEntity] and returns its assigned id.
     * Required since #360 introduced a FK from `user_setting.user_id` to
     * `plugwerk_user(id)` — the previous fixture seeded raw subject strings
     * without backing user rows, which would now violate the constraint.
     */
    private fun seedTestUser(usernameSuffix: String): java.util.UUID {
        val user = UserEntity(
            displayName = "Test $usernameSuffix",
            email = "$usernameSuffix@plugwerk.test",
            source = UserSource.INTERNAL,
            username = usernameSuffix,
            passwordHash = "\$2a\$12\$dummyhash",
        )
        entityManager.persist(user)
        entityManager.flush()
        return requireNotNull(user.id)
    }

    @Test
    fun `DB-015 findTagsByNamespaceAndStatusIn uses a single projection query`() {
        seedPluginsWithReleases("db015-small", pluginCount = 3, releasesPerPlugin = 1)
        val smallNs = namespaceRepository.findBySlug("ns-db015-small").orElseThrow()
        val (_, smallCount) = entityManager.countingStatements {
            pluginRepository.findTagsByNamespaceAndStatusIn(smallNs, listOf(PluginStatus.ACTIVE))
        }

        seedPluginsWithReleases("db015-large", pluginCount = 10, releasesPerPlugin = 1)
        val largeNs = namespaceRepository.findBySlug("ns-db015-large").orElseThrow()
        val (tags, largeCount) = entityManager.countingStatements {
            pluginRepository.findTagsByNamespaceAndStatusIn(largeNs, listOf(PluginStatus.ACTIVE))
        }

        assertThat(tags).isNotEmpty
        assertThat(smallCount).isEqualTo(1)
        assertThat(largeCount).isEqualTo(smallCount)
    }
}
