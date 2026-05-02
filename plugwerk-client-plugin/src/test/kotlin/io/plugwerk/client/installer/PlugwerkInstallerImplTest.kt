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

import io.plugwerk.client.PlugwerkClient
import io.plugwerk.spi.PlugwerkConfig
import io.plugwerk.spi.model.InstallResult
import io.plugwerk.spi.model.UninstallResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.pf4j.PluginDescriptor
import org.pf4j.PluginManager
import org.pf4j.PluginWrapper
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class PlugwerkInstallerImplTest {
    private lateinit var server: MockWebServer
    private lateinit var installer: PlugwerkInstallerImpl
    private lateinit var pluginManager: PluginManager

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
        // Default: no plugin loaded. Individual tests stub a wrapper when
        // exercising reinstall / no-op paths.
        pluginManager = mock<PluginManager> {
            on { getPlugin(any()) } doReturn null
        }
        installer = PlugwerkInstallerImpl(client, pluginDir, pluginManager)
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

    // -----------------------------------------------------------------------
    // download — now does release-info lookup + SHA-256 verify (#424 point 2)
    // -----------------------------------------------------------------------

    @Test
    fun `download succeeds and returns the verified artifact path`(@TempDir targetDir: Path) {
        val content = "plugin-content".toByteArray()
        val sha256 = sha256Hex(content)
        enqueueReleaseInfo(sha256)
        enqueueArtifact(content)

        val path = installer.download("my-plugin", "1.0.0", targetDir)

        assertEquals(targetDir.resolve("my-plugin-1.0.0.jar"), path)
        assertTrue(Files.exists(path))
        assertNoTempFilesIn(targetDir)
    }

    @Test
    fun `download throws and cleans up temp file when checksum mismatches`(@TempDir targetDir: Path) {
        enqueueReleaseInfo(sha256 = "deadbeef".repeat(8))
        enqueueArtifact("plugin-content".toByteArray())

        val ex = assertThrows(IOException::class.java) {
            installer.download("my-plugin", "1.0.0", targetDir)
        }
        assertTrue(ex.message!!.contains("SHA-256")) { "Expected SHA-256 mention, got: ${ex.message}" }
        assertFalse(Files.exists(targetDir.resolve("my-plugin-1.0.0.jar")))
        assertNoTempFilesIn(targetDir)
    }

    @Test
    fun `download throws when server provides no SHA-256 checksum`(@TempDir targetDir: Path) {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.0.0","status":"published"}""",
                )
                .setResponseCode(200),
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            installer.download("my-plugin", "1.0.0", targetDir)
        }
        assertTrue(ex.message!!.contains("SHA-256"))
        assertNoTempFilesIn(targetDir)
    }

    @Test
    fun `download rejects blank pluginId`(@TempDir targetDir: Path) {
        assertThrows(IllegalArgumentException::class.java) {
            installer.download("", "1.0.0", targetDir)
        }
    }

    @Test
    fun `download rejects blank version`(@TempDir targetDir: Path) {
        assertThrows(IllegalArgumentException::class.java) {
            installer.download("my-plugin", "", targetDir)
        }
    }

    // -----------------------------------------------------------------------
    // install — composes download + PF4J load+start (#424 point 1)
    // -----------------------------------------------------------------------

    @Test
    fun `install loads and starts the plugin via PluginManager`() {
        val content = "plugin-content".toByteArray()
        enqueueReleaseInfo(sha256Hex(content))
        enqueueArtifact(content)
        whenever(pluginManager.loadPlugin(any())).thenReturn("my-plugin")

        val result = installer.install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Success::class.java, result)
        verify(pluginManager).loadPlugin(eq(pluginDir.resolve("my-plugin-1.0.0.jar")))
        verify(pluginManager).startPlugin("my-plugin")
        assertTrue(Files.exists(pluginDir.resolve("my-plugin-1.0.0.jar")))
    }

    @Test
    fun `install is a no-op success when same version is already loaded`() {
        val existing = loadedWrapper("my-plugin", "1.0.0")
        whenever(pluginManager.getPlugin("my-plugin")).thenReturn(existing)

        val result = installer.install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Success::class.java, result)
        verify(pluginManager, never()).loadPlugin(any<Path>())
        verify(pluginManager, never()).startPlugin(any())
        assertEquals(0, server.requestCount) {
            "No HTTP traffic should occur for a no-op same-version install"
        }
    }

    @Test
    fun `install upgrades when a different version is already loaded`() {
        // Pre-stage an old artifact so the eviction sweep has something to clean.
        val oldArtifact = pluginDir.resolve("my-plugin-1.0.0.jar")
        Files.write(oldArtifact, "old".toByteArray())
        val existing = loadedWrapper("my-plugin", "1.0.0")
        whenever(pluginManager.getPlugin("my-plugin")).thenReturn(existing)

        val newContent = "plugin-2-content".toByteArray()
        enqueueReleaseInfo(sha256Hex(newContent))
        enqueueArtifact(newContent)
        whenever(pluginManager.loadPlugin(any())).thenReturn("my-plugin")

        val result = installer.install("my-plugin", "2.0.0")

        assertInstanceOf(InstallResult.Success::class.java, result)
        verify(pluginManager).stopPlugin("my-plugin")
        verify(pluginManager).unloadPlugin("my-plugin")
        verify(pluginManager).loadPlugin(eq(pluginDir.resolve("my-plugin-2.0.0.jar")))
        verify(pluginManager).startPlugin("my-plugin")
        assertFalse(Files.exists(oldArtifact)) { "Old version artifact must be evicted on upgrade" }
        assertTrue(Files.exists(pluginDir.resolve("my-plugin-2.0.0.jar")))
    }

    @Test
    fun `install rolls back the artifact when loadPlugin fails`() {
        val content = "plugin-content".toByteArray()
        enqueueReleaseInfo(sha256Hex(content))
        enqueueArtifact(content)
        whenever(pluginManager.loadPlugin(any())).thenThrow(RuntimeException("PF4J refuses"))

        val result = installer.install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Failure::class.java, result)
        assertTrue((result as InstallResult.Failure).reason.contains("PF4J refuses"))
        assertFalse(Files.exists(pluginDir.resolve("my-plugin-1.0.0.jar"))) {
            "Artifact must be rolled back so the plugin directory does not accumulate stale files"
        }
        assertNoTempFilesIn(pluginDir)
        verify(pluginManager, never()).startPlugin(any())
    }

    @Test
    fun `install rolls back the artifact when startPlugin fails`() {
        val content = "plugin-content".toByteArray()
        enqueueReleaseInfo(sha256Hex(content))
        enqueueArtifact(content)
        whenever(pluginManager.loadPlugin(any())).thenReturn("my-plugin")
        // After load succeeds, simulate the plugin being visible to PF4J (so
        // rollback's defensive unloadPlugin call has something to act on),
        // then make startPlugin throw.
        val rollbackWrapper = loadedWrapper("my-plugin", "1.0.0")
        whenever(pluginManager.getPlugin("my-plugin"))
            .thenReturn(null) // first call: pre-install reinstall check
            .thenReturn(rollbackWrapper) // rollback check
        doThrow(RuntimeException("startup boom")).whenever(pluginManager).startPlugin("my-plugin")

        val result = installer.install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Failure::class.java, result)
        assertFalse(Files.exists(pluginDir.resolve("my-plugin-1.0.0.jar")))
        verify(pluginManager).unloadPlugin("my-plugin")
    }

    @Test
    fun `install returns Failure when plugin not found on server`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = installer.install("unknown-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Failure::class.java, result)
        verify(pluginManager, never()).loadPlugin(any<Path>())
    }

    @Test
    fun `install returns Failure when checksum is missing on server`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.0.0","status":"published"}""",
                )
                .setResponseCode(200),
        )

        val result = installer.install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Failure::class.java, result)
        assertTrue((result as InstallResult.Failure).reason.contains("SHA-256"))
        assertNoTempFilesIn(pluginDir)
        verify(pluginManager, never()).loadPlugin(any<Path>())
    }

    // -----------------------------------------------------------------------
    // uninstall — stops + unloads via PluginManager, then removes file (#424 #1+#3)
    // -----------------------------------------------------------------------

    @Test
    fun `uninstall stops, unloads, and removes the JAR`() {
        val jar = pluginDir.resolve("my-plugin-1.0.0.jar")
        Files.write(jar, "content".toByteArray())
        val loaded = loadedWrapper("my-plugin", "1.0.0")
        whenever(pluginManager.getPlugin("my-plugin")).thenReturn(loaded)

        val result = installer.uninstall("my-plugin")

        assertInstanceOf(UninstallResult.Success::class.java, result)
        assertEquals("my-plugin", (result as UninstallResult.Success).pluginId)
        verify(pluginManager).stopPlugin("my-plugin")
        verify(pluginManager).unloadPlugin("my-plugin")
        assertFalse(Files.exists(jar))
    }

    @Test
    fun `uninstall removes a leftover artifact even when PF4J does not know the plugin`() {
        // Filesystem-only cleanup path — host force-unloaded outside our SPI.
        val jar = pluginDir.resolve("my-plugin-1.0.0.jar")
        Files.write(jar, "content".toByteArray())
        whenever(pluginManager.getPlugin("my-plugin")).thenReturn(null)

        val result = installer.uninstall("my-plugin")

        assertInstanceOf(UninstallResult.Success::class.java, result)
        verify(pluginManager, never()).stopPlugin(any())
        verify(pluginManager, never()).unloadPlugin(any())
        assertFalse(Files.exists(jar))
    }

    @Test
    fun `uninstall returns Failure when no artifact found and plugin not loaded`() {
        whenever(pluginManager.getPlugin("nonexistent-plugin")).thenReturn(null)

        val result = installer.uninstall("nonexistent-plugin")

        assertInstanceOf(UninstallResult.Failure::class.java, result)
    }

    @Test
    fun `uninstall returns Failure when stopPlugin throws`() {
        val jar = pluginDir.resolve("my-plugin-1.0.0.jar")
        Files.write(jar, "content".toByteArray())
        val loaded = loadedWrapper("my-plugin", "1.0.0")
        whenever(pluginManager.getPlugin("my-plugin")).thenReturn(loaded)
        doThrow(RuntimeException("cannot stop")).whenever(pluginManager).stopPlugin("my-plugin")

        val result = installer.uninstall("my-plugin")

        assertInstanceOf(UninstallResult.Failure::class.java, result)
        assertTrue((result as UninstallResult.Failure).reason.contains("cannot stop"))
        // Filesystem must NOT be touched if the unload step blew up — we do not
        // want to delete a JAR PF4J still has a classloader reference to.
        assertTrue(Files.exists(jar))
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private fun enqueueReleaseInfo(sha256: String) {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.0.0","status":"published","artifactSha256":"$sha256"}""",
                )
                .setResponseCode(200),
        )
    }

    private fun enqueueArtifact(content: ByteArray) {
        server.enqueue(
            MockResponse()
                .setBody(String(content))
                .setResponseCode(200),
        )
    }

    private fun assertNoTempFilesIn(dir: Path) {
        val tempCount = Files.list(dir).use { stream ->
            stream.filter { p -> p.fileName.toString().endsWith(".tmp") }.count()
        }
        assertEquals(0L, tempCount) { "Temp files leaked into $dir" }
    }

    private fun loadedWrapper(loadedPluginId: String, loadedVersion: String): PluginWrapper {
        val descriptor = mock<PluginDescriptor>()
        whenever(descriptor.getPluginId()).thenReturn(loadedPluginId)
        whenever(descriptor.getVersion()).thenReturn(loadedVersion)
        val wrapper = mock<PluginWrapper>()
        whenever(wrapper.getPluginId()).thenReturn(loadedPluginId)
        whenever(wrapper.getDescriptor()).thenReturn(descriptor)
        return wrapper
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
