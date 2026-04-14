/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.plugwerk.spi.model

/**
 * Result of a [PlugwerkInstaller.install] or [PlugwerkInstaller.uninstall] operation.
 *
 * [pluginId] and [version] are available on every result without casting.
 * For detailed handling, use [onSuccess] / [onFailure] callbacks, [fold], or
 * Kotlin `when` expressions.
 *
 * Kotlin:
 * ```kotlin
 * installer.install("io.example.my-plugin", "2.0.0")
 *     .onSuccess { println("Installed ${it.pluginId} ${it.version}") }
 *     .onFailure { println("Failed: ${it.reason}") }
 * ```
 *
 * Java:
 * ```java
 * installer.install("io.example.my-plugin", "2.0.0")
 *     .onSuccess(s -> System.out.println("Installed " + s.getPluginId()))
 *     .onFailure(f -> System.out.println("Failed: " + f.getReason()));
 * ```
 *
 * The `when` / `instanceof` pattern is still fully supported for exhaustive matching.
 */
sealed class InstallResult {

    /** The plugin ID that the operation targeted. */
    abstract val pluginId: String

    /** The SemVer version string that the operation targeted. */
    abstract val version: String

    /**
     * The operation completed successfully.
     *
     * @property pluginId the unique plugin ID that was installed or uninstalled
     * @property version  the SemVer version string that was installed or uninstalled
     */
    data class Success(override val pluginId: String, override val version: String) : InstallResult()

    /**
     * The operation failed.
     *
     * @property pluginId the unique plugin ID for which the operation was attempted
     * @property version  the SemVer version string that was attempted
     * @property reason   human-readable explanation of why the operation failed
     */
    data class Failure(override val pluginId: String, override val version: String, val reason: String) :
        InstallResult()

    /** Returns `true` if this result represents a successful operation. */
    fun isSuccess(): Boolean = this is Success

    /** Returns `true` if this result represents a failed operation. */
    fun isFailure(): Boolean = this is Failure

    /**
     * Executes [action] if this is a [Success], then returns `this` for chaining.
     *
     * ```java
     * result.onSuccess(s -> log.info("Installed: {}", s.getPluginId()));
     * ```
     */
    fun onSuccess(action: (Success) -> Unit): InstallResult {
        if (this is Success) action(this)
        return this
    }

    /**
     * Executes [action] if this is a [Failure], then returns `this` for chaining.
     *
     * ```java
     * result.onFailure(f -> log.warn("Failed: {}", f.getReason()));
     * ```
     */
    fun onFailure(action: (Failure) -> Unit): InstallResult {
        if (this is Failure) action(this)
        return this
    }

    /**
     * Maps this result to a value of type [T] by applying the appropriate function.
     *
     * Both branches must be handled, guaranteeing exhaustive coverage at compile time.
     *
     * ```java
     * String message = result.fold(
     *     s -> "Installed " + s.getPluginId(),
     *     f -> "Failed: " + f.getReason()
     * );
     * ```
     */
    fun <T> fold(onSuccess: (Success) -> T, onFailure: (Failure) -> T): T = when (this) {
        is Success -> onSuccess(this)
        is Failure -> onFailure(this)
    }

    /**
     * Returns the failure [Failure.reason] if this is a [Failure], or `null` if this is a [Success].
     *
     * Convenience accessor for callers that only need the error message.
     */
    fun reasonOrNull(): String? = (this as? Failure)?.reason
}
