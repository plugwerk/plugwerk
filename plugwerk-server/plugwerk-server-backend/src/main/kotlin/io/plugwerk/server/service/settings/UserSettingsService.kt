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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Reads and writes per-user settings (ADR-0018).
 *
 * Unlike [ApplicationSettingsService] (which caches globally), this service reads from the database
 * on every call because user settings are low-frequency and per-user caching adds complexity
 * with no meaningful performance benefit for the profile settings page.
 */
@Service
class UserSettingsService(private val repository: UserSettingRepository) {

    fun getAll(userId: UUID): Map<String, String> {
        val stored = repository.findByUserId(userId).associate { it.settingKey to (it.settingValue ?: "") }
        return UserSettingKey.entries.associate { key ->
            key.key to (stored[key.key]?.ifEmpty { null } ?: key.defaultValue)
        }
    }

    fun get(userId: UUID, key: UserSettingKey): String {
        val entity = repository.findByUserIdAndSettingKey(userId, key.key).orElse(null)
        val value = entity?.settingValue
        return if (value.isNullOrEmpty()) key.defaultValue else value
    }

    @Transactional
    fun update(userId: UUID, settings: Map<String, String>): Map<String, String> {
        val existingByKey = repository.findByUserId(userId).associateBy { it.settingKey }
        val toSave = mutableListOf<UserSettingEntity>()
        for ((rawKey, rawValue) in settings) {
            val key = UserSettingKey.byKey(rawKey)
                ?: throw IllegalArgumentException("Unknown user setting key: '$rawKey'")
            key.validate(rawValue)?.let { error ->
                throw IllegalArgumentException("Invalid value for user setting '$rawKey': $error")
            }
            val existing = existingByKey[key.key]
            if (existing != null) {
                existing.settingValue = rawValue
                toSave += existing
            } else {
                toSave += UserSettingEntity(
                    userId = userId,
                    settingKey = key.key,
                    settingValue = rawValue,
                )
            }
        }
        repository.saveAll(toSave)
        return getAll(userId)
    }

    @Transactional
    fun deleteAll(userId: UUID) {
        repository.deleteByUserId(userId)
    }

    @Transactional
    fun clearDefaultNamespace(namespaceSlug: String) {
        repository.nullifyBySettingKeyAndValue(UserSettingKey.DEFAULT_NAMESPACE.key, namespaceSlug)
    }
}
