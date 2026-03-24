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

import io.plugwerk.client.PlugwerkClient
import io.plugwerk.client.PlugwerkConfig
import io.plugwerk.spi.model.InstallResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class PlugwerkInstallerImplTest {
    private lateinit var server: MockWebServer
    private lateinit var installer: PlugwerkInstallerImpl

    @TempDir
    lateinit var pluginDir: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client =
            PlugwerkClient(
                PlugwerkConfig(
                    serverUrl = server.url("/").toString().trimEnd('/'),
                    namespace = "acme",
                ),
            )
        installer = PlugwerkInstallerImpl(client, pluginDir)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `verifyChecksum returns true for correct SHA-256`(@TempDir tempDir: Path) {
        val content = "hello plugwerk".toByteArray()
        val file = tempDir.resolve("artifact.jar")
        Files.write(file, content)

        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(content).joinToString("") { "%02x".format(it) }

        assertTrue(installer.verifyChecksum(file, expectedHash))
    }

    @Test
    fun `verifyChecksum returns false for wrong hash`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("artifact.jar")
        Files.write(file, "some bytes".toByteArray())

        assertFalse(installer.verifyChecksum(file, "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun `verifyChecksum is case-insensitive`(@TempDir tempDir: Path) {
        val content = "test".toByteArray()
        val file = tempDir.resolve("artifact.jar")
        Files.write(file, content)

        val digest = MessageDigest.getInstance("SHA-256")
        val lowerHash = digest.digest(content).joinToString("") { "%02x".format(it) }
        val upperHash = lowerHash.uppercase()

        assertTrue(installer.verifyChecksum(file, upperHash))
    }

    @Test
    fun `install succeeds when checksum matches`() {
        val content = "plugin-content".toByteArray()
        val sha256 = sha256Hex(content)

        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.0.0","status":"published","artifactSha256":"$sha256"}""",
                )
                .setResponseCode(200),
        )
        server.enqueue(
            MockResponse()
                .setBody(String(content))
                .setResponseCode(200),
        )

        val result = installer.install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Success::class.java, result)
        val success = result as InstallResult.Success
        assertTrue(Files.exists(pluginDir.resolve("my-plugin-1.0.0.jar")))
        assertTrue(
            Files.list(pluginDir).use {
                it.filter { p -> p.fileName.toString().endsWith(".tmp") }.count()
            } == 0L,
        ) {
            "No temp files should remain after successful install"
        }
    }

    @Test
    fun `install fails and cleans up temp file when checksum mismatches`() {
        val content = "plugin-content".toByteArray()
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.0.0","status":"published","artifactSha256":"deadbeef"}""",
                )
                .setResponseCode(200),
        )
        server.enqueue(
            MockResponse()
                .setBody(String(content))
                .setResponseCode(200),
        )

        val result = installer.install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Failure::class.java, result)
        assertFalse(Files.exists(pluginDir.resolve("my-plugin-1.0.0.jar")))
        assertTrue(
            Files.list(pluginDir).use {
                it.filter { p -> p.fileName.toString().endsWith(".tmp") }.count()
            } == 0L,
        ) {
            "Temp file must be cleaned up on checksum failure"
        }
    }

    @Test
    fun `install returns Failure when plugin not found`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = installer.install("unknown-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Failure::class.java, result)
    }

    @Test
    fun `install returns Failure when server provides no SHA-256 checksum`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.0.0","status":"published"}""",
                )
                .setResponseCode(200),
        )

        val result = installer.install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Failure::class.java, result)
        val failure = result as InstallResult.Failure
        assertTrue(failure.reason.contains("SHA-256")) {
            "Expected SHA-256 mention in failure reason, got: ${failure.reason}"
        }
        assertTrue(
            Files.list(pluginDir).use { it.filter { p -> p.fileName.toString().endsWith(".tmp") }.count() } == 0L,
        ) { "Temp file must be cleaned up when checksum is missing" }
    }

    @Test
    fun `uninstall removes installed JAR`() {
        val jar = pluginDir.resolve("my-plugin-1.0.0.jar")
        Files.write(jar, "content".toByteArray())

        val result = installer.uninstall("my-plugin")

        assertInstanceOf(InstallResult.Success::class.java, result)
        assertFalse(Files.exists(jar))
    }

    @Test
    fun `uninstall returns Failure when no artifact found`() {
        val result = installer.uninstall("nonexistent-plugin")

        assertInstanceOf(InstallResult.Failure::class.java, result)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
