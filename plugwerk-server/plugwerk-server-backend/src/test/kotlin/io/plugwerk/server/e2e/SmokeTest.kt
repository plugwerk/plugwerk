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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertNotNull

@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmokeTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun overrideDataSource(registry: DynamicPropertyRegistry) {
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
        val result = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("username" to "smoke", "password" to "smoke"))
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
            content = objectMapper.writeValueAsString(mapOf("slug" to namespace, "ownerOrg" to "Smoke Test"))
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
        val uploadResult = mockMvc.multipart("/api/v1/namespaces/$namespace/releases") {
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

    // ----------------------------------------------------------------------- //
    // Helpers                                                                   //
    // ----------------------------------------------------------------------- //

    private fun buildMinimalJar(id: String, version: String): ByteArray {
        val descriptor = """
            plugwerk:
              id: $id
              version: $version
              name: Smoke Test Plugin
              description: Minimal plugin for E2E smoke testing
        """.trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("plugwerk.yml"))
            zip.write(descriptor.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
