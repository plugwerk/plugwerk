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
import io.plugwerk.spi.model.UninstallResult
import org.pf4j.ExtensionPoint
import java.nio.file.Path

/**
 * Extension point for downloading, verifying, and managing plugin lifecycle.
 *
 * Implement this interface to control how plugin JARs or ZIP bundles are fetched
 * from the Plugwerk server and integrated with a PF4J host application's
 * `PluginManager`. The default client SDK implementation:
 *
 *  - [download] fetches and **verifies** an artifact (SHA-256), but does not
 *    touch PF4J — this is the path for CI / audit / dry-run callers.
 *  - [install] composes [download] with PF4J `loadPlugin` + `startPlugin`, so
 *    after a successful return the plugin is live in the host's `PluginManager`.
 *  - [uninstall] stops + unloads the plugin via `PluginManager` and then
 *    deletes the artifact file from the plugin directory.
 *
 * Typical usage in a host application:
 *
 * Kotlin:
 * ```kotlin
 * val installer = pluginManager.getExtensions(PlugwerkInstaller::class.java).first()
 * installer.install("io.example.my-plugin", "2.0.0")
 *     .onSuccess { println("Installed ${it.pluginId} ${it.version}") }
 *     .onFailure { println("Failed: ${it.reason}") }
 * ```
 *
 * Java:
 * ```java
 * PlugwerkInstaller installer = pluginManager.getExtensions(PlugwerkInstaller.class).get(0);
 * installer.install("io.example.my-plugin", "2.0.0")
 *     .onSuccess(s -> System.out.println("Installed " + s.getPluginId()))
 *     .onFailure(f -> System.out.println("Failed: " + f.getReason()));
 * ```
 *
 * @see PlugwerkMarketplace for a unified facade
 */
interface PlugwerkInstaller : ExtensionPoint {
    /**
     * Downloads and SHA-256-verifies a plugin artifact into [targetDir] without
     * touching the PF4J `PluginManager`. The artifact file name is determined by
     * the implementation (typically `<pluginId>-<version>.jar` or `.zip`).
     *
     * Use this directly for headless audit / dry-run / pre-stage scenarios where
     * you want a verified file on disk but no live plugin in the host. For the
     * full lifecycle, call [install] instead.
     *
     * @param pluginId  the plugin's unique ID within the namespace
     * @param version   the exact SemVer version string (e.g. `"1.2.3"`)
     * @param targetDir directory where the artifact should be saved; must exist or be creatable, and writable
     * @return path to the downloaded, verified artifact file inside [targetDir]
     * @throws IllegalArgumentException if [pluginId] or [version] are blank
     * @throws java.io.IOException if the download or checksum verification fails
     */
    fun download(pluginId: String, version: String, targetDir: Path): Path

    /**
     * Downloads, verifies, and installs a plugin into the host's PF4J
     * `PluginManager` — the plugin is **loaded and started** before this method
     * returns successfully.
     *
     * Reinstall semantics: if a plugin with the same id is already loaded:
     *   - same version → no-op success
     *   - different version → stop + unload the old one, delete its artifact,
     *     then load + start the new one
     *
     * On failure between download and start (e.g. PF4J refuses to load), the
     * partially-installed artifact is rolled back so the plugin directory does
     * not accumulate stale "downloaded but never loaded" files.
     *
     * @param pluginId the plugin's unique ID within the namespace
     * @param version  the exact SemVer version string (e.g. `"1.2.3"`)
     * @return [InstallResult.Success] if the plugin was loaded and started successfully,
     *         [InstallResult.Failure] with a human-readable reason otherwise
     */
    fun install(pluginId: String, version: String): InstallResult

    /**
     * Stops and unloads a previously installed plugin from the host's PF4J
     * `PluginManager`, then deletes its artifact file (and any expanded ZIP
     * directory) from the plugin directory.
     *
     * If the plugin is not currently installed (no artifact on disk and no
     * loaded plugin) the implementation returns [UninstallResult.Failure]
     * rather than throwing.
     *
     * @param pluginId the plugin's unique ID to remove
     * @return [UninstallResult.Success] if the plugin was unloaded and removed,
     *         [UninstallResult.Failure] if it was not installed or removal failed
     */
    fun uninstall(pluginId: String): UninstallResult

    /**
     * Verifies the SHA-256 checksum of a local artifact file.
     *
     * @param artifactPath    path to the local file to verify
     * @param expectedSha256  lowercase hex-encoded SHA-256 digest as provided by the server (64 characters)
     * @return `true` if the digest of [artifactPath] matches [expectedSha256], `false` otherwise
     */
    fun verifyChecksum(artifactPath: Path, expectedSha256: String): Boolean
}
