# ADR-0003: Spring Boot 4.x Test Infrastructure and Dual Database Strategy

## Status

Accepted

## Context

Plugwerk uses Spring Boot 4.x (Spring Framework 7, Hibernate 7, Jakarta EE 11), which introduced significant breaking
changes compared to Spring Boot 3.x. During implementation of Milestone 3 (Database & Domain Layer), we discovered
several incompatibilities:

1. **Relocated test annotations**: `@DataJpaTest` moved from `org.springframework.boot.test.autoconfigure.orm.jpa` to
   `org.springframework.boot.data.jpa.test.autoconfigure` and requires the new `spring-boot-starter-data-jpa-test`
   dependency.

2. **Relocated JDBC test annotations**: `@AutoConfigureTestDatabase` moved from
   `org.springframework.boot.test.autoconfigure.jdbc` to `org.springframework.boot.jdbc.test.autoconfigure`.

3. **Testcontainers 2.x artifact rename**: Modules gained a `testcontainers-` prefix (e.g., `junit-jupiter` →
   `testcontainers-junit-jupiter`, `postgresql` → `testcontainers-postgresql`). The BOM coordinates remain
   `org.testcontainers:testcontainers-bom`.

4. **Docker availability**: Running integration tests with Testcontainers requires a Docker daemon. This blocks
   developer workflows on machines without Docker or in CI environments where Docker is unavailable.

5. **Hibernate 7 DDL generation**: Using `columnDefinition = "jsonb"` or `columnDefinition = "text[]"` in JPA
   `@Column` annotations breaks H2 compatibility. Hibernate 7 can infer the correct DDL type per dialect from
   `@JdbcTypeCode(SqlTypes.JSON)` and `@JdbcTypeCode(SqlTypes.ARRAY)` alone.

6. **JPA entity ID initialization**: Pre-initializing `@GeneratedValue` UUID fields (e.g., `var id: UUID =
   UUID.randomUUID()`) causes Spring Data to call `merge()` instead of `persist()`, resulting in
   `ObjectOptimisticLockingFailureException`. IDs must be nullable (`var id: UUID? = null`) so that `isNew()` correctly
   returns `true` for new entities.

## Decision

### Dual Test Strategy

We use two Gradle test tasks with different database backends:

| Task | Database | Docker required | Purpose |
|------|----------|-----------------|---------|
| `./gradlew test` | H2 (in-memory) | No | Fast feedback, CI default |
| `./gradlew integrationTest` | PostgreSQL 18 via Testcontainers | Yes | Full compatibility verification |

### Test Class Hierarchy

```
AbstractRepositoryTest           @DataJpaTest + @ActiveProfiles("test") → H2
  └── *RepositoryTest (open)     Contains all test methods
        └── *IntegrationTest     @AutoConfigureTestDatabase(replace=NONE) + SharedPostgresContainer
```

- H2 tests use `spring.jpa.hibernate.ddl-auto=create-drop` with Liquibase disabled (test profile).
- Integration tests use Liquibase migrations against real PostgreSQL.
- Test method inheritance eliminates code duplication: integration test classes are empty subclasses.
- JUnit 5 `@Tag("integration")` separates the two suites. The `test` task excludes this tag; `integrationTest`
  includes only this tag.

### JPA Entity Conventions

- No `columnDefinition` on `@Column` — let Hibernate choose the DDL type per dialect.
- `@JdbcTypeCode(SqlTypes.JSON)` for JSON/JSONB columns, `@JdbcTypeCode(SqlTypes.ARRAY)` for array columns.
- `@GeneratedValue(strategy = GenerationType.UUID)` with `var id: UUID? = null` (never pre-initialize).
- Composite unique constraints via `@Table(uniqueConstraints = [...])` (required for Hibernate DDL generation).

### Dependency Additions (Spring Boot 4.x specific)

```kotlin
testImplementation(libs.spring.boot.starter.data.jpa.test)  // provides @DataJpaTest
testRuntimeOnly(libs.h2)                                     // H2 for unit tests
```

## Consequences

- **Easier**: Developers can run repository tests without Docker (`./gradlew test`). CI pipelines can run the fast
  H2 suite even in constrained environments. PostgreSQL-specific behavior is still verified via `integrationTest`.
- **Easier**: Entity mappings are database-agnostic, making future database migrations less risky.
- **Harder**: Two test suites to maintain. Schema drift between Hibernate DDL (H2) and Liquibase migrations
  (PostgreSQL) is possible — integration tests catch this.
- **Harder**: Spring Boot 4.x package relocations are not well-documented yet. Developers must consult the Spring
  Boot 4.x Javadoc or Maven Central to find moved classes.
