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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertFailsWith

/**
 * Branch-coverage tests for [ApplicationSettingsService] (ADR-0016) that
 * complement the existing `ApplicationSettingsServiceTest` (which focuses on
 * the #478 cache/listener timing contract).
 *
 * This class drives the remaining decision arms:
 *  - `getRaw`: null row, empty stored value, present value.
 *  - typed accessors with `toIntOrNull`/`coerceIn`/`toBooleanStrict`: parse
 *    failure -> default, clamp-low, clamp-high, valid pass-through.
 *  - `listAll`: stored null/empty/value, source DEFAULT vs DATABASE, and the
 *    `restartPending` true/false arms for a `requiresRestart` key.
 *  - `update`: PASSWORD masking-sentinel no-op, validation failure throw,
 *    PASSWORD encryption with a missing encryptor (`requireNotNull` throw) and
 *    with a present encryptor, plus the existing-vs-new entity branches.
 *  - `smtpPasswordPlaintext`: missing row, empty ciphertext, successful decrypt.
 *  - `notifyListeners`: a throwing listener is swallowed and logged.
 *
 * The harness mirrors the sibling test: the service is wired directly with a
 * mocked repository backed by an in-memory map, and the private `@PostConstruct`
 * is invoked via reflection so `bootSnapshot` is initialised without Spring.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationSettingsServiceBranchCoverageTest {

    @Mock lateinit var repository: ApplicationSettingRepository

    private lateinit var service: ApplicationSettingsService
    private val storage = ConcurrentHashMap<String, ApplicationSettingEntity>()

    @BeforeEach
    fun setUp() {
        whenever(repository.findBySettingKey(any())).thenAnswer { inv ->
            Optional.ofNullable(storage[inv.getArgument<String>(0)])
        }
        whenever(repository.findAll()).thenAnswer { storage.values.toMutableList() }
        whenever(repository.save(any<ApplicationSettingEntity>())).thenAnswer(this::recordSave)

        service = ApplicationSettingsService(
            repository = repository,
            encryptorProvider = EmptyEncryptorProvider,
        )
        invokePostConstruct(service)
    }

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear()
        }
    }

    // ---- getRaw ------------------------------------------------------------

    @Test
    fun `getRaw falls back to the default when no row is cached`() {
        // Empty store -> cache miss -> default branch.
        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME))
            .isEqualTo(ApplicationSettingKey.GENERAL_SITE_NAME.defaultValue)
    }

    @Test
    fun `getRaw falls back to the default when the cached value is empty`() {
        seed(ApplicationSettingKey.GENERAL_SITE_NAME, "")
        invokePostConstruct(service)

        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME))
            .isEqualTo(ApplicationSettingKey.GENERAL_SITE_NAME.defaultValue)
    }

    @Test
    fun `getRaw returns the cached value when present and non-empty`() {
        seed(ApplicationSettingKey.GENERAL_SITE_NAME, "Custom Site")
        invokePostConstruct(service)

        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME)).isEqualTo("Custom Site")
    }

    // ---- typed Int accessors: toIntOrNull / coerceIn -----------------------

    @Test
    fun `maxUploadSizeMb defaults when the stored value is not an integer`() {
        seed(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB, "not-a-number")
        invokePostConstruct(service)

        assertThat(service.maxUploadSizeMb())
            .isEqualTo(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB.defaultValue.toInt())
    }

    @Test
    fun `maxUploadSizeMb clamps a value below the floor up to 1`() {
        seed(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB, "0")
        invokePostConstruct(service)

        assertThat(service.maxUploadSizeMb()).isEqualTo(1)
    }

    @Test
    fun `maxUploadSizeMb clamps a value above the ceiling down to the hard cap`() {
        seed(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB, "999999")
        invokePostConstruct(service)

        assertThat(service.maxUploadSizeMb()).isEqualTo(MAX_ALLOWED_UPLOAD_MB)
    }

    @Test
    fun `maxUploadSizeMb returns an in-range value unchanged`() {
        seed(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB, "250")
        invokePostConstruct(service)

        assertThat(service.maxUploadSizeMb()).isEqualTo(250)
    }

    @Test
    fun `smtpPort defaults when the stored value is not an integer`() {
        seed(ApplicationSettingKey.SMTP_PORT, "abc")
        invokePostConstruct(service)

        assertThat(service.smtpPort())
            .isEqualTo(ApplicationSettingKey.SMTP_PORT.defaultValue.toInt())
    }

    @Test
    fun `smtpPort clamps an out-of-range value to the upper bound`() {
        seed(ApplicationSettingKey.SMTP_PORT, "70000")
        invokePostConstruct(service)

        assertThat(service.smtpPort()).isEqualTo(65535)
    }

    @Test
    fun `smtpPort returns an in-range value unchanged`() {
        seed(ApplicationSettingKey.SMTP_PORT, "2525")
        invokePostConstruct(service)

        assertThat(service.smtpPort()).isEqualTo(2525)
    }

    @Test
    fun `passwordResetTokenTtlMinutes defaults on a non-integer value`() {
        seed(ApplicationSettingKey.AUTH_PASSWORD_RESET_TOKEN_TTL_MINUTES, "soon")
        invokePostConstruct(service)

        assertThat(service.passwordResetTokenTtlMinutes())
            .isEqualTo(ApplicationSettingKey.AUTH_PASSWORD_RESET_TOKEN_TTL_MINUTES.defaultValue.toInt())
    }

    @Test
    fun `passwordResetTokenTtlMinutes clamps below the floor up to 5`() {
        seed(ApplicationSettingKey.AUTH_PASSWORD_RESET_TOKEN_TTL_MINUTES, "1")
        invokePostConstruct(service)

        assertThat(service.passwordResetTokenTtlMinutes()).isEqualTo(5)
    }

    @Test
    fun `passwordResetTokenTtlMinutes returns an in-range value unchanged`() {
        seed(ApplicationSettingKey.AUTH_PASSWORD_RESET_TOKEN_TTL_MINUTES, "120")
        invokePostConstruct(service)

        assertThat(service.passwordResetTokenTtlMinutes()).isEqualTo(120)
    }

    // ---- typed boolean / string accessors ----------------------------------

    @Test
    fun `boolean accessors read both true and false stored values`() {
        seed(ApplicationSettingKey.TRACKING_ENABLED, "false")
        seed(ApplicationSettingKey.SMTP_ENABLED, "true")
        invokePostConstruct(service)

        assertThat(service.trackingEnabled()).isFalse()
        assertThat(service.smtpEnabled()).isTrue()
    }

    @Test
    fun `string accessors surface the configured values`() {
        seed(ApplicationSettingKey.SMTP_HOST, "smtp.example.com")
        seed(ApplicationSettingKey.SMTP_FROM_NAME, "Ops")
        invokePostConstruct(service)

        assertThat(service.smtpHost()).isEqualTo("smtp.example.com")
        assertThat(service.smtpFromName()).isEqualTo("Ops")
        // Unset string accessor returns the (empty) default.
        assertThat(service.smtpUsername()).isEqualTo(ApplicationSettingKey.SMTP_USERNAME.defaultValue)
    }

    // ---- listAll -----------------------------------------------------------

    @Test
    fun `listAll marks an unset key as DEFAULT and a set key as DATABASE`() {
        seed(ApplicationSettingKey.GENERAL_SITE_NAME, "Configured")
        invokePostConstruct(service)

        val all = service.listAll().associateBy { it.key }

        val configured = all.getValue(ApplicationSettingKey.GENERAL_SITE_NAME)
        assertThat(configured.source).isEqualTo(SettingSource.DATABASE)
        assertThat(configured.value).isEqualTo("Configured")

        // A key with no row -> DEFAULT source + default value.
        val unset = all.getValue(ApplicationSettingKey.SMTP_HOST)
        assertThat(unset.source).isEqualTo(SettingSource.DEFAULT)
        assertThat(unset.value).isEqualTo(ApplicationSettingKey.SMTP_HOST.defaultValue)
    }

    @Test
    fun `listAll treats a row with an empty value as DATABASE-sourced but default-valued`() {
        // stored != null but rawValue is empty -> the middle `when` arm.
        seed(ApplicationSettingKey.GENERAL_SITE_NAME, "")
        invokePostConstruct(service)

        val snapshot = service.listAll().first { it.key == ApplicationSettingKey.GENERAL_SITE_NAME }
        assertThat(snapshot.source).isEqualTo(SettingSource.DATABASE)
        assertThat(snapshot.value).isEqualTo(ApplicationSettingKey.GENERAL_SITE_NAME.defaultValue)
    }

    @Test
    fun `listAll does not flag restartPending when boot and effective values agree`() {
        // requiresRestart key with no divergence -> restartPending == false.
        val snapshot = service.listAll()
            .first { it.key == ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB }
        assertThat(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB.requiresRestart).isTrue()
        assertThat(snapshot.restartPending).isFalse()
    }

    @Test
    fun `listAll flags restartPending when a requiresRestart key diverges from its boot value`() {
        // Boot snapshot captured with the default. Now mutate the underlying store
        // and refresh only the live cache (not the boot snapshot) so the two diverge.
        seed(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB, "512")
        invokeRefreshCache(service)

        val snapshot = service.listAll()
            .first { it.key == ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB }
        assertThat(snapshot.value).isEqualTo("512")
        assertThat(snapshot.restartPending).isTrue()
    }

    // ---- update: validation + masking sentinel -----------------------------

    @Test
    fun `update on a PASSWORD key with the masking sentinel is a no-op`() {
        // Pre-seed ciphertext; submitting "***" must not overwrite it.
        seed(ApplicationSettingKey.SMTP_PASSWORD, "existing-cipher")
        invokePostConstruct(service)

        val snapshot = service.update(
            ApplicationSettingKey.SMTP_PASSWORD,
            ApplicationSettingsService.MASKED_VALUE,
            "admin",
        )

        // Returned from listAll (the early-return arm), and nothing was persisted.
        assertThat(snapshot.key).isEqualTo(ApplicationSettingKey.SMTP_PASSWORD)
        // The store still holds the original ciphertext — save() was never called for it.
        assertThat(storage[ApplicationSettingKey.SMTP_PASSWORD.key]?.settingValue)
            .isEqualTo("existing-cipher")
    }

    @Test
    fun `update throws IllegalArgumentException when validation fails`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            // UPLOAD_MAX_FILE_SIZE_MB is INTEGER 1..1024.
            service.update(ApplicationSettingKey.UPLOAD_MAX_FILE_SIZE_MB, "99999", "admin")
        }
        assertThat(ex.message).contains("Invalid value for setting 'upload.max_file_size_mb'")
    }

    @Test
    fun `update on a PASSWORD key without an encryptor throws when encryption is required`() {
        // EmptyEncryptorProvider yields no encryptor -> requireNotNull throws.
        // Kotlin's requireNotNull raises IllegalArgumentException (the KDoc's
        // "@throws IllegalStateException" is a doc-level imprecision).
        val ex = assertFailsWith<IllegalArgumentException> {
            service.update(ApplicationSettingKey.SMTP_PASSWORD, "new-secret", "admin")
        }
        assertThat(ex.message).contains("TextEncryptor bean is required")
    }

    @Test
    fun `update on a PASSWORD key with an empty value skips encryption and stores blank`() {
        // PASSWORD + isEmpty -> the else arm of storedValue, no encryptor needed.
        val snapshot = service.update(ApplicationSettingKey.SMTP_PASSWORD, "", "admin")

        assertThat(snapshot.source).isEqualTo(SettingSource.DATABASE)
        assertThat(storage[ApplicationSettingKey.SMTP_PASSWORD.key]?.settingValue).isEmpty()
    }

    @Test
    fun `update encrypts a non-empty PASSWORD value when an encryptor is available`() {
        val encryptingService = newServiceWithEncryptor(
            object : StubEncryptor {
                override fun encrypt(text: String) = "ENC($text)"
                override fun decrypt(encryptedText: String) = encryptedText.removeSurrounding("ENC(", ")")
            },
        )

        val snapshot = encryptingService.update(ApplicationSettingKey.SMTP_PASSWORD, "hunter2", "admin")

        // The masking layer is in the controller; the snapshot value here is the ciphertext.
        assertThat(snapshot.value).isEqualTo("ENC(hunter2)")
        assertThat(storage[ApplicationSettingKey.SMTP_PASSWORD.key]?.settingValue).isEqualTo("ENC(hunter2)")
    }

    @Test
    fun `update inserts a new entity when no row exists for the key`() {
        val snapshot = service.update(ApplicationSettingKey.GENERAL_SITE_NAME, "Fresh", "admin")

        assertThat(snapshot.value).isEqualTo("Fresh")
        assertThat(storage[ApplicationSettingKey.GENERAL_SITE_NAME.key]?.settingValue).isEqualTo("Fresh")
        assertThat(storage[ApplicationSettingKey.GENERAL_SITE_NAME.key]?.updatedBy).isEqualTo("admin")
    }

    @Test
    fun `update reuses an existing entity rather than inserting a duplicate`() {
        val pre = ApplicationSettingEntity(
            settingKey = ApplicationSettingKey.GENERAL_SITE_NAME.key,
            settingValue = "Old",
            valueType = SettingValueType.STRING,
            updatedBy = "seed",
        )
        storage[ApplicationSettingKey.GENERAL_SITE_NAME.key] = pre
        invokePostConstruct(service)

        val snapshot = service.update(ApplicationSettingKey.GENERAL_SITE_NAME, "New", "admin2")

        assertThat(snapshot.value).isEqualTo("New")
        // Same row object mutated in place (existing branch), value + updatedBy refreshed.
        assertThat(storage[ApplicationSettingKey.GENERAL_SITE_NAME.key]).isSameAs(pre)
        assertThat(pre.settingValue).isEqualTo("New")
        assertThat(pre.updatedBy).isEqualTo("admin2")
    }

    @Test
    fun `update with a null updatedBy persists a null principal`() {
        service.update(ApplicationSettingKey.GENERAL_SITE_NAME, "Anon", null)

        assertThat(storage[ApplicationSettingKey.GENERAL_SITE_NAME.key]?.updatedBy).isNull()
    }

    // ---- smtpPasswordPlaintext ---------------------------------------------

    @Test
    fun `smtpPasswordPlaintext returns empty when no row is stored`() {
        assertThat(service.smtpPasswordPlaintext()).isEmpty()
    }

    @Test
    fun `smtpPasswordPlaintext returns empty when the stored ciphertext is blank`() {
        seed(ApplicationSettingKey.SMTP_PASSWORD, "")
        invokePostConstruct(service)

        assertThat(service.smtpPasswordPlaintext()).isEmpty()
    }

    @Test
    fun `smtpPasswordPlaintext decrypts a stored ciphertext via the encryptor`() {
        val decryptingService = newServiceWithEncryptor(
            object : StubEncryptor {
                override fun encrypt(text: String) = "ENC($text)"
                override fun decrypt(encryptedText: String) = encryptedText.removeSurrounding("ENC(", ")")
            },
        )
        storage[ApplicationSettingKey.SMTP_PASSWORD.key] = ApplicationSettingEntity(
            settingKey = ApplicationSettingKey.SMTP_PASSWORD.key,
            settingValue = "ENC(plaintext-pw)",
            valueType = SettingValueType.PASSWORD,
        )
        invokeRefreshCache(decryptingService)

        assertThat(decryptingService.smtpPasswordPlaintext()).isEqualTo("plaintext-pw")
    }

    @Test
    fun `smtpPasswordPlaintext returns empty when ciphertext is present but no encryptor is wired`() {
        // encryptor?.decrypt(stored) ?: "" — the elvis-null arm.
        seed(ApplicationSettingKey.SMTP_PASSWORD, "some-cipher")
        invokePostConstruct(service)

        assertThat(service.smtpPasswordPlaintext()).isEmpty()
    }

    // ---- notifyListeners ----------------------------------------------------

    @Test
    fun `a throwing update listener is swallowed and does not break the write`() {
        val good = mutableListOf<ApplicationSettingKey>()
        service.addUpdateListener { throw RuntimeException("boom") }
        service.addUpdateListener { key -> good.add(key) }

        // No active transaction -> refresh + notify run synchronously.
        service.update(ApplicationSettingKey.GENERAL_SITE_NAME, "Notify", "admin")

        // The second listener still fired despite the first throwing.
        assertThat(good).containsExactly(ApplicationSettingKey.GENERAL_SITE_NAME)
        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME)).isEqualTo("Notify")
    }

    // ---- helpers -----------------------------------------------------------

    private fun seed(key: ApplicationSettingKey, value: String?) {
        storage[key.key] = ApplicationSettingEntity(
            settingKey = key.key,
            settingValue = value,
            valueType = key.valueType,
        )
    }

    private fun newServiceWithEncryptor(encryptor: TextEncryptor): ApplicationSettingsService {
        val provider = object : ObjectProvider<TextEncryptor> {
            override fun getObject(): TextEncryptor = encryptor
            override fun getObject(vararg args: Any?): TextEncryptor = encryptor
            override fun getIfAvailable(): TextEncryptor = encryptor
            override fun getIfUnique(): TextEncryptor = encryptor
        }
        val s = ApplicationSettingsService(repository = repository, encryptorProvider = provider)
        invokePostConstruct(s)
        return s
    }

    /** TextEncryptor is a SAM-ish interface with two methods; this keeps stubs terse. */
    private interface StubEncryptor : TextEncryptor

    @Suppress("UNCHECKED_CAST")
    private fun <T> recordSave(invocation: InvocationOnMock): T {
        val entity = invocation.getArgument<ApplicationSettingEntity>(0)
        storage[entity.settingKey] = entity
        return entity as T
    }

    private fun invokePostConstruct(target: ApplicationSettingsService) {
        val initFn = ApplicationSettingsService::class.declaredMemberFunctions
            .first { fn -> fn.annotations.any { it.annotationClass.simpleName == "PostConstruct" } }
        initFn.isAccessible = true
        initFn.call(target)
    }

    private fun invokeRefreshCache(target: ApplicationSettingsService) {
        val refreshFn = ApplicationSettingsService::class.declaredMemberFunctions
            .first { it.name == "refreshCache" }
        refreshFn.isAccessible = true
        refreshFn.call(target)
    }

    private object EmptyEncryptorProvider : ObjectProvider<TextEncryptor> {
        override fun getObject(): TextEncryptor = error("not used in these tests")
        override fun getObject(vararg args: Any?): TextEncryptor = error("not used in these tests")
        override fun getIfAvailable(): TextEncryptor? = null
        override fun getIfUnique(): TextEncryptor? = null
    }
}
