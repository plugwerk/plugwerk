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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.stream.Stream

class OidcProviderEndpointAuthzTest : AbstractAuthorizationTest() {

    @ParameterizedTest(name = "GET /admin/oidc-providers {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `list OIDC providers denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(get("/api/v1/admin/oidc-providers").actAs(case.actor))
            .andExpect(status().`is`(case.expectedStatus))
    }

    @Test
    fun `superadmin can list OIDC providers`() {
        mockMvc.perform(get("/api/v1/admin/oidc-providers").actAs(Actor.SUPERADMIN))
            .andExpect(status().isOk)
    }

    @ParameterizedTest(name = "POST /admin/oidc-providers {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `create OIDC provider denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(
            post("/api/v1/admin/oidc-providers")
                .actAs(case.actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to "denied-${UUID.randomUUID().toString().take(8)}",
                            "providerType" to "GENERIC_OIDC",
                            "clientId" to "test-client-id",
                            "clientSecret" to "test-client-secret",
                            "issuerUri" to "https://example.com",
                        ),
                    ),
                ),
        )
            .andExpect(status().`is`(case.expectedStatus))
    }

    @ParameterizedTest(name = "PATCH /admin/oidc-providers/[id] {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `update OIDC provider denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(
            patch("/api/v1/admin/oidc-providers/${UUID.randomUUID()}")
                .actAs(case.actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("enabled" to false))),
        )
            .andExpect(status().`is`(case.expectedStatus))
    }

    @ParameterizedTest(name = "DELETE /admin/oidc-providers/[id] {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `delete OIDC provider denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(
            delete("/api/v1/admin/oidc-providers/${UUID.randomUUID()}")
                .actAs(case.actor),
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
