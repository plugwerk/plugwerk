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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

class FilesystemArtifactStorageServiceTest {

    @TempDir
    lateinit var tmp: Path

    private lateinit var storage: FilesystemArtifactStorageService

    @BeforeEach
    fun setUp() {
        storage = FilesystemArtifactStorageService(propsWithRoot(tmp))
    }

    @Test
    fun `store retrieve roundtrip preserves bytes`() {
        val bytes = "hello-plugwerk".toByteArray()

        storage.store("acme:hello:1.0.0:jar", ByteArrayInputStream(bytes), bytes.size.toLong())

        val read = storage.retrieve("acme:hello:1.0.0:jar").readAllBytes()
        assertThat(read).isEqualTo(bytes)
    }

    @Test
    fun `exists returns true after store and false after delete`() {
        storage.store("acme:plugin-a:1.0.0:jar", ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3)

        assertThat(storage.exists("acme:plugin-a:1.0.0:jar")).isTrue()

        storage.delete("acme:plugin-a:1.0.0:jar")

        assertThat(storage.exists("acme:plugin-a:1.0.0:jar")).isFalse()
    }

    @Test
    fun `delete is idempotent for missing keys`() {
        storage.delete("never:existed:0.0.0:jar")
        // No exception. Matches S3.DeleteObject and the broader contract.
    }

    @Test
    fun `listKeys returns empty sequence when root does not exist`() {
        val freshTmp = tmp.resolve("does-not-exist")
        val s = FilesystemArtifactStorageService(propsWithRoot(freshTmp))

        assertThat(s.listKeys().toList()).isEmpty()
    }

    @Test
    fun `listKeys returns every key with empty prefix`() {
        listOf(
            "acme:plugin-a:1.0.0:jar",
            "acme:plugin-b:2.0.0:jar",
            "other:plugin-c:0.1.0:zip",
        ).forEach { storage.store(it, ByteArrayInputStream(byteArrayOf(0)), 1) }

        assertThat(storage.listKeys().toList())
            .containsExactlyInAnyOrder(
                "acme:plugin-a:1.0.0:jar",
                "acme:plugin-b:2.0.0:jar",
                "other:plugin-c:0.1.0:zip",
            )
    }

    @Test
    fun `listKeys filters by prefix`() {
        listOf(
            "acme:plugin-a:1.0.0:jar",
            "acme:plugin-b:2.0.0:jar",
            "other:plugin-c:0.1.0:zip",
        ).forEach { storage.store(it, ByteArrayInputStream(byteArrayOf(0)), 1) }

        assertThat(storage.listKeys("acme:").toList())
            .containsExactlyInAnyOrder(
                "acme:plugin-a:1.0.0:jar",
                "acme:plugin-b:2.0.0:jar",
            )
    }

    @Test
    fun `listKeys ignores directories and only returns regular files`() {
        // Plant a bare directory inside root — it must not be reported as a key.
        Files.createDirectories(tmp.resolve("subdir/nested"))
        storage.store("acme:plugin:1.0.0:jar", ByteArrayInputStream(byteArrayOf(0)), 1)

        assertThat(storage.listKeys().toList())
            .containsExactly("acme:plugin:1.0.0:jar")
    }

    @Test
    fun `listObjects returns key, lastModified and size for each artifact (#190)`() {
        val bytes = "hello-plugwerk".toByteArray()
        storage.store("acme:plugin-a:1.0.0:jar", ByteArrayInputStream(bytes), bytes.size.toLong())

        val before = java.time.Instant.now().minusSeconds(2)
        val after = java.time.Instant.now().plusSeconds(2)

        val info = storage.listObjects().toList().single()
        assertThat(info.key).isEqualTo("acme:plugin-a:1.0.0:jar")
        assertThat(info.sizeBytes).isEqualTo(bytes.size.toLong())
        assertThat(info.lastModified).isBetween(before, after)
    }

    @Test
    fun `listObjects honours prefix filter`() {
        storage.store("acme:plugin-a:1.0.0:jar", ByteArrayInputStream(byteArrayOf(0)), 1)
        storage.store("other:plugin-b:1.0.0:jar", ByteArrayInputStream(byteArrayOf(0)), 1)

        val acmeOnly = storage.listObjects("acme:").map { it.key }.toList()
        assertThat(acmeOnly).containsExactly("acme:plugin-a:1.0.0:jar")
    }

    private fun propsWithRoot(root: Path): PlugwerkProperties = PlugwerkProperties(
        storage = PlugwerkProperties.StorageProperties(
            type = "fs",
            fs = PlugwerkProperties.StorageProperties.FsProperties(root = root.toString()),
        ),
        auth = PlugwerkProperties.AuthProperties(
            jwtSecret = "test-secret-at-least-32-chars-long!!",
        ),
    )
}
