/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.client.installer

import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.client.PlugwerkClient
import io.plugwerk.client.PlugwerkNotFoundException
import io.plugwerk.spi.extension.PlugwerkInstaller
import io.plugwerk.spi.model.InstallResult
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Downloads, verifies, and installs plugin artifacts.
 *
 * Install protocol:
 * 1. Download artifact to a temporary file in [pluginDirectory]
 * 2. Verify SHA-256 checksum against the server-provided hash
 * 3. Atomically move the temp file to its final location
 *
 * On any failure, the temporary file is deleted — no partial state is left in [pluginDirectory].
 */
internal class PlugwerkInstallerImpl(private val client: PlugwerkClient, private val pluginDirectory: Path) :
    PlugwerkInstaller {
    private val log = LoggerFactory.getLogger(PlugwerkInstallerImpl::class.java)

    override fun download(pluginId: String, version: String, targetDir: Path): Path {
        Files.createDirectories(targetDir)
        val tempFile = Files.createTempFile(targetDir, "$pluginId-$version-", ".tmp")
        try {
            client.download("plugins/$pluginId/releases/$version/download").use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }
            log.debug("Downloaded artifact for {}:{} to {}", pluginId, version, tempFile)
            return tempFile
        } catch (ex: Exception) {
            deleteSilently(tempFile)
            throw ex
        }
    }

    override fun install(pluginId: String, version: String): InstallResult {
        Files.createDirectories(pluginDirectory)
        val tempFile = Files.createTempFile(pluginDirectory, "$pluginId-$version-", ".tmp")
        return try {
            val releaseInfo =
                client.getOrNull<PluginReleaseDto>("plugins/$pluginId/releases/$version")
                    ?: return InstallResult.Failure(pluginId, version, "Release $pluginId:$version not found on server")

            val expectedSha256 = releaseInfo.artifactSha256
            if (expectedSha256.isNullOrBlank()) {
                deleteSilently(tempFile)
                return InstallResult.Failure(
                    pluginId,
                    version,
                    "Server did not provide a SHA-256 checksum for $pluginId:$version — installation aborted",
                )
            }

            val (suggestedFilename, bodyStream) = client.downloadWithFilename(
                "plugins/$pluginId/releases/$version/download",
            )
            bodyStream.use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }

            if (!verifyChecksum(tempFile, expectedSha256)) {
                deleteSilently(tempFile)
                return InstallResult.Failure(pluginId, version, "SHA-256 checksum mismatch for $pluginId:$version")
            }

            val extension = releaseInfo.fileFormat?.value
                ?: suggestedFilename?.substringAfterLast('.')?.lowercase()
                    ?.takeIf { it == "zip" || it == "jar" }
                ?: "jar"
            val finalPath = pluginDirectory.resolve("$pluginId-$version.$extension")
            Files.move(tempFile, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            log.info("Installed {}:{} to {} ({})", pluginId, version, finalPath, extension)
            InstallResult.Success(pluginId, version)
        } catch (ex: PlugwerkNotFoundException) {
            deleteSilently(tempFile)
            InstallResult.Failure(pluginId, version, "Release $pluginId:$version not found on server")
        } catch (ex: Exception) {
            deleteSilently(tempFile)
            InstallResult.Failure(pluginId, version, ex.message ?: "Unexpected error during install")
        }
    }

    override fun uninstall(pluginId: String): InstallResult {
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

        if (artifactFiles.isEmpty() && extractedDirs.isEmpty()) {
            return InstallResult.Failure(pluginId, "", "No installed artifact found for plugin $pluginId")
        }

        // Remove the ZIP/JAR artifact file
        artifactFiles.forEach { Files.deleteIfExists(it) }

        // Remove the extracted directory that PF4J created when loading the ZIP
        // (DefaultPluginRepository.expandIfZip strips the .zip suffix for the dir name)
        extractedDirs
            .filter { Files.isDirectory(it) }
            .forEach { deleteRecursively(it) }

        log.info("Uninstalled plugin {}", pluginId)
        return InstallResult.Success(pluginId, "")
    }

    private fun deleteRecursively(path: java.nio.file.Path) {
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
