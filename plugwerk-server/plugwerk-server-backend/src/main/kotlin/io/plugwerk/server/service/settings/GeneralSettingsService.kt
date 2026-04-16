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

import io.plugwerk.server.domain.ApplicationSettingEntity
import io.plugwerk.server.repository.ApplicationSettingRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.atomic.AtomicReference

/**
 * Single source of truth for admin-manageable application settings (ADR-0016).
 *
 * Reads the `application_setting` table into an in-memory snapshot at startup and after
 * every successful write. The snapshot is held in an [AtomicReference] so reads are
 * lock-free and consistent with the most recent write.
 *
 * The service exposes typed accessors for common reads (e.g. [maxUploadSizeMb]) and a
 * generic [getRaw] fallback. Writes go through [update], which validates against
 * [SettingKey.validate] before persisting.
 *
 * A per-key **boot snapshot** is captured once at startup so the Admin UI can flag keys
 * whose current DB value differs from the value the running JVM loaded at boot
 * (e.g. `upload.max_file_size_mb`, which the multipart filter only picks up on restart —
 * see [SettingKey.requiresRestart]).
 */
@Service
class GeneralSettingsService(private val repository: ApplicationSettingRepository) {

    private val log = LoggerFactory.getLogger(GeneralSettingsService::class.java)

    /** Live snapshot of `setting_key -> stored row data`. Updated atomically on writes. */
    private val cache = AtomicReference<Map<String, StoredSetting>>(emptyMap())

    /**
     * Snapshot of the values as they were at JVM startup. Immutable for the lifetime of the
     * process. Used to compute [SettingSnapshot.restartPending] — a UI hint, not a hard
     * enforcement mechanism.
     */
    private lateinit var bootSnapshot: Map<String, StoredSetting>

    @PostConstruct
    fun initialize() {
        refreshCache()
        bootSnapshot = cache.get()
        log.info("GeneralSettingsService initialized with {} setting(s)", bootSnapshot.size)
    }

    /**
     * Reloads the entire snapshot from the database. Called on startup and after every
     * successful write. The atomic swap guarantees readers see either the old snapshot or
     * the new snapshot in full — never a partially-updated view.
     */
    private fun refreshCache() {
        val rows = repository.findAll()
        val snapshot = rows.associate {
            it.settingKey to StoredSetting(
                rawValue = it.settingValue ?: "",
                description = it.settingDesc,
            )
        }
        cache.set(snapshot)
    }

    /**
     * Returns the raw string value for [key], falling back to [SettingKey.defaultValue] if
     * the row is missing or the stored value is an empty string.
     */
    fun getRaw(key: SettingKey): String {
        val stored = cache.get()[key.key]?.rawValue
        return if (stored.isNullOrEmpty()) key.defaultValue else stored
    }

    /** Typed accessor: `upload.max_file_size_mb` as an Int, clamped to the hard ceiling. */
    fun maxUploadSizeMb(): Int = getRaw(SettingKey.UPLOAD_MAX_FILE_SIZE_MB).toIntOrNull()
        ?.coerceIn(1, MAX_ALLOWED_UPLOAD_MB)
        ?: SettingKey.UPLOAD_MAX_FILE_SIZE_MB.defaultValue.toInt()

    /** Typed accessor: `tracking.enabled`. */
    fun trackingEnabled(): Boolean = getRaw(SettingKey.TRACKING_ENABLED).toBooleanStrict()

    /** Typed accessor: `tracking.capture_ip`. */
    fun trackingCaptureIp(): Boolean = getRaw(SettingKey.TRACKING_CAPTURE_IP).toBooleanStrict()

    /** Typed accessor: `tracking.anonymize_ip`. */
    fun trackingAnonymizeIp(): Boolean = getRaw(SettingKey.TRACKING_ANONYMIZE_IP).toBooleanStrict()

    /** Typed accessor: `tracking.capture_user_agent`. */
    fun trackingCaptureUserAgent(): Boolean = getRaw(SettingKey.TRACKING_CAPTURE_USER_AGENT).toBooleanStrict()

    /** Typed accessor: `general.default_language`. */
    fun defaultLanguage(): String = getRaw(SettingKey.GENERAL_DEFAULT_LANGUAGE)

    /** Typed accessor: `general.site_name`. */
    fun siteName(): String = getRaw(SettingKey.GENERAL_SITE_NAME)

    /**
     * Returns a complete snapshot of every key, including its effective value, description,
     * source (`DATABASE` if a row exists, `DEFAULT` otherwise), and a `restartPending` flag
     * for keys whose DB value has changed since the JVM started.
     */
    fun listAll(): List<SettingSnapshot> {
        val current = cache.get()
        val boot = if (::bootSnapshot.isInitialized) bootSnapshot else current
        return SettingKey.entries.map { key ->
            val stored = current[key.key]
            val effective = when {
                stored == null -> key.defaultValue
                stored.rawValue.isEmpty() -> key.defaultValue
                else -> stored.rawValue
            }
            val source = if (stored == null) SettingSource.DEFAULT else SettingSource.DATABASE
            val bootStored = boot[key.key]
            val bootValue = when {
                bootStored == null -> key.defaultValue
                bootStored.rawValue.isEmpty() -> key.defaultValue
                else -> bootStored.rawValue
            }
            val restartPending = key.requiresRestart && bootValue != effective
            SettingSnapshot(
                key = key,
                value = effective,
                description = stored?.description,
                source = source,
                restartPending = restartPending,
            )
        }
    }

    /**
     * Persists a new value for [key] and refreshes the cache.
     *
     * @throws IllegalArgumentException if [rawValue] fails [SettingKey.validate].
     */
    @Transactional
    fun update(key: SettingKey, rawValue: String, updatedBy: String?): SettingSnapshot {
        key.validate(rawValue)?.let { error ->
            throw IllegalArgumentException("Invalid value for setting '${key.key}': $error")
        }
        val existing = repository.findBySettingKey(key.key).orElse(null)
        val entity = existing?.apply {
            settingValue = rawValue
            this.updatedBy = updatedBy
        } ?: ApplicationSettingEntity(
            settingKey = key.key,
            settingValue = rawValue,
            valueType = key.valueType,
            updatedBy = updatedBy,
        )
        repository.save(entity)
        refreshCache()
        return listAll().first { it.key == key }
    }
}

/** Origin of the effective value returned by [GeneralSettingsService.listAll]. */
enum class SettingSource {
    /** Value is backed by a row in `application_setting`. */
    DATABASE,

    /** No row exists — the value is the hard-coded default in [SettingKey]. */
    DEFAULT,
}

/**
 * Internal cache row — holds the raw stored value plus the DB-persisted description.
 */
internal data class StoredSetting(val rawValue: String, val description: String?)

/**
 * A point-in-time view of one setting.
 *
 * @property key the [SettingKey] entry.
 * @property value the effective raw string value (either from DB or the hard-coded default).
 * @property description human-readable description from the DB row, or `null` if the row
 *   is missing or has no description persisted.
 * @property source where [value] came from.
 * @property restartPending `true` if [SettingKey.requiresRestart] and the DB value has
 *   diverged from the value the JVM loaded at boot. UI hint only.
 */
data class SettingSnapshot(
    val key: SettingKey,
    val value: String,
    val description: String?,
    val source: SettingSource,
    val restartPending: Boolean,
)
