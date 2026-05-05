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
import io.plugwerk.server.domain.SettingValueType
import io.plugwerk.server.repository.ApplicationSettingRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.crypto.encrypt.TextEncryptor
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
 * [ApplicationSettingKey.validate] before persisting.
 *
 * A per-key **boot snapshot** is captured once at startup so the Admin UI can flag keys
 * whose current DB value differs from the value the running JVM loaded at boot
 * (e.g. `upload.max_file_size_mb`, which the multipart filter only picks up on restart —
 * see [ApplicationSettingKey.requiresRestart]).
 *
 * **Encryption (#253):** keys with [SettingValueType.PASSWORD] are encrypted before being
 * persisted (via the `TextEncryptor` bean from `SecurityConfiguration`) and decrypted only
 * when an internal consumer asks for the plaintext via a typed accessor like
 * [smtpPasswordPlaintext]. The cached snapshot stores ciphertext; the GET-response masking
 * layer in `AdminSettingsController.toDto` returns `***` so plaintext never leaves the
 * service layer.
 *
 * The encryptor is injected via [ObjectProvider] so the service can be constructed in
 * tests without the full Security autoconfiguration; a missing encryptor is a hard error
 * only when a PASSWORD-typed setting is actually written or decrypted.
 */
@Service
class ApplicationSettingsService(
    private val repository: ApplicationSettingRepository,
    encryptorProvider: ObjectProvider<TextEncryptor>,
) {

    private val log = LoggerFactory.getLogger(ApplicationSettingsService::class.java)

    private val encryptor: TextEncryptor? = encryptorProvider.ifAvailable

    /** Live snapshot of `setting_key -> stored row data`. Updated atomically on writes. */
    private val cache = AtomicReference<Map<String, StoredSetting>>(emptyMap())

    /**
     * Snapshot of the values as they were at JVM startup. Immutable for the lifetime of the
     * process. Used to compute [SettingSnapshot.restartPending] — a UI hint, not a hard
     * enforcement mechanism.
     */
    private lateinit var bootSnapshot: Map<String, StoredSetting>

    /**
     * Hooks invoked after every successful [update]. Lets settings-derived
     * components (currently only the SMTP `MailSenderProvider` for #253)
     * invalidate any cached state without having to poll. Keep light — runs
     * synchronously inside the write transaction.
     */
    private val updateListeners = mutableListOf<(ApplicationSettingKey) -> Unit>()

    @PostConstruct
    fun initialize() {
        refreshCache()
        bootSnapshot = cache.get()
        log.info("ApplicationSettingsService initialized with {} setting(s)", bootSnapshot.size)
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
     * Registers a callback fired after every successful write. Listeners receive the key
     * that was updated and run inside the write transaction — keep them fast and
     * non-throwing. The current consumer is the SMTP `MailSenderProvider` (#253), which
     * uses the hook to invalidate its cached `JavaMailSender` when any `smtp.*` setting
     * changes.
     */
    fun addUpdateListener(listener: (ApplicationSettingKey) -> Unit) {
        updateListeners.add(listener)
    }

    /**
     * Returns the raw string value for [key], falling back to [ApplicationSettingKey.defaultValue] if
     * the row is missing or the stored value is an empty string. For PASSWORD-typed keys
     * the returned value is **ciphertext** — call a typed accessor like
     * [smtpPasswordPlaintext] when the consumer needs the decrypted value.
     */
    fun getRaw(key: ApplicationSettingKey): String {
        val stored = cache.get()[key.key]?.rawValue
        return if (stored.isNullOrEmpty()) key.defaultValue else stored
    }

    /** Typed accessor: `upload.max_file_size_mb` as an Int, clamped to the hard ceiling. */
    fun maxUploadSizeMb(): Int = getRaw(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB).toIntOrNull()
        ?.coerceIn(1, MAX_ALLOWED_UPLOAD_MB)
        ?: ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB.defaultValue.toInt()

    /** Typed accessor: `tracking.enabled`. */
    fun trackingEnabled(): Boolean = getRaw(ApplicationSettingKey.TRACKING_ENABLED).toBooleanStrict()

    /** Typed accessor: `tracking.capture_ip`. */
    fun trackingCaptureIp(): Boolean = getRaw(ApplicationSettingKey.TRACKING_CAPTURE_IP).toBooleanStrict()

    /** Typed accessor: `tracking.anonymize_ip`. */
    fun trackingAnonymizeIp(): Boolean = getRaw(ApplicationSettingKey.TRACKING_ANONYMIZE_IP).toBooleanStrict()

    /** Typed accessor: `tracking.capture_user_agent`. */
    fun trackingCaptureUserAgent(): Boolean =
        getRaw(ApplicationSettingKey.TRACKING_CAPTURE_USER_AGENT).toBooleanStrict()

    /** Typed accessor: `general.default_language`. */
    fun defaultLanguage(): String = getRaw(ApplicationSettingKey.GENERAL_DEFAULT_LANGUAGE)

    /** Typed accessor: `general.site_name`. */
    fun siteName(): String = getRaw(ApplicationSettingKey.GENERAL_SITE_NAME)

    /** Typed accessor: `general.default_timezone` (IANA id, e.g. `UTC`, `Europe/Berlin`). */
    fun defaultTimezone(): String = getRaw(ApplicationSettingKey.GENERAL_DEFAULT_TIMEZONE)

    // ---- SMTP (#253) -------------------------------------------------------

    /** Typed accessor: `smtp.enabled` master switch. */
    fun smtpEnabled(): Boolean = getRaw(ApplicationSettingKey.SMTP_ENABLED).toBooleanStrict()

    /** Typed accessor: `smtp.host`. Empty string when unset. */
    fun smtpHost(): String = getRaw(ApplicationSettingKey.SMTP_HOST)

    /** Typed accessor: `smtp.port` (1–65535). */
    fun smtpPort(): Int = getRaw(ApplicationSettingKey.SMTP_PORT).toIntOrNull()
        ?.coerceIn(1, 65535)
        ?: ApplicationSettingKey.SMTP_PORT.defaultValue.toInt()

    /** Typed accessor: `smtp.username`. Empty string means unauthenticated relay. */
    fun smtpUsername(): String = getRaw(ApplicationSettingKey.SMTP_USERNAME)

    /**
     * Typed accessor: `smtp.password` decrypted to plaintext. Empty string when unset.
     *
     * **Internal-only.** Never include this in any DTO or log line — the masking layer
     * in `AdminSettingsController.toDto` exists precisely so plaintext never leaves the
     * service layer through user-facing channels.
     */
    fun smtpPasswordPlaintext(): String {
        val stored = cache.get()[ApplicationSettingKey.SMTP_PASSWORD.key]?.rawValue ?: return ""
        if (stored.isEmpty()) return ""
        return decryptOrNull(stored) ?: run {
            log.warn(
                "Could not decrypt smtp.password — value is corrupted or the encryption key " +
                    "changed. Treating as unset.",
            )
            ""
        }
    }

    /** Typed accessor: `smtp.encryption` ∈ {`none`, `starttls`, `tls`}. */
    fun smtpEncryption(): String = getRaw(ApplicationSettingKey.SMTP_ENCRYPTION)

    /** Typed accessor: `smtp.from_address`. Empty string when unset. */
    fun smtpFromAddress(): String = getRaw(ApplicationSettingKey.SMTP_FROM_ADDRESS)

    /** Typed accessor: `smtp.from_name`. */
    fun smtpFromName(): String = getRaw(ApplicationSettingKey.SMTP_FROM_NAME)

    // ---- Self-registration (#420) ------------------------------------------

    /** Master switch: surface the public registration endpoint at all? */
    fun selfRegistrationEnabled(): Boolean =
        getRaw(ApplicationSettingKey.AUTH_SELF_REGISTRATION_ENABLED).toBooleanStrict()

    /**
     * When self-registration IS on, must the user click an emailed link
     * before their account is enabled? Default true; turning it off skips
     * the verification email and creates already-enabled users.
     */
    fun selfRegistrationEmailVerificationRequired(): Boolean =
        getRaw(ApplicationSettingKey.AUTH_SELF_REGISTRATION_EMAIL_VERIFICATION_REQUIRED).toBooleanStrict()

    // ---- Password reset (#421) ---------------------------------------------

    /** Master switch: surface the public forgot-password / reset-password endpoints at all? */
    fun passwordResetEnabled(): Boolean =
        getRaw(ApplicationSettingKey.AUTH_PASSWORD_RESET_ENABLED).toBooleanStrict()

    /**
     * How long an issued reset link stays valid, in minutes. Operator-tunable
     * 5..1440 (enforced by the enum's minInt/maxInt) — defaults to 60.
     */
    fun passwordResetTokenTtlMinutes(): Int =
        getRaw(ApplicationSettingKey.AUTH_PASSWORD_RESET_TOKEN_TTL_MINUTES).toIntOrNull()
            ?.coerceIn(5, 1440)
            ?: ApplicationSettingKey.AUTH_PASSWORD_RESET_TOKEN_TTL_MINUTES.defaultValue.toInt()

    // ------------------------------------------------------------------------

    /**
     * Returns a complete snapshot of every key, including its effective value, description,
     * source (`DATABASE` if a row exists, `DEFAULT` otherwise), and a `restartPending` flag
     * for keys whose DB value has changed since the JVM started.
     *
     * **PASSWORD-typed keys carry ciphertext in the returned [SettingSnapshot.value].**
     * The DTO conversion in `AdminSettingsController` masks them to `***` before they leave
     * the process. Internal callers that need plaintext use the typed accessors instead.
     */
    fun listAll(): List<SettingSnapshot> {
        val current = cache.get()
        val boot = if (::bootSnapshot.isInitialized) bootSnapshot else current
        return ApplicationSettingKey.entries.map { key ->
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
     * For PASSWORD-typed keys the [rawValue] is encrypted at rest before being persisted.
     * The masking sentinel `***` (which the Admin UI surfaces in GET responses) is treated
     * as "no change" — writing it back does not re-encrypt random bytes as a password.
     *
     * @throws IllegalArgumentException if [rawValue] fails [ApplicationSettingKey.validate].
     * @throws IllegalStateException if [key] is PASSWORD-typed and no [TextEncryptor] is
     *   available (Security autoconfiguration not loaded).
     */
    @Transactional
    fun update(key: ApplicationSettingKey, rawValue: String, updatedBy: String?): SettingSnapshot {
        // PASSWORD masking sentinel — when the Admin UI submits the form
        // unchanged, every password field comes back as "***". Treat that as
        // a no-op so we never encrypt the literal string "***" as if it were
        // the new password (#253).
        if (key.valueType == SettingValueType.PASSWORD && rawValue == MASKED_VALUE) {
            return listAll().first { it.key == key }
        }
        key.validate(rawValue)?.let { error ->
            throw IllegalArgumentException("Invalid value for setting '${key.key}': $error")
        }
        val storedValue = if (key.valueType == SettingValueType.PASSWORD && rawValue.isNotEmpty()) {
            requireNotNull(encryptor) {
                "TextEncryptor bean is required to write PASSWORD settings — Security " +
                    "autoconfiguration is not loaded in this context"
            }.encrypt(rawValue)
        } else {
            rawValue
        }
        val existing = repository.findBySettingKey(key.key).orElse(null)
        val entity = existing?.apply {
            settingValue = storedValue
            this.updatedBy = updatedBy
        } ?: ApplicationSettingEntity(
            settingKey = key.key,
            settingValue = storedValue,
            valueType = key.valueType,
            updatedBy = updatedBy,
        )
        repository.save(entity)
        refreshCache()
        notifyListeners(key)
        return listAll().first { it.key == key }
    }

    private fun notifyListeners(key: ApplicationSettingKey) {
        for (listener in updateListeners) {
            try {
                listener(key)
            } catch (ex: Exception) {
                log.warn("Settings update listener threw for key '{}': {}", key.key, ex.message)
            }
        }
    }

    private fun decryptOrNull(ciphertext: String): String? = try {
        encryptor?.decrypt(ciphertext)
    } catch (_: Exception) {
        null
    }

    companion object {
        /**
         * Sentinel value rendered for PASSWORD-typed settings in GET responses.
         * Also recognised by [update] as "no change" so a round-trip through the
         * Admin UI does not silently overwrite the stored ciphertext with the
         * literal string `"***"`.
         */
        const val MASKED_VALUE: String = "***"
    }
}

/** Origin of the effective value returned by [ApplicationSettingsService.listAll]. */
enum class SettingSource {
    /** Value is backed by a row in `application_setting`. */
    DATABASE,

    /** No row exists — the value is the hard-coded default in [ApplicationSettingKey]. */
    DEFAULT,
}

/**
 * Internal cache row — holds the raw stored value plus the DB-persisted description.
 */
internal data class StoredSetting(val rawValue: String, val description: String?)

/**
 * A point-in-time view of one setting.
 *
 * @property key the [ApplicationSettingKey] entry.
 * @property value the effective raw string value (either from DB or the hard-coded default).
 *   For PASSWORD-typed keys this is ciphertext; consumers that need plaintext use the
 *   typed accessors on [ApplicationSettingsService] instead.
 * @property description human-readable description from the DB row, or `null` if the row
 *   is missing or has no description persisted.
 * @property source where [value] came from.
 * @property restartPending `true` if [ApplicationSettingKey.requiresRestart] and the DB value has
 *   diverged from the value the JVM loaded at boot. UI hint only.
 */
data class SettingSnapshot(
    val key: ApplicationSettingKey,
    val value: String,
    val description: String?,
    val source: SettingSource,
    val restartPending: Boolean,
)
