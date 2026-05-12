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
package io.plugwerk.server.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * Cluster-coordination primitive for `@Scheduled` jobs (#190 prerequisite).
 *
 * Plugwerk's deployment model since #191 supports horizontal scaling. The four
 * existing cleanup jobs (`RefreshTokenService.cleanupExpired`,
 * `TokenRevocationService.cleanupExpired`, `PasswordResetTokenService.sweep`,
 * `EmailVerificationTokenService.sweep`) are idempotent — N-fold execution
 * does not corrupt data — but they each issue the same `DELETE WHERE ...`
 * statement N times per tick, spiking DB contention without benefit. The
 * orphan-reaper added in #496 is materially different: even though storage
 * `delete` is idempotent, two instances doing list+delete in parallel doubles
 * the storage API cost and produces confusing duplicate log lines.
 *
 * [JdbcTemplateLockProvider] persists leases in the `shedlock` table
 * (migration `0033_shedlock.yaml`). UTC timezone is forced so leases compare
 * correctly across instances running with different `TZ` env vars.
 *
 * Per-job `lockAtMostFor` lives on each `@SchedulerLock` annotation — picking
 * one global default would either deadlock recovery after instance crash or
 * leave a stuck job blocking forever.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class SchedulerLockConfig {

    /**
     * Real Postgres-backed lock provider. Active by default; the H2-backed
     * unit-test slices (`application-test.yml`) flip
     * `plugwerk.scheduler.shedlock.enabled=false` because H2 does not run the
     * Liquibase migration that creates the `shedlock` table.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "plugwerk.scheduler.shedlock",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun lockProvider(dataSource: DataSource): LockProvider = JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(JdbcTemplate(dataSource))
            .withTimeZone(java.util.TimeZone.getTimeZone(ZoneOffset.UTC))
            // JVM time, not DB time: usingDbTime() requires a Postgres-
            // specific SELECT pg_… probe that H2 (used in unit tests)
            // does not implement. Plugwerk hosts run NTP-synchronised so
            // JVM-time skew is in the millisecond range; lock granularity
            // here is minutes/hours.
            .build(),
    )

    /**
     * Fallback when ShedLock is disabled (test profile, single-node deploys
     * that don't care about cluster coordination). Without a [LockProvider]
     * bean, every `@SchedulerLock` method throws at AOP-interception time.
     * The no-op makes the annotation an inert documentation hint.
     */
    @Bean
    @ConditionalOnMissingBean(LockProvider::class)
    fun noOpLockProvider(): LockProvider = net.javacrumbs.shedlock.core.LockProvider {
        java.util.Optional.of(
            net.javacrumbs.shedlock.core.SimpleLock {
                // no-op: nothing to release
            },
        )
    }
}
