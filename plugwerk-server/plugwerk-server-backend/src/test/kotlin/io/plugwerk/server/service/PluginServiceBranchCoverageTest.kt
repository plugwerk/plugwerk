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

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.spi.model.PluginStatus
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Branch-coverage-focused tests for [PluginService]. Targets conditional
 * arms not exercised by [PluginServiceTest]: the four-phase catalog
 * orchestration ([PluginService.findPagedByNamespace]) across all visibility
 * tiers, the in-memory tag/query/version filters, the sort comparators and
 * direction handling, the pagination offset clamp, [PluginService.update]
 * field-by-field `?.let` arms, and the [PluginService.findDistinctTags]
 * visibility `when`.
 */
@ExtendWith(MockitoExtension::class)
class PluginServiceBranchCoverageTest {

    @Mock
    lateinit var pluginRepository: PluginRepository

    @Mock
    lateinit var releaseRepository: PluginReleaseRepository

    @Mock
    lateinit var storageService: ArtifactStorageService

    @Mock
    lateinit var namespaceService: NamespaceService

    @Mock
    lateinit var pluginDeletionTransaction: PluginDeletionTransaction

    @InjectMocks
    lateinit var pluginService: PluginService

    private val namespaceId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
    private val namespace = NamespaceEntity(id = namespaceId, slug = "acme", name = "ACME Corp")

    private fun plugin(
        pluginId: String,
        name: String = pluginId,
        status: PluginStatus = PluginStatus.ACTIVE,
        description: String? = null,
        tags: Array<String> = emptyArray(),
        id: UUID = UUID.randomUUID(),
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now(),
    ) = PluginEntity(
        id = id,
        namespace = namespace,
        pluginId = pluginId,
        name = name,
        description = description,
        tags = tags,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun release(owner: PluginEntity, version: String) = PluginReleaseEntity(
        id = UUID.randomUUID(),
        plugin = owner,
        version = version,
        artifactSha256 = "sha-$version",
        artifactKey = "${owner.pluginId}:$version",
        status = ReleaseStatus.PUBLISHED,
    )

    // ---- findPagedByNamespace: visibility tiers -------------------------------------------

    @Test
    fun `findPagedByNamespace PUBLIC keeps only ACTIVE plugins that have a published release`() {
        val active = plugin("active", status = PluginStatus.ACTIVE)
        val activeNoRelease = plugin("active-no-release", status = PluginStatus.ACTIVE)
        val archived = plugin("archived", status = PluginStatus.ARCHIVED)
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace))
            .thenReturn(listOf(active, activeNoRelease, archived))
        // Only `active` has a published release.
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(active, "1.0.0")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }.containsExactly("active")
        // draftOnly query must NOT fire for PUBLIC visibility.
        verify(releaseRepository, never()).findPluginIdsWithOnlyDraftReleases(any())
    }

    @Test
    fun `findPagedByNamespace AUTHENTICATED includes draft-only plugins and excludes SUSPENDED`() {
        val withRelease = plugin("with-release", status = PluginStatus.ACTIVE)
        val draftOnly = plugin("draft-only", status = PluginStatus.ACTIVE)
        val suspended = plugin("suspended", status = PluginStatus.SUSPENDED)
        val orphan = plugin("orphan", status = PluginStatus.ACTIVE) // no release, not draft-only
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace))
            .thenReturn(listOf(withRelease, draftOnly, suspended, orphan))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(withRelease, "1.0.0")))
        whenever(releaseRepository.findPluginIdsWithOnlyDraftReleases(namespaceId))
            .thenReturn(setOf(draftOnly.id!!))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.AUTHENTICATED,
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }
            .containsExactlyInAnyOrder("with-release", "draft-only")
        verify(releaseRepository).findPluginIdsWithOnlyDraftReleases(namespaceId)
        assertThat(result.draftOnlyPluginIds).containsExactly(draftOnly.id!!)
    }

    @Test
    fun `findPagedByNamespace ADMIN returns every plugin regardless of status or release`() {
        val active = plugin("active", status = PluginStatus.ACTIVE)
        val suspended = plugin("suspended", status = PluginStatus.SUSPENDED)
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace))
            .thenReturn(listOf(active, suspended))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any())).thenReturn(emptyList())
        whenever(releaseRepository.findPluginIdsWithOnlyDraftReleases(namespaceId)).thenReturn(emptySet())

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.ADMIN,
        )

        assertThat(result.page.content).hasSize(2)
    }

    // ---- findPagedByNamespace: status filter + empty-id short-circuit ---------------------

    @Test
    fun `findPagedByNamespace with status filter routes to findAllByNamespaceAndStatus`() {
        val active = plugin("active", status = PluginStatus.ACTIVE)
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespaceAndStatus(namespace, PluginStatus.ACTIVE))
            .thenReturn(listOf(active))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(active, "1.0.0")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            PluginStatus.ACTIVE,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).hasSize(1)
        verify(pluginRepository, never()).findAllByNamespace(any<NamespaceEntity>())
    }

    @Test
    fun `findPagedByNamespace short-circuits release and download queries when no plugins`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(emptyList())

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).isEmpty()
        // allIds.isEmpty() branch: neither the latest-release nor the download query runs.
        verify(releaseRepository, never()).findLatestPublishedReleasesForPlugins(any())
        verify(releaseRepository, never()).sumDownloadCountsByPluginIds(any())
    }

    // ---- applyCatalogFilters: tag + query + version ---------------------------------------

    @Test
    fun `findPagedByNamespace filters by tag keeping only plugins carrying that tag`() {
        val tagged = plugin("tagged", tags = arrayOf("billing"))
        val untagged = plugin("untagged", tags = arrayOf("other"))
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(tagged, untagged))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(tagged, "1.0.0"), release(untagged, "1.0.0")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            "billing",
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }.containsExactly("tagged")
    }

    @Test
    fun `findPagedByNamespace query matches description when name does not match`() {
        val byDescription = plugin("alpha", name = "Alpha", description = "great FINANCE tooling")
        val noMatch = plugin("beta", name = "Beta", description = "unrelated")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(byDescription, noMatch))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(byDescription, "1.0.0"), release(noMatch, "1.0.0")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            "finance",
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }.containsExactly("alpha")
    }

    @Test
    fun `findPagedByNamespace query matches plugin name case-insensitively`() {
        val byName = plugin("gamma", name = "Payment Gateway", description = null)
        val noMatch = plugin("delta", name = "Other", description = null)
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(byName, noMatch))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(byName, "1.0.0"), release(noMatch, "1.0.0")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            "payment",
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }.containsExactly("gamma")
    }

    @Test
    fun `findPagedByNamespace version filter excludes plugins below the minimum constraint`() {
        val newEnough = plugin("new", name = "New")
        val tooOld = plugin("old", name = "Old")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(newEnough, tooOld))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(newEnough, "3.1.0"), release(tooOld, "2.0.0")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
            version = ">=3.0.0",
        )

        // newEnough (3.1.0 >= 3.0.0) kept; tooOld (2.0.0) dropped.
        assertThat(result.page.content).extracting<String> { it.pluginId }.containsExactly("new")
    }

    @Test
    fun `findPagedByNamespace version filter drops plugins lacking a published release`() {
        val noRelease = plugin("no-release", name = "No Release", status = PluginStatus.ACTIVE)
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        // ADMIN so visibility does not pre-filter the release-less plugin.
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(noRelease))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any())).thenReturn(emptyList())
        whenever(releaseRepository.findPluginIdsWithOnlyDraftReleases(namespaceId)).thenReturn(emptySet())

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.ADMIN,
            version = ">=1.0.0",
        )

        // latestReleases[id] == null -> return@filter false branch.
        assertThat(result.page.content).isEmpty()
    }

    @Test
    fun `findPagedByNamespace version filter uses exact-equality when constraint has no prefix`() {
        val exact = plugin("exact", name = "Exact")
        val different = plugin("different", name = "Different")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(exact, different))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(exact, "2.0.0"), release(different, "1.0.0")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
            version = "2.0.0",
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }.containsExactly("exact")
    }

    @Test
    fun `findPagedByNamespace blank version constraint skips version filtering`() {
        val anyVersion = plugin("any", name = "Any")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(anyVersion))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(anyVersion, "0.0.1")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
            version = "   ",
        )

        // version.isNullOrBlank() == true -> early return, plugin retained.
        assertThat(result.page.content).extracting<String> { it.pluginId }.containsExactly("any")
    }

    @Test
    fun `findPagedByNamespace version filter handles differing version segment lengths`() {
        val shortVersion = plugin("short", name = "Short")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(shortVersion))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            // "3" vs constraint ">=3.0.0" exercises the getOrElse padding in compareVersions.
            .thenReturn(listOf(release(shortVersion, "3")))

        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(0, 20),
            PluginService.CatalogVisibility.PUBLIC,
            version = ">=3.0.0",
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }.containsExactly("short")
    }

    // ---- sortAndPaginate: comparators + directions + offset clamp -------------------------

    @Test
    fun `findPagedByNamespace sorts by name ascending for unknown sort property`() {
        val zebra = plugin("zebra", name = "Zebra")
        val apple = plugin("apple", name = "apple")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(zebra, apple))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(zebra, "1.0.0"), release(apple, "1.0.0")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "somethingUnknown"))
        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            pageable,
            PluginService.CatalogVisibility.PUBLIC,
        )

        // else-branch comparator: alphabetical by lowercased name.
        assertThat(result.page.content).extracting<String> { it.pluginId }
            .containsExactly("apple", "zebra")
    }

    @Test
    fun `findPagedByNamespace sorts by downloadCount descending`() {
        val popular = plugin("popular", name = "Popular")
        val niche = plugin("niche", name = "Niche")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(niche, popular))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(popular, "1.0.0"), release(niche, "1.0.0")))
        whenever(releaseRepository.sumDownloadCountsByPluginIds(any())).thenReturn(
            listOf(
                arrayOf<Any>(popular.id!!, 100L),
                // niche has no row -> elvis 0L branch.
            ),
        )

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "downloadCount"))
        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            pageable,
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }
            .containsExactly("popular", "niche")
    }

    @Test
    fun `findPagedByNamespace sorts by updatedAt ascending`() {
        val older = plugin("older", name = "Older", updatedAt = OffsetDateTime.parse("2020-01-01T00:00:00Z"))
        val newer = plugin("newer", name = "Newer", updatedAt = OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(newer, older))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(older, "1.0.0"), release(newer, "1.0.0")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "updatedAt"))
        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            pageable,
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }
            .containsExactly("older", "newer")
    }

    @Test
    fun `findPagedByNamespace sorts by createdAt descending`() {
        val older = plugin("older", name = "Older", createdAt = OffsetDateTime.parse("2020-01-01T00:00:00Z"))
        val newer = plugin("newer", name = "Newer", createdAt = OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(older, newer))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(older, "1.0.0"), release(newer, "1.0.0")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            pageable,
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).extracting<String> { it.pluginId }
            .containsExactly("newer", "older")
    }

    @Test
    fun `findPagedByNamespace clamps offset beyond list size to an empty page`() {
        val only = plugin("only", name = "Only")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(only))
        whenever(releaseRepository.findLatestPublishedReleasesForPlugins(any()))
            .thenReturn(listOf(release(only, "1.0.0")))

        // Page index 5 * size 10 = offset 50, well beyond the single result.
        val result = pluginService.findPagedByNamespace(
            "acme",
            null,
            null,
            null,
            PageRequest.of(5, 10),
            PluginService.CatalogVisibility.PUBLIC,
        )

        assertThat(result.page.content).isEmpty()
        assertThat(result.page.totalElements).isEqualTo(1)
    }

    // ---- findDistinctTags: visibility when arms -------------------------------------------

    @Test
    fun `findDistinctTags PUBLIC queries ACTIVE only and returns sorted distinct tags`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findTagsByNamespaceAndStatusIn(eq(namespace), eq(listOf(PluginStatus.ACTIVE))))
            .thenReturn(listOf(arrayOf("z-tag", "a-tag"), arrayOf("a-tag", "m-tag")))

        val tags = pluginService.findDistinctTags("acme", PluginService.CatalogVisibility.PUBLIC)

        assertThat(tags).containsExactly("a-tag", "m-tag", "z-tag")
    }

    @Test
    fun `findDistinctTags AUTHENTICATED excludes SUSPENDED from the queried statuses`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        val expected = PluginStatus.entries.filter { it != PluginStatus.SUSPENDED }
        whenever(pluginRepository.findTagsByNamespaceAndStatusIn(eq(namespace), eq(expected)))
            .thenReturn(listOf(arrayOf("auth")))

        val tags = pluginService.findDistinctTags("acme", PluginService.CatalogVisibility.AUTHENTICATED)

        assertThat(tags).containsExactly("auth")
        assertThat(expected).doesNotContain(PluginStatus.SUSPENDED)
    }

    @Test
    fun `findDistinctTags ADMIN queries every status`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findTagsByNamespaceAndStatusIn(eq(namespace), eq(PluginStatus.entries.toList())))
            .thenReturn(emptyList())

        val tags = pluginService.findDistinctTags("acme", PluginService.CatalogVisibility.ADMIN)

        assertThat(tags).isEmpty()
    }

    // ---- update: per-field ?.let arms -----------------------------------------------------

    @Test
    fun `update with all-null fields leaves entity untouched but still saves`() {
        val existing = plugin("p1", name = "Original", description = "desc")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "p1")).thenReturn(Optional.of(existing))
        whenever(pluginRepository.save(any<PluginEntity>())).thenReturn(existing)

        pluginService.update("acme", "p1")

        // Every `?.let` skipped (null arm): unchanged fields.
        assertThat(existing.name).isEqualTo("Original")
        assertThat(existing.description).isEqualTo("desc")
        verify(pluginRepository).save(existing)
    }

    @Test
    fun `update applies every optional field when all are provided`() {
        val existing = plugin("p1", name = "Original")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "p1")).thenReturn(Optional.of(existing))
        whenever(pluginRepository.save(any<PluginEntity>())).thenReturn(existing)

        val newTags = arrayOf("a", "b")
        pluginService.update(
            "acme", "p1",
            name = "New",
            description = "New desc",
            provider = "Provider",
            license = "MIT",
            homepage = "https://home",
            repository = "https://repo",
            icon = "https://icon",
            tags = newTags,
            status = PluginStatus.SUSPENDED,
        )

        // Every `?.let` non-null arm executed.
        assertThat(existing.name).isEqualTo("New")
        assertThat(existing.description).isEqualTo("New desc")
        assertThat(existing.provider).isEqualTo("Provider")
        assertThat(existing.license).isEqualTo("MIT")
        assertThat(existing.homepage).isEqualTo("https://home")
        assertThat(existing.repository).isEqualTo("https://repo")
        assertThat(existing.icon).isEqualTo("https://icon")
        assertThat(existing.tags).containsExactly("a", "b")
        assertThat(existing.status).isEqualTo(PluginStatus.SUSPENDED)
    }

    @Test
    fun `update propagates PluginNotFoundException from the lookup`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "ghost")).thenReturn(Optional.empty())

        assertFailsWith<PluginNotFoundException> {
            pluginService.update("acme", "ghost", name = "irrelevant")
        }
        verify(pluginRepository, never()).save(any<PluginEntity>())
    }

    // ---- findAllByNamespace: explicit status-null arm -------------------------------------

    @Test
    fun `findAllByNamespace with null status uses the unfiltered query`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(plugin("p1")))

        val result = pluginService.findAllByNamespace("acme", null)

        assertThat(result).hasSize(1)
        verify(pluginRepository, never()).findAllByNamespaceAndStatus(any(), any())
    }
}
