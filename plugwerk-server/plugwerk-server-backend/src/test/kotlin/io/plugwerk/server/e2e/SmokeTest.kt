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
package io.plugwerk.server.e2e

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.test.assertNotNull

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmokeTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun overrideDataSource(registry: DynamicPropertyRegistry) {
            val postgres = io.plugwerk.server.SharedPostgresContainer.instance
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var token: String
    private val namespace = "smoke-test"
    private val pluginId = "smoke-plugin"
    private val pluginVersion = "1.0.0"

    @BeforeAll
    fun login() {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("username" to "admin", "password" to "smoke-test-password"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken").exists()
        }.andReturn()

        val body = objectMapper.readValue(result.response.contentAsString, Map::class.java)
        token = body["accessToken"] as String
        assertNotNull(token)
    }

    @Test
    fun `full upload-catalog-download flow`() {
        val authHeader = "Bearer $token"

        // ------------------------------------------------------------------ //
        // 1. Create namespace                                                  //
        // ------------------------------------------------------------------ //
        mockMvc.post("/api/v1/namespaces") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("slug" to namespace, "name" to "Smoke Test"))
        }.andExpect {
            status { isCreated() }
        }

        // ------------------------------------------------------------------ //
        // 2. Build a minimal plugin JAR in-memory                             //
        // ------------------------------------------------------------------ //
        val jarBytes = buildMinimalJar(pluginId, pluginVersion)
        val expectedSha256 = sha256(jarBytes)

        // ------------------------------------------------------------------ //
        // 3. Upload plugin release                                             //
        // ------------------------------------------------------------------ //
        val artifact = MockMultipartFile(
            "artifact",
            "$pluginId-$pluginVersion.jar",
            "application/java-archive",
            jarBytes,
        )
        val uploadResult = mockMvc.multipart("/api/v1/namespaces/$namespace/plugin-releases") {
            file(artifact)
            header("Authorization", authHeader)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id").exists()
        }.andReturn()

        val uploadBody = objectMapper.readValue(uploadResult.response.contentAsString, Map::class.java)
        assertNotNull(uploadBody["id"], "Upload response must contain release ID")

        // ------------------------------------------------------------------ //
        // 4. Query catalog — plugin must appear                                //
        // ------------------------------------------------------------------ //
        mockMvc.get("/api/v1/namespaces/$namespace/plugins") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[?(@.pluginId == '$pluginId')]").exists()
        }

        // ------------------------------------------------------------------ //
        // 5. Download artifact — SHA-256 must match                           //
        // ------------------------------------------------------------------ //
        val downloadResult = mockMvc.get(
            "/api/v1/namespaces/$namespace/plugins/$pluginId/releases/$pluginVersion/download",
        ) {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val downloadedBytes = downloadResult.response.contentAsByteArray
        val actualSha256 = sha256(downloadedBytes)
        kotlin.test.assertEquals(expectedSha256, actualSha256, "SHA-256 of downloaded artifact must match uploaded JAR")
    }

    @Test
    fun `review approval and rejection flow`() {
        val authHeader = "Bearer $token"
        val reviewNs = "smoke-review"

        // ------------------------------------------------------------------ //
        // 1. Create a namespace that requires manual review                    //
        // ------------------------------------------------------------------ //
        mockMvc.post("/api/v1/namespaces") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("slug" to reviewNs, "name" to "Smoke Review", "autoApproveReleases" to false),
            )
        }.andExpect {
            status { isCreated() }
        }

        // ------------------------------------------------------------------ //
        // 2. Upload a release — with auto-approve off it lands in `draft`      //
        //    and must surface in the pending review queue                      //
        // ------------------------------------------------------------------ //
        val approveId = "review-approve-plugin"
        val approveReleaseId = uploadRelease(reviewNs, approveId, "1.0.0", authHeader)

        mockMvc.get("/api/v1/namespaces/$reviewNs/reviews/pending") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.releaseId == '$approveReleaseId')]").exists()
            jsonPath("$[?(@.pluginId == '$approveId')]").exists()
        }

        // ------------------------------------------------------------------ //
        // 3. Approve — the release transitions to `published`                  //
        // ------------------------------------------------------------------ //
        mockMvc.post("/api/v1/namespaces/$reviewNs/reviews/$approveReleaseId/approve") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("comment" to "Reviewed and approved."))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("published") }
        }

        // …and it is no longer pending
        mockMvc.get("/api/v1/namespaces/$reviewNs/reviews/pending") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.releaseId == '$approveReleaseId')]").doesNotExist()
        }

        // ------------------------------------------------------------------ //
        // 4. Reject path — a second release is yanked out of the queue         //
        // ------------------------------------------------------------------ //
        val rejectId = "review-reject-plugin"
        val rejectReleaseId = uploadRelease(reviewNs, rejectId, "1.0.0", authHeader)

        mockMvc.post("/api/v1/namespaces/$reviewNs/reviews/$rejectReleaseId/reject") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("comment" to "Rejected during smoke test."))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("yanked") }
        }

        mockMvc.get("/api/v1/namespaces/$reviewNs/reviews/pending") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.releaseId == '$rejectReleaseId')]").doesNotExist()
        }
    }

    @Test
    fun `update-check reports a newer published version`() {
        val authHeader = "Bearer $token"
        val updateNs = "smoke-updates"
        val updatePlugin = "update-plugin"

        // Auto-approve on → both uploads are immediately `published`, which is
        // the only status the update checker considers.
        mockMvc.post("/api/v1/namespaces") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("slug" to updateNs, "name" to "Smoke Updates", "autoApproveReleases" to true),
            )
        }.andExpect {
            status { isCreated() }
        }

        uploadRelease(updateNs, updatePlugin, "1.0.0", authHeader)
        uploadRelease(updateNs, updatePlugin, "2.0.0", authHeader)

        // A client on 1.0.0 must be told 2.0.0 is available…
        mockMvc.post("/api/v1/namespaces/$updateNs/updates/check") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("plugins" to listOf(mapOf("pluginId" to updatePlugin, "currentVersion" to "1.0.0"))),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.updates[?(@.pluginId == '$updatePlugin')].latestVersion") { value("2.0.0") }
            jsonPath("$.updates[?(@.pluginId == '$updatePlugin')].currentVersion") { value("1.0.0") }
        }

        // …and a client already on 2.0.0 must be told nothing is pending.
        mockMvc.post("/api/v1/namespaces/$updateNs/updates/check") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("plugins" to listOf(mapOf("pluginId" to updatePlugin, "currentVersion" to "2.0.0"))),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.updates[?(@.pluginId == '$updatePlugin')]").doesNotExist()
        }
    }

    // ----------------------------------------------------------------------- //
    // Helpers                                                                   //
    // ----------------------------------------------------------------------- //

    /** Uploads a minimal plugin JAR to [ns] and returns the created release id. */
    private fun uploadRelease(ns: String, pluginId: String, version: String, authHeader: String): String {
        val artifact = MockMultipartFile(
            "artifact",
            "$pluginId-$version.jar",
            "application/java-archive",
            buildMinimalJar(pluginId, version),
        )
        val result = mockMvc.multipart("/api/v1/namespaces/$ns/plugin-releases") {
            file(artifact)
            header("Authorization", authHeader)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id").exists()
        }.andReturn()
        val body = objectMapper.readValue(result.response.contentAsString, Map::class.java)
        return body["id"] as String
    }

    private fun buildMinimalJar(id: String, version: String): ByteArray {
        val manifest = java.util.jar.Manifest().apply {
            mainAttributes[java.util.jar.Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", id)
            mainAttributes.putValue("Plugin-Version", version)
            mainAttributes.putValue("Plugin-Name", "Smoke Test Plugin")
            mainAttributes.putValue("Plugin-Description", "Minimal plugin for E2E smoke testing")
        }
        val out = ByteArrayOutputStream()
        java.util.jar.JarOutputStream(out, manifest).use { jar ->
            jar.putNextEntry(java.util.jar.JarEntry("dummy.txt"))
            jar.write("smoke-test".toByteArray())
            jar.closeEntry()
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
