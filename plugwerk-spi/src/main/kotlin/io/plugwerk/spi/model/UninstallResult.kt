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

import java.util.function.Consumer

/**
 * Result of a [io.plugwerk.spi.extension.PlugwerkInstaller.uninstall] operation.
 *
 * Mirrors [InstallResult] in shape and Java-friendly callback API but carries
 * only the [pluginId] — uninstall does not know (and does not need) the
 * version that was installed (issue #424).
 *
 * Kotlin:
 * ```kotlin
 * installer.uninstall("io.example.my-plugin")
 *     .onSuccess { println("Removed ${it.pluginId}") }
 *     .onFailure { println("Failed: ${it.reason}") }
 * ```
 *
 * Java:
 * ```java
 * installer.uninstall("io.example.my-plugin")
 *     .onSuccess(s -> System.out.printf("Removed: %s%n", s.getPluginId()))
 *     .onFailure(f -> System.out.printf("Failed: %s%n", f.getReason()));
 * ```
 *
 * The `when` / `instanceof` pattern is fully supported for exhaustive matching.
 */
sealed class UninstallResult {

    /** The plugin ID that the operation targeted. */
    abstract val pluginId: String

    /**
     * The operation completed successfully.
     *
     * @property pluginId the unique plugin ID that was uninstalled
     */
    data class Success(override val pluginId: String) : UninstallResult()

    /**
     * The operation failed.
     *
     * @property pluginId the unique plugin ID for which the operation was attempted
     * @property reason   human-readable explanation of why the operation failed
     */
    data class Failure(override val pluginId: String, val reason: String) : UninstallResult()

    /** Returns `true` if this result represents a successful operation. */
    fun isSuccess(): Boolean = this is Success

    /** Returns `true` if this result represents a failed operation. */
    fun isFailure(): Boolean = this is Failure

    /**
     * Executes [action] if this is a [Success], then returns `this` for chaining.
     *
     * Uses [Consumer] so Java callers can pass expression lambdas directly.
     */
    fun onSuccess(action: Consumer<Success>): UninstallResult {
        if (this is Success) action.accept(this)
        return this
    }

    /**
     * Executes [action] if this is a [Failure], then returns `this` for chaining.
     *
     * Uses [Consumer] so Java callers can pass expression lambdas directly.
     */
    fun onFailure(action: Consumer<Failure>): UninstallResult {
        if (this is Failure) action.accept(this)
        return this
    }

    /**
     * Maps this result to a value of type [T] by applying the appropriate function.
     *
     * Both branches must be handled, guaranteeing exhaustive coverage at compile time.
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
