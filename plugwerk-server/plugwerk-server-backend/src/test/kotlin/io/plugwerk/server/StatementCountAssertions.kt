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
package io.plugwerk.server

import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory

/**
 * Thin wrapper around Hibernate's `Statistics` used by N+1 regression tests (ADR-0023).
 *
 * The test profile enables `hibernate.generate_statistics=true` so these counters are
 * populated during the test run. Each call clears the counters before running [block],
 * flushes any pending writes, then returns the block result together with the number of
 * JDBC prepared-statement executions observed — the invariant the tests assert on.
 */
inline fun <T> EntityManager.countingStatements(block: () -> T): Pair<T, Long> {
    val sessionFactory = entityManagerFactory.unwrap(SessionFactory::class.java)
    val statistics = sessionFactory.statistics
    statistics.clear()
    val result = block()
    flush()
    return result to statistics.prepareStatementCount
}
