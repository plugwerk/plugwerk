# ADR-0003: Spring Boot 4.x Backend Conventions

## Status

Accepted

## Context

Plugwerk uses Spring Boot 4.x (Spring Framework 7, Hibernate 7, Jakarta EE 11), which introduced significant breaking
changes compared to Spring Boot 3.x. During implementation of Milestone 3 (Database & Domain Layer), we established
conventions for the backend stack covering technology choices, JPA entity design, database migrations, and the test
infrastructure.

### Spring Boot 4.x Breaking Changes

1. **Relocated test annotations**: `@DataJpaTest` moved from `org.springframework.boot.test.autoconfigure.orm.jpa` to
   `org.springframework.boot.data.jpa.test.autoconfigure` and requires the new `spring-boot-starter-data-jpa-test`
   dependency.

2. **Relocated JDBC test annotations**: `@AutoConfigureTestDatabase` moved from
   `org.springframework.boot.test.autoconfigure.jdbc` to `org.springframework.boot.jdbc.test.autoconfigure`.

3. **Testcontainers 2.x artifact rename**: Modules gained a `testcontainers-` prefix (e.g., `junit-jupiter` →
   `testcontainers-junit-jupiter`, `postgresql` → `testcontainers-postgresql`). The BOM coordinates remain
   `org.testcontainers:testcontainers-bom`.

4. **Hibernate 7 DDL generation**: Using `columnDefinition = "jsonb"` or `columnDefinition = "text[]"` in JPA
   `@Column` annotations breaks H2 compatibility. Hibernate 7 can infer the correct DDL type per dialect from
   `@JdbcTypeCode(SqlTypes.JSON)` and `@JdbcTypeCode(SqlTypes.ARRAY)` alone.

5. **JPA entity ID initialization**: Pre-initializing `@GeneratedValue` UUID fields (e.g., `var id: UUID =
   UUID.randomUUID()`) causes Spring Data to call `merge()` instead of `persist()`, resulting in
   `ObjectOptimisticLockingFailureException`. IDs must be nullable (`var id: UUID? = null`) so that `isNew()` correctly
   returns `true` for new entities.

## Decision

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 2.3.x |
| JVM | JDK | 21 |
| Framework | Spring Boot | 4.0.x |
| ORM | Hibernate | 7.x (via Spring Boot) |
| Database | PostgreSQL | 18 |
| Migrations | Liquibase | 5.x |
| Code Style | Spotless + ktlint | intellij_idea style |
| Build | Gradle (Kotlin DSL) | Version catalog (`libs.versions.toml`) |

### Spring Configuration Profiles

| Profile | Purpose | Database | Liquibase |
|---------|---------|----------|-----------|
| `dev` | Local development | PostgreSQL (localhost:5432) | Enabled |
| `test` | H2 unit tests | H2 in-memory (`create-drop`) | Disabled |
| `prod` | Production | PostgreSQL (env vars) | Enabled |

- `spring.jpa.open-in-view=false` globally — no lazy loading outside transactions.
- `spring.jpa.hibernate.ddl-auto=validate` in production/dev — Liquibase owns the schema.
- Jackson: ISO-8601 dates (`write-dates-as-timestamps: false`), lenient deserialization.
- Actuator exposes `health`, `info`, `prometheus` endpoints.

### JPA Entity Conventions

- **UUIDv7 primary keys**: `@UuidGenerator(style = UuidGenerator.Style.TIME)` with `var id: UUID? = null`.
  UUIDv7 (RFC 9562) embeds a millisecond timestamp, providing natural chronological ordering and better B-Tree index
  locality than random UUIDv4. Hibernate's `Style.TIME` generates these natively.
- **No `columnDefinition`** on `@Column` — let Hibernate choose the DDL type per dialect for H2/PostgreSQL
  compatibility.
- **Type mapping via `@JdbcTypeCode`**: `SqlTypes.JSON` for JSON/JSONB columns, `SqlTypes.ARRAY` for array columns.
- **Composite unique constraints** via `@Table(uniqueConstraints = [...])` (required for Hibernate DDL generation).
- **Timestamps**: `@CreationTimestamp` / `@UpdateTimestamp` with `OffsetDateTime` for timezone-aware audit fields.
- **Kotlin JPA plugin** (`kotlin-jpa`): Generates no-arg constructors required by JPA for Kotlin data classes.

### Database Migrations

- Liquibase YAML changelogs under `db/changelog/`.
- Master changelog: `db.changelog-master.yaml` includes versioned migration files.
- Migrations use PostgreSQL-native types (`TIMESTAMPTZ`, `TEXT[]`, `JSONB`) — H2 compatibility is handled via
  Hibernate DDL generation in the test profile, not via migration scripts.

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

### Dependency Additions (Spring Boot 4.x specific)

```kotlin
testImplementation(libs.spring.boot.starter.data.jpa.test)  // provides @DataJpaTest
testRuntimeOnly(libs.h2)                                     // H2 for unit tests
```

## Consequences

- **Easier**: UUIDv7 gives chronologically sortable IDs without additional sequence columns or `ORDER BY created_at`.
- **Easier**: Developers can run repository tests without Docker (`./gradlew test`). CI pipelines can run the fast
  H2 suite even in constrained environments. PostgreSQL-specific behavior is still verified via `integrationTest`.
- **Easier**: Entity mappings are database-agnostic, making future database migrations less risky.
- **Easier**: All dependency versions are centralized in `libs.versions.toml` — no version strings in build scripts.
- **Harder**: Two test suites to maintain. Schema drift between Hibernate DDL (H2) and Liquibase migrations
  (PostgreSQL) is possible — integration tests catch this.
- **Harder**: Spring Boot 4.x package relocations are not well-documented yet. Developers must consult the Spring
  Boot 4.x Javadoc or Maven Central to find moved classes.
- **Harder**: UUIDv7 is Hibernate-specific (`@UuidGenerator`), not portable to other JPA providers.
