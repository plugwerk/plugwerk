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
package io.plugwerk.server.service.storage

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.service.ArtifactNotFoundException
import io.plugwerk.server.service.ArtifactStorageException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

@Service
@ConditionalOnProperty(prefix = "plugwerk.storage", name = ["type"], havingValue = "fs", matchIfMissing = true)
class FilesystemArtifactStorageService(properties: PlugwerkProperties) : ArtifactStorageService {

    private val root: Path = Path.of(properties.storage.fs.root)

    override fun store(key: String, content: InputStream, contentLength: Long): String {
        val target = resolveKey(key)
        try {
            Files.createDirectories(target.parent)
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            throw ArtifactStorageException("Failed to store artifact at key '$key'", e)
        }
        return key
    }

    override fun retrieve(key: String): InputStream {
        val path = resolveKey(key)
        if (!path.exists()) throw ArtifactNotFoundException(key)
        return try {
            Files.newInputStream(path)
        } catch (e: Exception) {
            throw ArtifactStorageException("Failed to retrieve artifact at key '$key'", e)
        }
    }

    override fun delete(key: String) {
        val path = resolveKey(key)
        try {
            Files.deleteIfExists(path)
        } catch (e: Exception) {
            throw ArtifactStorageException("Failed to delete artifact at key '$key'", e)
        }
    }

    override fun exists(key: String): Boolean = resolveKey(key).exists()

    override fun listObjects(prefix: String): Sequence<StorageObjectInfo> {
        if (!root.exists()) return emptySequence()
        return try {
            // Eager materialisation: the filesystem artifact directory is bounded
            // (one file per release), so walking it once and closing the stream
            // immediately is correct. Lazy iteration would force callers to close
            // the Files.walk stream — extra contract burden for no real benefit.
            Files.walk(root).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .map { path ->
                        val attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java)
                        StorageObjectInfo(
                            key = root.relativize(path).toString(),
                            lastModified = attrs.lastModifiedTime().toInstant(),
                            sizeBytes = attrs.size(),
                        )
                    }
                    .filter { it.key.startsWith(prefix) }
                    .toList()
                    .asSequence()
            }
        } catch (e: Exception) {
            throw ArtifactStorageException("Failed to list artifacts with prefix '$prefix'", e)
        }
    }

    private fun resolveKey(key: String): Path = root.resolve(key).normalize().also {
        require(it.startsWith(root)) { "Key '$key' resolves outside storage root" }
    }
}
