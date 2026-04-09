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
package io.plugwerk.spi.extension

import io.plugwerk.spi.model.InstallResult
import org.pf4j.ExtensionPoint
import java.nio.file.Path

/**
 * Extension point for downloading, verifying, and installing plugin artifacts.
 *
 * Implement this interface to control how plugin JARs or ZIP bundles are fetched
 * from the Plugwerk server and integrated into a PF4J host application.
 * The default client SDK implementation:
 * 1. Downloads the artifact from the server to a temporary directory.
 * 2. Verifies the SHA-256 checksum against the server-provided value.
 * 3. Moves the verified artifact into the PF4J plugin directory and triggers loading.
 *
 * Typical usage in a host application:
 *
 * Kotlin:
 * ```kotlin
 * val installer = pluginManager.getExtensions(PlugwerkInstaller::class.java).first()
 * when (val result = installer.install("io.example.my-plugin", "2.0.0")) {
 *     is InstallResult.Success -> println("Installed ${result.pluginId} ${result.version}")
 *     is InstallResult.Failure -> println("Failed: ${result.reason}")
 * }
 * ```
 *
 * Java:
 * ```java
 * PlugwerkInstaller installer = pluginManager.getExtensions(PlugwerkInstaller.class).get(0);
 * InstallResult result = installer.install("io.example.my-plugin", "2.0.0");
 * if (result instanceof InstallResult.Success s) {
 *     System.out.println("Installed " + s.getPluginId() + " " + s.getVersion());
 * } else if (result instanceof InstallResult.Failure f) {
 *     System.out.println("Failed: " + f.getReason());
 * }
 * ```
 *
 * @see PlugwerkMarketplace for a unified facade
 */
interface PlugwerkInstaller : ExtensionPoint {
    /**
     * Downloads a plugin artifact to [targetDir] without installing it.
     *
     * The artifact file name is determined by the implementation (typically
     * `<pluginId>-<version>.jar` or `.zip`). The SHA-256 checksum is verified
     * before the path is returned.
     *
     * @param pluginId  the plugin's unique ID within the namespace
     * @param version   the exact SemVer version string (e.g. `"1.2.3"`)
     * @param targetDir directory where the artifact should be saved; must exist and be writable
     * @return path to the downloaded artifact file inside [targetDir]
     * @throws IllegalArgumentException if [pluginId] or [version] are blank
     * @throws java.io.IOException if the download or checksum verification fails
     */
    fun download(pluginId: String, version: String, targetDir: Path): Path

    /**
     * Downloads and installs a plugin into the host application.
     *
     * This is a convenience operation combining [download], [verifyChecksum], and
     * PF4J plugin loading into a single transactional step. On failure the partial
     * download is cleaned up automatically.
     *
     * @param pluginId the plugin's unique ID within the namespace
     * @param version  the exact SemVer version string (e.g. `"1.2.3"`)
     * @return [InstallResult.Success] if the plugin was loaded successfully,
     *         [InstallResult.Failure] with a human-readable reason otherwise
     */
    fun install(pluginId: String, version: String): InstallResult

    /**
     * Unloads and removes a previously installed plugin from the host application.
     *
     * The plugin is stopped and unloaded from the PF4J plugin manager before its
     * artifact file is deleted. If the plugin is not currently installed the
     * implementation returns [InstallResult.Failure] rather than throwing.
     *
     * @param pluginId the plugin's unique ID to remove
     * @return [InstallResult.Success] if the plugin was removed,
     *         [InstallResult.Failure] if it was not installed or removal failed
     */
    fun uninstall(pluginId: String): InstallResult

    /**
     * Verifies the SHA-256 checksum of a local artifact file.
     *
     * @param artifactPath    path to the local file to verify
     * @param expectedSha256  lowercase hex-encoded SHA-256 digest as provided by the server (64 characters)
     * @return `true` if the digest of [artifactPath] matches [expectedSha256], `false` otherwise
     */
    fun verifyChecksum(artifactPath: Path, expectedSha256: String): Boolean
}
