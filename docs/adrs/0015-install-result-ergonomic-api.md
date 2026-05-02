# ADR-0015: Ergonomic InstallResult API — Convenience Methods over Pattern Matching

## Status

Accepted

## Context

`InstallResult` is a sealed class in `plugwerk-spi` with two subtypes (`Success` and `Failure`). Consuming the result requires pattern matching — `when` in Kotlin and `instanceof` in Java:

```java
// Java — verbose and error-prone
InstallResult result = installer.install("com.acme.crm-connector", "3.0.0");
if (result instanceof InstallResult.Success s) {
    log.info("Installed: {}", s.getPluginId());
} else if (result instanceof InstallResult.Failure f) {
    log.warn("Failed: {}", f.getReason());
}
```

This is acceptable in Kotlin (exhaustive `when` is idiomatic), but Java callers find it verbose. More importantly, `pluginId` and `version` are only accessible after a downcast, even though both subtypes carry them.

Four alternatives were evaluated:

### Option A: Callback-style (`onSuccess` / `onFailure`)

Fluent chaining methods that execute a lambda only for the matching subtype. Inspired by Kotlin's `Result<T>` API.

- **Pro:** Clean Java syntax, chainable, self-documenting
- **Pro:** No type erasure issues — lambdas receive the concrete subtype
- **Con:** Adds methods to the sealed class (minor API surface increase)

### Option B: `fold` (functional)

A single method that maps both outcomes to a common return type, forcing the caller to handle both cases.

- **Pro:** Exhaustive handling guaranteed at compile time
- **Pro:** Clean for transformations (e.g., mapping to an HTTP response)
- **Con:** Slightly less discoverable for Java developers unfamiliar with functional patterns

### Option C: Simple boolean + getters (pragmatic)

Replace the sealed class with a single data class carrying `success: Boolean` and nullable `reason`.

- **Pro:** Simplest possible API
- **Con:** Loses compile-time exhaustiveness — `reason` is nullable even on failure
- **Con:** Breaking change — all existing callers must be rewritten
- **Con:** Not idiomatic Kotlin (sealed types are the standard pattern)

### Option D: Exception-based (throw on failure)

Change `install()` to throw on failure instead of returning a result.

- **Pro:** Java developers are familiar with try-catch
- **Con:** Exceptions for expected outcomes (install failure) violate the principle of least surprise
- **Con:** Breaking change — all callers must be rewritten
- **Con:** The current contract explicitly documents that `install()` never throws

## Decision

**Combine Options A and B** — add convenience methods (`onSuccess`, `onFailure`, `fold`, `isSuccess`, `isFailure`, `reasonOrNull`) to the existing sealed class. Additionally, promote `pluginId` and `version` to abstract properties on the base class so they are accessible without casting.

This approach is **purely additive**: existing `when` expressions and `instanceof` checks continue to compile and work. Java callers gain an ergonomic alternative:

```java
// Java — after (clean, chainable)
installer.install("com.acme.crm-connector", "3.0.0")
    .onSuccess(s -> log.info("Installed: {}", s.getPluginId()))
    .onFailure(f -> log.warn("Failed: {}", f.getReason()));

// Java — fold for transformations
String message = result.fold(
    s -> "Installed " + s.getPluginId(),
    f -> "Failed: " + f.getReason()
);
```

Kotlin callers can continue using `when` or adopt the new methods where convenient:

```kotlin
// Kotlin — existing pattern still works
when (result) {
    is InstallResult.Success -> log.info("Installed: ${result.pluginId}")
    is InstallResult.Failure -> log.warn("Failed: ${result.reason}")
}

// Kotlin — new option
result
    .onSuccess { log.info("Installed: ${it.pluginId}") }
    .onFailure { log.warn("Failed: ${it.reason}") }
```

Options C and D were rejected because they introduce breaking changes, lose type safety, or contradict established API contracts.

## Consequences

- **Java ergonomics improved** — no `instanceof` needed for common use cases
- **Backward compatible** — zero changes required for existing callers
- **`pluginId` and `version` accessible on base type** — eliminates the most common reason for downcasting
- **API surface grows** — six new methods on `InstallResult`, but all are small and well-understood
- **Follows Kotlin `Result<T>` conventions** — developers familiar with the stdlib will find the API intuitive

## Related — `UninstallResult` (2026-05-02, #424)

`PlugwerkInstaller.uninstall()` originally returned `InstallResult` too, but
uninstall does not know the version of what it removed — only the `pluginId`.
That forced an empty-string `version` on every result, which leaked through
the type. Issue #424 introduced `UninstallResult` as a parallel sealed class
mirroring `InstallResult`'s shape (`Success` / `Failure` + `onSuccess` /
`onFailure` / `fold` / `isSuccess` / `isFailure` / `reasonOrNull`) but
carrying only `pluginId`. The decision rationale (callbacks > `fold` > simple
boolean > exceptions) from this ADR applied unchanged.
