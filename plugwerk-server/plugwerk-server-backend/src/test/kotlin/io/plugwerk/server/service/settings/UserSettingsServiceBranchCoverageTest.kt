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
package io.plugwerk.server.service.settings

import io.plugwerk.server.domain.UserSettingEntity
import io.plugwerk.server.repository.UserSettingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Branch-coverage tests for [UserSettingsService] (ADR-0018).
 *
 * Targets every decision arm:
 *  - `getAll`: stored-empty (`ifEmpty` -> default) vs stored-present, and the
 *    completely-empty store (every key falls back to its default).
 *  - `get`: missing row, present-but-null value, present-but-empty value, and
 *    a real stored value.
 *  - `update`: unknown key (throw), failed per-key validation (throw), the
 *    existing-row update branch and the new-row insert branch.
 *  - `deleteAll` / `clearDefaultNamespace`: the simple delegation paths.
 */
@ExtendWith(MockitoExtension::class)
class UserSettingsServiceBranchCoverageTest {

    @Mock lateinit var repository: UserSettingRepository

    @InjectMocks lateinit var service: UserSettingsService

    private val userId: UUID = UUID.randomUUID()

    private fun entity(key: String, value: String?) = UserSettingEntity(
        userId = userId,
        settingKey = key,
        settingValue = value,
    )

    // ---- getAll ------------------------------------------------------------

    @Test
    fun `getAll returns defaults for every key when nothing is stored`() {
        whenever(repository.findByUserId(userId)).thenReturn(emptyList())

        val result = service.getAll(userId)

        // Every enum key present, all defaulted.
        assertThat(result).containsAllEntriesOf(
            UserSettingKey.entries.associate { it.key to it.defaultValue },
        )
    }

    @Test
    fun `getAll prefers a stored non-empty value over the default`() {
        whenever(repository.findByUserId(userId)).thenReturn(
            listOf(entity(UserSettingKey.THEME.key, "dark")),
        )

        val result = service.getAll(userId)

        assertThat(result[UserSettingKey.THEME.key]).isEqualTo("dark")
        // Untouched key still falls back to its default.
        assertThat(result[UserSettingKey.PREFERRED_LANGUAGE.key])
            .isEqualTo(UserSettingKey.PREFERRED_LANGUAGE.defaultValue)
    }

    @Test
    fun `getAll falls back to default when the stored value is an empty string`() {
        // The `ifEmpty { null }` arm: a row exists but holds "" -> default wins.
        whenever(repository.findByUserId(userId)).thenReturn(
            listOf(entity(UserSettingKey.THEME.key, "")),
        )

        val result = service.getAll(userId)

        assertThat(result[UserSettingKey.THEME.key]).isEqualTo(UserSettingKey.THEME.defaultValue)
    }

    @Test
    fun `getAll treats a null stored value as absent and uses the default`() {
        // associate stores "" for a null settingValue; the ifEmpty arm then defaults.
        whenever(repository.findByUserId(userId)).thenReturn(
            listOf(entity(UserSettingKey.DEFAULT_NAMESPACE.key, null)),
        )

        val result = service.getAll(userId)

        assertThat(result[UserSettingKey.DEFAULT_NAMESPACE.key])
            .isEqualTo(UserSettingKey.DEFAULT_NAMESPACE.defaultValue)
    }

    // ---- get ---------------------------------------------------------------

    @Test
    fun `get returns the default when no row exists`() {
        whenever(repository.findByUserIdAndSettingKey(userId, UserSettingKey.THEME.key))
            .thenReturn(Optional.empty())

        val value = service.get(userId, UserSettingKey.THEME)

        assertThat(value).isEqualTo(UserSettingKey.THEME.defaultValue)
    }

    @Test
    fun `get returns the default when the stored value is null`() {
        whenever(repository.findByUserIdAndSettingKey(userId, UserSettingKey.THEME.key))
            .thenReturn(Optional.of(entity(UserSettingKey.THEME.key, null)))

        val value = service.get(userId, UserSettingKey.THEME)

        assertThat(value).isEqualTo(UserSettingKey.THEME.defaultValue)
    }

    @Test
    fun `get returns the default when the stored value is empty`() {
        whenever(repository.findByUserIdAndSettingKey(userId, UserSettingKey.THEME.key))
            .thenReturn(Optional.of(entity(UserSettingKey.THEME.key, "")))

        val value = service.get(userId, UserSettingKey.THEME)

        assertThat(value).isEqualTo(UserSettingKey.THEME.defaultValue)
    }

    @Test
    fun `get returns the stored value when present and non-empty`() {
        whenever(repository.findByUserIdAndSettingKey(userId, UserSettingKey.THEME.key))
            .thenReturn(Optional.of(entity(UserSettingKey.THEME.key, "light")))

        val value = service.get(userId, UserSettingKey.THEME)

        assertThat(value).isEqualTo("light")
    }

    // ---- update ------------------------------------------------------------

    @Test
    fun `update throws for an unknown setting key`() {
        whenever(repository.findByUserId(userId)).thenReturn(emptyList())

        val ex = assertFailsWith<IllegalArgumentException> {
            service.update(userId, mapOf("not_a_real_key" to "x"))
        }
        assertThat(ex.message).isEqualTo("Unknown user setting key: 'not_a_real_key'")
        verify(repository, never()).saveAll(any<List<UserSettingEntity>>())
    }

    @Test
    fun `update throws when a value fails per-key validation`() {
        whenever(repository.findByUserId(userId)).thenReturn(emptyList())

        // THEME is an ENUM restricted to light/dark/system.
        val ex = assertFailsWith<IllegalArgumentException> {
            service.update(userId, mapOf(UserSettingKey.THEME.key to "neon"))
        }
        assertThat(ex.message).contains("Invalid value for user setting 'theme'")
        verify(repository, never()).saveAll(any<List<UserSettingEntity>>())
    }

    @Test
    fun `update mutates an existing row in place rather than inserting a new one`() {
        val existing = entity(UserSettingKey.THEME.key, "system")
        // First call: load existing rows for the merge. Second call: getAll() at the end.
        whenever(repository.findByUserId(userId))
            .thenReturn(listOf(existing))
        whenever(repository.saveAll(any<List<UserSettingEntity>>())).thenReturn(emptyList())

        service.update(userId, mapOf(UserSettingKey.THEME.key to "dark"))

        val captor = argumentCaptor<List<UserSettingEntity>>()
        verify(repository).saveAll(captor.capture())
        val saved = captor.firstValue
        assertThat(saved).hasSize(1)
        // The very same instance was reused and its value updated (existing branch).
        assertThat(saved.first()).isSameAs(existing)
        assertThat(saved.first().settingValue).isEqualTo("dark")
    }

    @Test
    fun `update inserts a new row when none exists for the key`() {
        whenever(repository.findByUserId(userId)).thenReturn(emptyList())
        whenever(repository.saveAll(any<List<UserSettingEntity>>())).thenReturn(emptyList())

        service.update(userId, mapOf(UserSettingKey.DEFAULT_NAMESPACE.key to "acme"))

        val captor = argumentCaptor<List<UserSettingEntity>>()
        verify(repository).saveAll(captor.capture())
        val saved = captor.firstValue
        assertThat(saved).hasSize(1)
        assertThat(saved.first().userId).isEqualTo(userId)
        assertThat(saved.first().settingKey).isEqualTo(UserSettingKey.DEFAULT_NAMESPACE.key)
        assertThat(saved.first().settingValue).isEqualTo("acme")
    }

    @Test
    fun `update with an empty settings map saves an empty batch and returns defaults`() {
        // The for-loop body never executes — covers the zero-iteration path.
        whenever(repository.findByUserId(userId)).thenReturn(emptyList())
        whenever(repository.saveAll(any<List<UserSettingEntity>>())).thenReturn(emptyList())

        val result = service.update(userId, emptyMap())

        val captor = argumentCaptor<List<UserSettingEntity>>()
        verify(repository).saveAll(captor.capture())
        assertThat(captor.firstValue).isEmpty()
        assertThat(result[UserSettingKey.THEME.key]).isEqualTo(UserSettingKey.THEME.defaultValue)
    }

    // ---- deleteAll / clearDefaultNamespace ---------------------------------

    @Test
    fun `deleteAll delegates to the repository`() {
        service.deleteAll(userId)

        verify(repository).deleteByUserId(userId)
    }

    @Test
    fun `clearDefaultNamespace nullifies matching rows for the default-namespace key`() {
        service.clearDefaultNamespace("acme")

        verify(repository).nullifyBySettingKeyAndValue(
            eq(UserSettingKey.DEFAULT_NAMESPACE.key),
            eq("acme"),
        )
    }
}
