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
 * Use a `when` expression to handle both outcomes:
 *
 * Kotlin:
 * ```kotlin
 * when (val result = installer.install("io.example.my-plugin", "2.0.0")) {
 *     is InstallResult.Success -> println("Installed ${result.pluginId} ${result.version}")
 *     is InstallResult.Failure -> println("Failed: ${result.reason}")
 * }
 * ```
 *
 * Java:
 * ```java
 * InstallResult result = installer.install("io.example.my-plugin", "2.0.0");
 * if (result instanceof InstallResult.Success s) {
 *     System.out.println("Installed " + s.getPluginId() + " " + s.getVersion());
 * } else if (result instanceof InstallResult.Failure f) {
 *     System.out.println("Failed: " + f.getReason());
 * }
 * ```
 */
sealed class InstallResult {
    /**
     * The operation completed successfully.
     *
     * @property pluginId the unique plugin ID that was installed or uninstalled
     * @property version  the SemVer version string that was installed or uninstalled
     */
    data class Success(val pluginId: String, val version: String) : InstallResult()

    /**
     * The operation failed.
     *
     * @property pluginId the unique plugin ID for which the operation was attempted
     * @property version  the SemVer version string that was attempted
     * @property reason   human-readable explanation of why the operation failed
     */
    data class Failure(val pluginId: String, val version: String, val reason: String) : InstallResult()
}
