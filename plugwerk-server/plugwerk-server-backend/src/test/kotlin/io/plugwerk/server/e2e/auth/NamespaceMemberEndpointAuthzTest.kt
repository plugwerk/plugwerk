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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.stream.Stream

class NamespaceMemberEndpointAuthzTest : AbstractAuthorizationTest() {

    // ------------------------------------------------------------------ //
    // GET /namespaces/{ns}/members/me                                     //
    // ------------------------------------------------------------------ //

    @Nested
    inner class GetMyMembership {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceMemberEndpointAuthzTest#getMyMembershipMatrix")
        fun `get my membership authorization`(case: NsActorExpectation) {
            mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/members/me").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // GET /namespaces/{ns}/members                                        //
    // ------------------------------------------------------------------ //

    @Nested
    inner class ListMembers {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceMemberEndpointAuthzTest#listMembersMatrix")
        fun `list members authorization`(case: NsActorExpectation) {
            mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/members").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // POST /namespaces/{ns}/members                                       //
    // ------------------------------------------------------------------ //

    @Nested
    inner class AddMember {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceMemberEndpointAuthzTest#addMemberDeniedMatrix")
        fun `add member denied for unauthorized actors`(case: NsActorExpectation) {
            mockMvc.perform(
                post("/api/v1/namespaces/${case.namespace}/members")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf("userId" to java.util.UUID.randomUUID().toString(), "role" to "READ_ONLY"),
                        ),
                    ),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }

        @Test
        fun `NS1 admin can add member to NS1`() {
            val userId = createEphemeralUser()
            mockMvc.perform(
                post("/api/v1/namespaces/$NS1/members")
                    .actAs(Actor.NS1_ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf("userId" to userId.toString(), "role" to "READ_ONLY"),
                        ),
                    ),
            )
                .andExpect(status().isCreated)
        }
    }

    // ------------------------------------------------------------------ //
    // PUT /namespaces/{ns}/members/{userSubject}                          //
    // ------------------------------------------------------------------ //

    @Nested
    inner class UpdateMember {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceMemberEndpointAuthzTest#updateMemberDeniedMatrix")
        fun `update member denied for unauthorized actors`(case: NsActorExpectation) {
            val targetUserId = userRepository
                .findByUsernameAndSource("ns1-readonly", io.plugwerk.server.domain.UserSource.LOCAL)
                .orElseThrow().id!!
            mockMvc.perform(
                put("/api/v1/namespaces/${case.namespace}/members/$targetUserId")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("role" to "READ_ONLY"))),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }

        @Test
        fun `NS1 admin can update member role`() {
            val targetUserId = userRepository
                .findByUsernameAndSource("ns1-readonly", io.plugwerk.server.domain.UserSource.LOCAL)
                .orElseThrow().id!!
            mockMvc.perform(
                put("/api/v1/namespaces/$NS1/members/$targetUserId")
                    .actAs(Actor.NS1_ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("role" to "READ_ONLY"))),
            )
                .andExpect(status().isOk)
        }
    }

    // ------------------------------------------------------------------ //
    // DELETE /namespaces/{ns}/members/{userSubject}                        //
    // ------------------------------------------------------------------ //

    @Nested
    inner class RemoveMember {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.NamespaceMemberEndpointAuthzTest#removeMemberDeniedMatrix")
        fun `remove member denied for unauthorized actors`(case: NsActorExpectation) {
            val targetUserId = userRepository
                .findByUsernameAndSource("ns1-readonly", io.plugwerk.server.domain.UserSource.LOCAL)
                .orElseThrow().id!!
            mockMvc.perform(
                delete("/api/v1/namespaces/${case.namespace}/members/$targetUserId")
                    .actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }

        @Test
        fun `NS1 admin can remove member`() {
            // Create an ephemeral membership to delete
            val userId = createEphemeralUser()
            createEphemeralMembership(NS1, userId)

            mockMvc.perform(
                delete("/api/v1/namespaces/$NS1/members/$userId")
                    .actAs(Actor.NS1_ADMIN),
            )
                .andExpect(status().isNoContent)
        }
    }

    companion object {

        @JvmStatic
        fun getMyMembershipMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 200),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 200),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 200),
        )

        @JvmStatic
        fun listMembersMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )

        @JvmStatic
        fun addMemberDeniedMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )

        @JvmStatic
        fun updateMemberDeniedMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )

        @JvmStatic
        fun removeMemberDeniedMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )
    }
}
