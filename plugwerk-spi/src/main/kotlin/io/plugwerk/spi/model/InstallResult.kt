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
