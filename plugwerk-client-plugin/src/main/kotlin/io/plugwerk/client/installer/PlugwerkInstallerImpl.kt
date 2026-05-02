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
package io.plugwerk.client.installer

import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.client.PlugwerkClient
import io.plugwerk.client.PlugwerkNotFoundException
import io.plugwerk.spi.extension.PlugwerkInstaller
import io.plugwerk.spi.model.InstallResult
import io.plugwerk.spi.model.UninstallResult
import org.pf4j.PluginManager
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Default [PlugwerkInstaller] implementation.
 *
 *  - [download] downloads + SHA-256-verifies an artifact into a target dir
 *    (smart bits used to live in [install] — issue #424).
 *  - [install] composes [download] with PF4J `loadPlugin` + `startPlugin`.
 *    Reinstall: same version → no-op; different version → upgrade in place.
 *    On any failure between download and start, the artifact is rolled back.
 *  - [uninstall] stops + unloads the plugin via [pluginManager], then deletes
 *    the artifact file (and any expanded ZIP directory) from [pluginDirectory].
 *    Returns [UninstallResult] instead of [InstallResult] (#424).
 *
 * On any download failure, the temporary file is deleted — no partial state is
 * left in [pluginDirectory] or the caller-provided target dir.
 */
internal class PlugwerkInstallerImpl(
    private val client: PlugwerkClient,
    private val pluginDirectory: Path,
    private val pluginManager: PluginManager,
) : PlugwerkInstaller {
    private val log = LoggerFactory.getLogger(PlugwerkInstallerImpl::class.java)

    override fun download(pluginId: String, version: String, targetDir: Path): Path {
        require(pluginId.isNotBlank()) { "pluginId must not be blank" }
        require(version.isNotBlank()) { "version must not be blank" }

        Files.createDirectories(targetDir)
        val tempFile = Files.createTempFile(targetDir, "$pluginId-$version-", ".tmp")
        try {
            val releaseInfo = client.getOrNull<PluginReleaseDto>("plugins/$pluginId/releases/$version")
                ?: throw PlugwerkNotFoundException("Release $pluginId:$version not found on server")

            val expectedSha256 = releaseInfo.artifactSha256
            require(!expectedSha256.isNullOrBlank()) {
                "Server did not provide a SHA-256 checksum for $pluginId:$version — download aborted"
            }

            val (suggestedFilename, bodyStream) = client.downloadWithFilename(
                "plugins/$pluginId/releases/$version/download",
            )
            bodyStream.use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }

            if (!verifyChecksum(tempFile, expectedSha256)) {
                throw IOException("SHA-256 checksum mismatch for $pluginId:$version")
            }

            val extension = releaseInfo.fileFormat?.value
                ?: suggestedFilename?.substringAfterLast('.')?.lowercase()
                    ?.takeIf { it == "zip" || it == "jar" }
                ?: "jar"
            val finalPath = targetDir.resolve("$pluginId-$version.$extension")
            Files.move(tempFile, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            log.debug("Downloaded and verified {}:{} to {}", pluginId, version, finalPath)
            return finalPath
        } catch (ex: Exception) {
            deleteSilently(tempFile)
            throw ex
        }
    }

    override fun install(pluginId: String, version: String): InstallResult {
        // Reinstall short-circuits before we touch the network or filesystem so
        // a no-op install on the same version is genuinely free.
        val existing = pluginManager.getPlugin(pluginId)
        if (existing != null && existing.descriptor.version == version) {
            log.info("Plugin {}:{} is already installed — install is a no-op", pluginId, version)
            return InstallResult.Success(pluginId, version)
        }

        val artifactPath: Path = try {
            download(pluginId, version, pluginDirectory)
        } catch (ex: PlugwerkNotFoundException) {
            return InstallResult.Failure(pluginId, version, "Release $pluginId:$version not found on server")
        } catch (ex: Exception) {
            return InstallResult.Failure(pluginId, version, ex.message ?: "Unexpected error during download")
        }

        // If a different version was already loaded, evict it first so PF4J
        // does not reject the new artifact with a duplicate-id error. Old
        // artifact removal mirrors uninstall's filesystem sweep.
        if (existing != null) {
            try {
                evictExistingVersion(pluginId, existing.descriptor.version)
            } catch (ex: Exception) {
                deleteSilently(artifactPath)
                return InstallResult.Failure(
                    pluginId,
                    version,
                    "Failed to evict previous ${existing.descriptor.version}: ${ex.message ?: "unknown"}",
                )
            }
        }

        return try {
            val loadedId = pluginManager.loadPlugin(artifactPath)
                ?: throw IllegalStateException("loadPlugin returned null for $artifactPath")
            check(loadedId == pluginId) {
                "Loaded plugin id '$loadedId' does not match requested '$pluginId' — refusing"
            }
            pluginManager.startPlugin(pluginId)
            log.info("Installed and started {}:{} from {}", pluginId, version, artifactPath)
            InstallResult.Success(pluginId, version)
        } catch (ex: Exception) {
            // Rollback: PF4J rejected the artifact. Clean up so the plugin
            // directory does not accumulate "downloaded but never loaded" files.
            rollback(pluginId, artifactPath)
            InstallResult.Failure(pluginId, version, ex.message ?: "Unexpected error during PF4J load")
        }
    }

    override fun uninstall(pluginId: String): UninstallResult {
        val loaded = pluginManager.getPlugin(pluginId)
        if (loaded != null) {
            try {
                pluginManager.stopPlugin(pluginId)
                pluginManager.unloadPlugin(pluginId)
            } catch (ex: Exception) {
                return UninstallResult.Failure(
                    pluginId,
                    "Failed to stop/unload plugin: ${ex.message ?: "unknown"}",
                )
            }
        }

        val (artifactFiles, extractedDirs) =
            Files.list(pluginDirectory).use { stream ->
                stream.filter { path ->
                    val name = path.fileName.toString()
                    name.startsWith("$pluginId-")
                }.toList()
            }.partition { path ->
                val name = path.fileName.toString()
                name.endsWith(".jar") || name.endsWith(".zip")
            }

        if (loaded == null && artifactFiles.isEmpty() && extractedDirs.isEmpty()) {
            return UninstallResult.Failure(pluginId, "No installed artifact found for plugin $pluginId")
        }

        artifactFiles.forEach { Files.deleteIfExists(it) }
        extractedDirs
            .filter { Files.isDirectory(it) }
            .forEach { deleteRecursively(it) }

        log.info("Uninstalled plugin {}", pluginId)
        return UninstallResult.Success(pluginId)
    }

    /**
     * Stops + unloads the previous version inside an upgrade flow and clears
     * its artifact file so PF4J does not see a second `pluginId-*.jar` file
     * once the new version is downloaded.
     *
     * Sweeps only files matching `pluginId-{oldVersion}.{jar,zip}` — using a
     * broader `pluginId-*` glob would also delete the just-downloaded new
     * artifact, since this method runs after [download] in the upgrade flow.
     */
    private fun evictExistingVersion(pluginId: String, oldVersion: String) {
        log.info("Upgrading plugin {} from {} to a different version — evicting old", pluginId, oldVersion)
        pluginManager.stopPlugin(pluginId)
        pluginManager.unloadPlugin(pluginId)
        listOf("jar", "zip").forEach { ext ->
            Files.deleteIfExists(pluginDirectory.resolve("$pluginId-$oldVersion.$ext"))
        }
    }

    /**
     * Rolls back a partially-completed install. Deletes the artifact file and,
     * defensively, asks PF4J to unload the plugin in case `loadPlugin` succeeded
     * but `startPlugin` failed.
     */
    private fun rollback(pluginId: String, artifactPath: Path) {
        try {
            if (pluginManager.getPlugin(pluginId) != null) {
                pluginManager.unloadPlugin(pluginId)
            }
        } catch (ex: Exception) {
            log.warn("Rollback: unloadPlugin({}) failed — {}", pluginId, ex.message)
        }
        deleteSilently(artifactPath)
    }

    private fun deleteRecursively(path: Path) {
        if (Files.isDirectory(path)) {
            Files.list(path).use { children ->
                children.forEach { deleteRecursively(it) }
            }
        }
        try {
            Files.deleteIfExists(path)
        } catch (ex: Exception) {
            log.warn("Could not delete {}: {}", path, ex.message)
        }
    }

    override fun verifyChecksum(artifactPath: Path, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(artifactPath).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    private fun deleteSilently(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (ex: Exception) {
            log.warn("Could not delete temporary file {}: {}", path, ex.message)
        }
    }
}
