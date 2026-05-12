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

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.stream.Stream

/**
 * Authorization matrix for the storage-consistency admin endpoints (#190).
 *
 * Anonymous callers must hit 401; every authenticated non-superadmin must hit
 * 403 — namespace admins are NOT allowed because cross-namespace orphan removal
 * could leak data between tenants.
 */
class AdminStorageConsistencyEndpointAuthzTest : AbstractAuthorizationTest() {

    @ParameterizedTest(name = "GET /admin/storage/consistency {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `scan denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(get("/api/v1/admin/storage/consistency").actAs(case.actor))
            .andExpect(status().`is`(case.expectedStatus))
    }

    @Test
    fun `superadmin can scan storage consistency`() {
        mockMvc.perform(get("/api/v1/admin/storage/consistency").actAs(Actor.SUPERADMIN))
            .andExpect(status().isOk)
    }

    @ParameterizedTest(name = "DELETE /admin/storage/consistency/releases/[id] {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `delete release denied for non-superadmins`(case: ActorExpectation) {
        val releaseId = UUID.randomUUID()
        mockMvc.perform(delete("/api/v1/admin/storage/consistency/releases/$releaseId").actAs(case.actor))
            .andExpect(status().`is`(case.expectedStatus))
    }

    @ParameterizedTest(name = "DELETE /admin/storage/consistency/releases {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `delete releases bulk denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(
            delete("/api/v1/admin/storage/consistency/releases")
                .actAs(case.actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("releaseIds" to listOf(UUID.randomUUID().toString())),
                    ),
                ),
        )
            .andExpect(status().`is`(case.expectedStatus))
    }

    @ParameterizedTest(name = "DELETE /admin/storage/consistency/artifacts {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `delete artifacts denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(
            delete("/api/v1/admin/storage/consistency/artifacts")
                .actAs(case.actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("keys" to listOf("ns:p:1.0.0:jar")))),
        )
            .andExpect(status().`is`(case.expectedStatus))
    }

    companion object {
        @JvmStatic
        fun superadminOnlyDeniedMatrix(): Stream<ActorExpectation> = Stream.of(
            ActorExpectation(Actor.ANONYMOUS, 401),
            ActorExpectation(Actor.NS1_ADMIN, 403),
            ActorExpectation(Actor.NS1_READ_ONLY, 403),
            ActorExpectation(Actor.NS2_ADMIN, 403),
            ActorExpectation(Actor.UNRELATED, 403),
            ActorExpectation(Actor.API_KEY_NS1, 403),
        )
    }
}
