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

import io.plugwerk.server.service.mail.MailSenderProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * End-to-end verification of the cache/DB-consistency contract for
 * [ApplicationSettingsService.update] (#478).
 *
 * Background: the previous implementation called `refreshCache()` and
 * `notifyListeners()` _inside_ the `@Transactional` boundary, before commit.
 * If the transaction rolled back for any reason after those calls, the
 * in-memory cache held a value that was never persisted — a divergence that
 * was not self-healing (no TTL, no refresh on read) and persisted until JVM
 * restart.
 *
 * The fix moves both calls into a `TransactionSynchronization.afterCommit`
 * hook so the cache only ever reflects committed state.
 *
 * These tests run with `@Tag("integration")` so they participate in
 * `:integrationTest` (full Spring context, Hibernate, real H2) but not in
 * the unit `:test` task.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:settings-rollback-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
@Tag("integration")
class ApplicationSettingsRollbackIT {

    @Autowired
    private lateinit var settings: ApplicationSettingsService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var mailSenderProvider: MailSenderProvider

    @BeforeEach
    fun seedKnownState() {
        // Set a known starting value so the post-rollback assertion has
        // something concrete to compare against. This commit succeeds — the
        // rollback test below operates on top of this committed state.
        settings.update(ApplicationSettingKey.SMTP_HOST, "host-a.test", "test-setup")
    }

    @Test
    fun `cache reflects committed DB state after enclosing transaction rolls back`() {
        // Pre: known committed state.
        assertThat(settings.smtpHost()).isEqualTo("host-a.test")

        // Run an outer transaction that calls update(...) and then forces
        // rollback via setRollbackOnly. update()'s own @Transactional
        // propagates REQUIRED, so it joins the outer transaction and is
        // rolled back together with it.
        val template = TransactionTemplate(transactionManager)
        template.execute { status ->
            settings.update(ApplicationSettingKey.SMTP_HOST, "host-b.test", "test-rollback")
            status.setRollbackOnly()
        }

        // Post: the DB never persisted "host-b.test". The cache must agree.
        // Before the #478 fix this assertion failed — the cache held
        // "host-b.test" from the pre-commit refreshCache() call, while the
        // DB still had "host-a.test".
        assertThat(settings.smtpHost())
            .withFailMessage(
                "Cache divergence after rollback: smtpHost() returned '%s', expected 'host-a.test'. " +
                    "This means refreshCache() ran before commit (issue #478).",
                settings.smtpHost(),
            )
            .isEqualTo("host-a.test")
    }

    @Test
    fun `MailSenderProvider listener is not invoked when enclosing transaction rolls back`() {
        // Force the cache to materialize a sender so we can detect a stale
        // invalidate() — invalidate() sets the cached sender to null.
        settings.update(ApplicationSettingKey.SMTP_HOST, "host-a.test", "test-setup")
        settings.update(ApplicationSettingKey.SMTP_PORT, "2525", "test-setup")
        settings.update(ApplicationSettingKey.SMTP_ENCRYPTION, "none", "test-setup")
        settings.update(ApplicationSettingKey.SMTP_FROM_ADDRESS, "noreply@plugwerk.test", "test-setup")
        settings.update(ApplicationSettingKey.SMTP_ENABLED, "true", "test-setup")
        // Trigger the lazy build so cached.get() != null afterwards.
        mailSenderProvider.current()

        val template = TransactionTemplate(transactionManager)
        template.execute { status ->
            // smtp.* update should normally invalidate the MailSenderProvider
            // cache via the registered listener. Inside a rolled-back outer
            // transaction the listener must NOT fire — otherwise the next
            // mail send would rebuild a sender from the new (uncommitted,
            // about-to-be-rolled-back) settings.
            settings.update(ApplicationSettingKey.SMTP_HOST, "host-c.test", "test-rollback")
            status.setRollbackOnly()
        }

        // Verify by checking smtpHost — if the listener had fired AND
        // refreshCache had run pre-commit, the next mail-send would be
        // rebuilt against "host-c.test". Post-fix, the cache still reads
        // "host-a.test" and any rebuild uses that.
        assertThat(settings.smtpHost()).isEqualTo("host-a.test")
    }
}
