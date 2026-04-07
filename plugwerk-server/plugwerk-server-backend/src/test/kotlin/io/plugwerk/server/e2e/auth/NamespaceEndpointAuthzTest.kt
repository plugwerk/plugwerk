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
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.stream.Stream

class NamespaceEndpointAuthzTest : AbstractAuthorizationTest() {

    // ------------------------------------------------------------------ //
    // GET /namespaces                                                     //
    // ------------------------------------------------------------------ //

    @Nested
    inner class ListNamespaces {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceEndpointAuthzTest#listNamespacesMatrix")
        fun `list namespaces authorization`(case: ActorExpectation) {
            mockMvc.perform(get("/api/v1/namespaces").actAs(case.actor))
                .andExpect(status().`is`(case.expectedStatus))
        }

        @Test
        fun `superadmin sees all namespaces`() {
            val result = mockMvc.perform(get("/api/v1/namespaces").actAs(Actor.SUPERADMIN))
                .andExpect(status().isOk)
                .andReturn()

            val body = objectMapper.readValue(result.response.contentAsString, List::class.java)

            @Suppress("UNCHECKED_CAST")
            val slugs = (body as List<Map<String, Any>>).map { it["slug"] }
            assert(slugs.contains(NS1) && slugs.contains(NS2)) {
                "Superadmin should see both ns1 and ns2, got $slugs"
            }
        }

        @Test
        fun `NS1 admin only sees NS1`() {
            val result = mockMvc.perform(get("/api/v1/namespaces").actAs(Actor.NS1_ADMIN))
                .andExpect(status().isOk)
                .andReturn()

            val body = objectMapper.readValue(result.response.contentAsString, List::class.java)

            @Suppress("UNCHECKED_CAST")
            val slugs = (body as List<Map<String, Any>>).map { it["slug"] }
            assert(slugs.contains(NS1) && !slugs.contains(NS2)) {
                "NS1 admin should see ns1 but not ns2, got $slugs"
            }
        }

        @Test
        fun `unrelated user sees empty list`() {
            val result = mockMvc.perform(get("/api/v1/namespaces").actAs(Actor.UNRELATED))
                .andExpect(status().isOk)
                .andReturn()

            val body = objectMapper.readValue(result.response.contentAsString, List::class.java)
            assert(body.isEmpty()) { "Unrelated user should see no namespaces, got $body" }
        }
    }

    // ------------------------------------------------------------------ //
    // POST /namespaces                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    inner class CreateNamespace {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceEndpointAuthzTest#createNamespaceDeniedMatrix")
        fun `create namespace denied for non-superadmins`(case: ActorExpectation) {
            mockMvc.perform(
                post("/api/v1/namespaces")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf("slug" to "denied-${UUID.randomUUID().toString().take(8)}", "name" to "Denied"),
                        ),
                    ),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }

        @Test
        fun `superadmin can create namespace`() {
            val slug = "ephemeral-${UUID.randomUUID().toString().take(8)}"
            mockMvc.perform(
                post("/api/v1/namespaces")
                    .actAs(Actor.SUPERADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("slug" to slug, "name" to "Ephemeral NS"))),
            )
                .andExpect(status().isCreated)
        }
    }

    // ------------------------------------------------------------------ //
    // PATCH /namespaces/{ns}                                              //
    // ------------------------------------------------------------------ //

    @Nested
    inner class UpdateNamespace {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceEndpointAuthzTest#updateNamespaceMatrix")
        fun `update namespace authorization`(case: NsActorExpectation) {
            mockMvc.perform(
                patch("/api/v1/namespaces/${case.namespace}")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("name" to "Updated Name"))),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // DELETE /namespaces/{ns}                                             //
    // ------------------------------------------------------------------ //

    @Nested
    inner class DeleteNamespace {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceEndpointAuthzTest#deleteNamespaceDeniedMatrix")
        fun `delete namespace denied for non-superadmins`(case: ActorExpectation) {
            mockMvc.perform(delete("/api/v1/namespaces/$NS1").actAs(case.actor))
                .andExpect(status().`is`(case.expectedStatus))
        }

        @Test
        fun `superadmin can delete namespace`() {
            val slug = createEphemeralNamespace()
            mockMvc.perform(delete("/api/v1/namespaces/$slug").actAs(Actor.SUPERADMIN))
                .andExpect(status().isNoContent)
        }
    }

    companion object {

        @JvmStatic
        fun listNamespacesMatrix(): Stream<ActorExpectation> = Stream.of(
            ActorExpectation(Actor.ANONYMOUS, 401),
            ActorExpectation(Actor.SUPERADMIN, 200),
            ActorExpectation(Actor.NS1_ADMIN, 200),
            ActorExpectation(Actor.NS1_READ_ONLY, 200),
            ActorExpectation(Actor.UNRELATED, 200),
            ActorExpectation(Actor.API_KEY_NS1, 200),
        )

        @JvmStatic
        fun createNamespaceDeniedMatrix(): Stream<ActorExpectation> = Stream.of(
            ActorExpectation(Actor.ANONYMOUS, 401),
            ActorExpectation(Actor.NS1_ADMIN, 403),
            ActorExpectation(Actor.NS1_READ_ONLY, 403),
            ActorExpectation(Actor.UNRELATED, 403),
            ActorExpectation(Actor.API_KEY_NS1, 403),
        )

        @JvmStatic
        fun updateNamespaceMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
            // NS2
            NsActorExpectation(Actor.SUPERADMIN, NS2, 200),
            NsActorExpectation(Actor.NS2_ADMIN, NS2, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS2, 403),
        )

        @JvmStatic
        fun deleteNamespaceDeniedMatrix(): Stream<ActorExpectation> = Stream.of(
            ActorExpectation(Actor.ANONYMOUS, 401),
            ActorExpectation(Actor.NS1_ADMIN, 403),
            ActorExpectation(Actor.NS2_ADMIN, 403),
            ActorExpectation(Actor.UNRELATED, 403),
            ActorExpectation(Actor.API_KEY_NS1, 403),
        )
    }
}
