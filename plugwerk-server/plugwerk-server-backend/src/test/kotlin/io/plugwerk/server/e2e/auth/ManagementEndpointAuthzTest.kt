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
package io.plugwerk.server.e2e.auth

import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.stream.Stream

/**
 * Tests management endpoint authorization: upload, update plugin/release, delete plugin/release.
 */
class ManagementEndpointAuthzTest : AbstractAuthorizationTest() {

    // ------------------------------------------------------------------ //
    // POST /namespaces/{ns}/plugin-releases — Upload                      //
    // ------------------------------------------------------------------ //

    @Nested
    inner class UploadRelease {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ManagementEndpointAuthzTest#uploadMatrix")
        fun `upload release authorization`(case: NsActorExpectation) {
            val uniqueId = "upload-${UUID.randomUUID().toString().take(8)}"
            val jarBytes = buildMinimalJar(uniqueId, "1.0.0")
            val artifact = MockMultipartFile("artifact", "$uniqueId-1.0.0.jar", "application/java-archive", jarBytes)

            // MockMultipartHttpServletRequestBuilder cannot be cast to MockHttpServletRequestBuilder
            // in Spring Boot 4 / Spring 6 — add auth header directly without casting.
            val builder = multipart("/api/v1/namespaces/${case.namespace}/plugin-releases").file(artifact)
            when {
                case.actor == Actor.ANONYMOUS -> Unit

                case.actor.isApiKey -> {
                    val key = requireNotNull(apiKeyCache[case.actor])
                    builder.header("X-Api-Key", key)
                }

                else -> {
                    val token = requireNotNull(tokenCache[case.actor])
                    builder.header("Authorization", "Bearer $token")
                }
            }
            mockMvc.perform(builder)
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // PATCH /namespaces/{ns}/plugins/{pluginId} — Update metadata         //
    // ------------------------------------------------------------------ //

    @Nested
    inner class UpdatePlugin {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ManagementEndpointAuthzTest#updatePluginMetadataMatrix")
        fun `update plugin metadata authorization`(case: NsActorExpectation) {
            mockMvc.perform(
                patch("/api/v1/namespaces/${case.namespace}/plugins/${case.namespace}-pl1-active")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("name" to "Updated Name"))),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ManagementEndpointAuthzTest#updatePluginStatusMatrix")
        fun `update plugin status requires ADMIN`(case: NsActorExpectation) {
            mockMvc.perform(
                patch("/api/v1/namespaces/${case.namespace}/plugins/${case.namespace}-pl1-active")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("status" to "active"))),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // PATCH /namespaces/{ns}/plugins/{id}/releases/{version} — Status     //
    // ------------------------------------------------------------------ //

    @Nested
    inner class UpdateReleaseStatus {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ManagementEndpointAuthzTest#updateReleaseStatusMatrix")
        fun `update release status authorization`(case: NsActorExpectation) {
            mockMvc.perform(
                patch("/api/v1/namespaces/${case.namespace}/plugins/${case.namespace}-pl1-active/releases/1.1.0")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("status" to "published"))),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // DELETE /namespaces/{ns}/plugins/{id}                                //
    // ------------------------------------------------------------------ //

    @Nested
    inner class DeletePlugin {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ManagementEndpointAuthzTest#deletePluginDeniedMatrix")
        fun `delete plugin denied for unauthorized actors`(case: NsActorExpectation) {
            // Use the existing shared plugin — denied actors can't actually delete it
            mockMvc.perform(
                delete("/api/v1/namespaces/${case.namespace}/plugins/${case.namespace}-pl1-active")
                    .actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ManagementEndpointAuthzTest#deletePluginAllowedMatrix")
        fun `delete plugin allowed for authorized actors`(case: NsActorExpectation) {
            // Create an ephemeral plugin so the shared fixture stays intact
            val (pluginId, _) = createEphemeralPlugin(case.namespace)
            mockMvc.perform(
                delete("/api/v1/namespaces/${case.namespace}/plugins/$pluginId")
                    .actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // DELETE /namespaces/{ns}/plugins/{id}/releases/{version}             //
    // ------------------------------------------------------------------ //

    @Nested
    inner class DeleteRelease {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ManagementEndpointAuthzTest#deleteReleaseDeniedMatrix")
        fun `delete release denied for unauthorized actors`(case: NsActorExpectation) {
            mockMvc.perform(
                delete("/api/v1/namespaces/${case.namespace}/plugins/${case.namespace}-pl1-active/releases/1.3.0")
                    .actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // Method sources                                                      //
    // ------------------------------------------------------------------ //

    companion object {

        // Upload: requires MEMBER in own namespace
        @JvmStatic
        fun uploadMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 201),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 201),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 201),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS2, NS1, 403),
            // NS2
            NsActorExpectation(Actor.SUPERADMIN, NS2, 201),
            NsActorExpectation(Actor.NS2_ADMIN, NS2, 201),
            NsActorExpectation(Actor.NS1_ADMIN, NS2, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS2, 403),
            NsActorExpectation(Actor.API_KEY_NS2, NS2, 403),
        )

        // Update plugin metadata: requires MEMBER
        @JvmStatic
        fun updatePluginMetadataMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 200),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )

        // Update plugin status: requires ADMIN (even if metadata only needs MEMBER)
        @JvmStatic
        fun updatePluginStatusMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )

        // Update release status: requires ADMIN
        @JvmStatic
        fun updateReleaseStatusMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )

        // Delete plugin: denied actors
        @JvmStatic
        fun deletePluginDeniedMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )

        // Delete plugin: allowed actors (use ephemeral plugins)
        @JvmStatic
        fun deletePluginAllowedMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.SUPERADMIN, NS1, 204),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 204),
        )

        // Delete release: denied actors
        @JvmStatic
        fun deleteReleaseDeniedMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )
    }
}
