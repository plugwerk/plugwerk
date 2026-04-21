# ADR-0023: N+1 regression testing via Hibernate Statistics

## Status

Accepted

## Context

The 1.0.0-beta.1 security/performance audit (tracker [#255]) flagged four N+1 query patterns in the service layer (rows DB-015..018 / [#273]). Fixing the patterns is mechanical (collapse per-row loops into batch `IN` queries or projection queries); the harder question is how to pin the fixes so a future refactor cannot silently reintroduce the pattern.

Two options were considered:

1. **QuickPerfRules** (`org.quickperf:quick-perf-spring-boot-2-sql`). Declarative assertions like `@ExpectSelect(1)` on test methods. Publishes a Spring Boot 2 starter; no official Boot 4 compatibility statement. Adds a new test dependency and a runtime-agent–style SQL interceptor.
2. **Hibernate `Statistics`** (built into Hibernate ORM). `SessionFactory.getStatistics()` exposes counters including `prepareStatementCount` which increments on every `PreparedStatement` acquisition. Requires `hibernate.generate_statistics=true` at runtime; free otherwise.

## Decision

Use **Hibernate Statistics**. No new test dependency, guaranteed compatibility with the current Hibernate 6 / Spring Boot 4 stack, and the counter semantics map directly to the property we want to assert: "the number of JDBC round-trips must not scale with N."

### Implementation

- Test profile (`src/test/resources/application-test.yml`) sets:
  - `spring.jpa.properties.hibernate.generate_statistics: true`
  - `spring.jpa.properties.hibernate.jdbc.batch_size: 20`
  - `spring.jpa.properties.hibernate.order_inserts: true` and `order_updates: true`

  Statistics are cheap when enabled but off in production. JDBC batching is enabled so that writes in a transaction collapse into a single prepared statement — otherwise a "fixed" service method that does `saveAll(list)` still counts as M `prepareStatement` calls without batching.

- Shared helper `plugwerk-server/plugwerk-server-backend/src/test/kotlin/io/plugwerk/server/StatementCountAssertions.kt` exposes:

  ```kotlin
  inline fun <T> EntityManager.countingStatements(block: () -> T): Pair<T, Long>
  ```

  It unwraps the `SessionFactory`, clears `Statistics`, runs the block, flushes, and returns `(result, prepareStatementCount)`. Tests assert against the count using AssertJ.

- Each N+1 regression test runs the same operation at two sizes (N=3 and N=10, or M=2 and M=all keys) and asserts the counts are equal. That invariant (`count(N1) == count(N2)`) falsifies N+1 regardless of absolute count, which insulates the test from minor Hibernate housekeeping variance between versions.

### Why not strict absolute-count assertions?

`prepareStatementCount` occasionally includes implicit housekeeping statements (dialect introspection on first use, schema validation) that vary by driver and Hibernate version. The invariant that matters is "does it scale with N?" — absolute equality would be noisy without adding signal.

## Consequences

### Good

- Zero new dependencies. Works across H2 (`@DataJpaTest` default) and PostgreSQL (`*IntegrationTest` subclasses).
- The invariant-style assertion (same count at two sizes) is robust to Hibernate-version drift.
- Catches not just classic N+1 but also subtler patterns (double-calling the same batch query, lazy-loading fields returned by a batch query).

### Neutral

- Test profile has `generate_statistics=true` globally; overhead is negligible per Hibernate docs.
- JDBC batching enabled in the test profile matches typical production configuration; no production impact either way.

### Watch

- If Hibernate's `Statistics` semantics change (e.g. a future major version reclassifies what counts as a "prepared statement"), the absolute-count assertions in individual tests may need adjustment. The invariant-style comparisons should remain valid.
- Tests that genuinely need Postgres behavior (e.g. native-array queries) must be tagged `@Tag("integration")` and run in the `integrationTest` task, not the default `test` task.

## References

- Audit rows DB-015..018 — `docs/audits/1.0.0-beta.1-artifacts/triage-DB.csv`
- Issue [#273]
- Helper: `plugwerk-server/plugwerk-server-backend/src/test/kotlin/io/plugwerk/server/StatementCountAssertions.kt`
- Regression tests: `plugwerk-server/plugwerk-server-backend/src/test/kotlin/io/plugwerk/server/repository/NPlusOneRegressionTest.kt`

[#255]: https://github.com/plugwerk/plugwerk/issues/255
[#273]: https://github.com/plugwerk/plugwerk/issues/273
