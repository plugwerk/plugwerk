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
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Unit tests for the cache + listener timing contract of
 * [ApplicationSettingsService.update] (#478).
 *
 * The integration counterpart `ApplicationSettingsRollbackIT` exercises the
 * same behaviour against a real Spring context + Hibernate; this class
 * isolates the four code paths of the `scheduleCacheRefreshAfterCommit`
 * helper using [TransactionSynchronizationManager] directly, no Spring
 * proxy required.
 *
 * `@Transactional`-proxy interception is bypassed because the service is
 * instantiated directly. That is exactly the path the
 * `update outside any active transaction` test exercises; the active-TX
 * tests simulate the same lifecycle Spring drives by manually firing
 * `afterCommit` / `afterCompletion` after `update()` returns.
 */
@ExtendWith(MockitoExtension::class)
class ApplicationSettingsServiceTest {

    @Mock lateinit var repository: ApplicationSettingRepository

    private lateinit var service: ApplicationSettingsService
    private lateinit var listenerCalls: MutableList<ApplicationSettingKey>
    private val storage = ConcurrentHashMap<String, ApplicationSettingEntity>()

    @BeforeEach
    fun setUp() {
        // Stub the two repository methods the service touches.
        whenever(repository.findBySettingKey(any())).thenAnswer { inv ->
            Optional.ofNullable(storage[inv.getArgument<String>(0)])
        }
        whenever(repository.findAll()).thenAnswer { storage.values.toMutableList() }
        whenever(repository.save(any<ApplicationSettingEntity>())).thenAnswer(this::recordSave)

        service = ApplicationSettingsService(
            repository = repository,
            encryptorProvider = EmptyEncryptorProvider,
        )
        // Run the @PostConstruct equivalent so bootSnapshot is initialised.
        invokePostConstruct(service)

        listenerCalls = mutableListOf()
        service.addUpdateListener { key -> listenerCalls.add(key) }
    }

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear()
        }
    }

    @Test
    fun `update inside active transaction defers cache refresh and listener until afterCommit`() {
        TransactionSynchronizationManager.initSynchronization()

        service.update(ApplicationSettingKey.GENERAL_SITE_NAME, "deferred", "test")

        // Inside the transaction the cache and listeners must NOT have been
        // touched yet. getRaw reads from cache; the post-init cache holds the
        // default for an unset key.
        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME))
            .isEqualTo(ApplicationSettingKey.GENERAL_SITE_NAME.defaultValue)
        assertThat(listenerCalls).isEmpty()

        // Fire the afterCommit hook the way Spring does at commit time.
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        // Now the cache reflects the persisted value and the listener fired
        // exactly once.
        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME)).isEqualTo("deferred")
        assertThat(listenerCalls).containsExactly(ApplicationSettingKey.GENERAL_SITE_NAME)
    }

    @Test
    fun `update inside active transaction does not refresh cache or notify listeners on rollback`() {
        TransactionSynchronizationManager.initSynchronization()

        service.update(ApplicationSettingKey.GENERAL_SITE_NAME, "rolledback", "test")

        // Simulate the lifecycle Spring drives on rollback: afterCompletion
        // with STATUS_ROLLED_BACK fires; afterCommit does NOT.
        TransactionSynchronizationManager.getSynchronizations()
            .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        // Cache untouched: default still wins because the put-into-cache
        // hook did not run.
        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME))
            .isEqualTo(ApplicationSettingKey.GENERAL_SITE_NAME.defaultValue)
        // Listener untouched.
        assertThat(listenerCalls).isEmpty()
    }

    @Test
    fun `update outside any active transaction refreshes cache and notifies listeners immediately`() {
        // Deliberately do NOT initSynchronization — this is the test-fallback
        // path the service takes when it is invoked from a context without
        // Spring's @Transactional proxy.
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse()

        service.update(ApplicationSettingKey.GENERAL_SITE_NAME, "immediate", "test")

        // Both effects happen synchronously because there is no transaction
        // to defer them to.
        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME)).isEqualTo("immediate")
        assertThat(listenerCalls).containsExactly(ApplicationSettingKey.GENERAL_SITE_NAME)
    }

    @Test
    fun `update returns snapshot derived from saved entity not from cache`() {
        // Snapshot must be built from the saved entity so the caller sees the
        // value they just wrote even though the cache refresh is deferred to
        // afterCommit. Pre-fix behaviour read from the cache and would have
        // returned either the default or stale data here, depending on timing.
        TransactionSynchronizationManager.initSynchronization()

        val snapshot = service.update(
            ApplicationSettingKey.GENERAL_SITE_NAME,
            "from-entity",
            "test",
        )

        // Verify the return value BEFORE firing afterCommit — the cache is
        // still cold at this point. The snapshot must already carry the
        // just-saved value regardless.
        assertThat(snapshot.key).isEqualTo(ApplicationSettingKey.GENERAL_SITE_NAME)
        assertThat(snapshot.value).isEqualTo("from-entity")
        assertThat(snapshot.source).isEqualTo(SettingSource.DATABASE)
        // Cache really is still cold at this point.
        assertThat(service.getRaw(ApplicationSettingKey.GENERAL_SITE_NAME))
            .isEqualTo(ApplicationSettingKey.GENERAL_SITE_NAME.defaultValue)
    }

    // ---- helpers ----------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun <T> recordSave(invocation: InvocationOnMock): T {
        val entity = invocation.getArgument<ApplicationSettingEntity>(0)
        storage[entity.settingKey] = entity
        return entity as T
    }

    private fun invokePostConstruct(target: ApplicationSettingsService) {
        // The @PostConstruct method is private; reach through reflection so
        // the test does not depend on Spring's annotation processor.
        val initFn = ApplicationSettingsService::class.declaredMemberFunctions
            .first { fn -> fn.annotations.any { it.annotationClass.simpleName == "PostConstruct" } }
        initFn.isAccessible = true
        initFn.call(target)
    }

    private object EmptyEncryptorProvider : ObjectProvider<TextEncryptor> {
        override fun getObject(): TextEncryptor = error("not used in these tests")
        override fun getObject(vararg args: Any?): TextEncryptor = error("not used in these tests")
        override fun getIfAvailable(): TextEncryptor? = null
        override fun getIfUnique(): TextEncryptor? = null
    }

    @org.junit.jupiter.api.Nested
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    inner class SmtpPasswordDecryptDiagnostics {
        @Test
        fun `bad-padding on smtp_password is logged as ERROR with PLUGWERK_AUTH_ENCRYPTION_KEY hint (#501)`() {
            // Plant a ciphertext for smtp.password, mock the encryptor to throw the
            // Spring-wrapped bad-padding shape, and verify the ERROR carries the
            // env-var name + remediation hint.
            val encryptor = org.mockito.Mockito.mock(TextEncryptor::class.java)
            org.mockito.kotlin.whenever(encryptor.decrypt("ciphertext-rotated")).thenThrow(
                java.lang.IllegalStateException(
                    "Unable to invoke Cipher due to bad padding",
                    javax.crypto.BadPaddingException("decrypt"),
                ),
            )
            val provider = object : ObjectProvider<TextEncryptor> {
                override fun getObject() = encryptor
                override fun getObject(vararg args: Any?) = encryptor
                override fun getIfAvailable() = encryptor
                override fun getIfUnique() = encryptor
            }
            storage[ApplicationSettingKey.SMTP_PASSWORD.key] = ApplicationSettingEntity(
                settingKey = ApplicationSettingKey.SMTP_PASSWORD.key,
                settingValue = "ciphertext-rotated",
                valueType = SettingValueType.PASSWORD,
            )
            val sut = ApplicationSettingsService(repository = repository, encryptorProvider = provider)
            invokePostConstruct(sut)

            val (events, detach) = captureLogs(ApplicationSettingsService::class.java)
            try {
                val plaintext = sut.smtpPasswordPlaintext()

                org.assertj.core.api.Assertions.assertThat(plaintext).isEmpty()
                val error = events.single { it.level == ch.qos.logback.classic.Level.ERROR }
                org.assertj.core.api.Assertions.assertThat(error.formattedMessage)
                    .contains("PLUGWERK_AUTH_ENCRYPTION_KEY")
                    .contains("smtp.password")
                    .contains("Re-enter the value")
            } finally {
                detach()
            }
        }

        @Test
        fun `non-bad-padding decrypt failure is logged as WARN (#501)`() {
            val encryptor = org.mockito.Mockito.mock(TextEncryptor::class.java)
            org.mockito.kotlin.whenever(encryptor.decrypt("corrupted")).thenThrow(
                java.lang.IllegalArgumentException("malformed ciphertext"),
            )
            val provider = object : ObjectProvider<TextEncryptor> {
                override fun getObject() = encryptor
                override fun getObject(vararg args: Any?) = encryptor
                override fun getIfAvailable() = encryptor
                override fun getIfUnique() = encryptor
            }
            storage[ApplicationSettingKey.SMTP_PASSWORD.key] = ApplicationSettingEntity(
                settingKey = ApplicationSettingKey.SMTP_PASSWORD.key,
                settingValue = "corrupted",
                valueType = SettingValueType.PASSWORD,
            )
            val sut = ApplicationSettingsService(repository = repository, encryptorProvider = provider)
            invokePostConstruct(sut)

            val (events, detach) = captureLogs(ApplicationSettingsService::class.java)
            try {
                sut.smtpPasswordPlaintext()
                val warn = events.single { it.level == ch.qos.logback.classic.Level.WARN }
                org.assertj.core.api.Assertions.assertThat(warn.formattedMessage)
                    .contains("Could not decrypt smtp.password")
                    .doesNotContain("PLUGWERK_AUTH_ENCRYPTION_KEY")
                org.assertj.core.api.Assertions.assertThat(
                    events.none { it.level == ch.qos.logback.classic.Level.ERROR },
                ).isTrue()
            } finally {
                detach()
            }
        }
    }

    private fun captureLogs(loggerClass: Class<*>): Pair<List<ch.qos.logback.classic.spi.ILoggingEvent>, () -> Unit> {
        val logger = org.slf4j.LoggerFactory.getLogger(loggerClass) as ch.qos.logback.classic.Logger
        val appender = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return appender.list to { logger.detachAppender(appender) }
    }
}
