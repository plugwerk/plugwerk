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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.stream.Stream

class ReviewsEndpointAuthzTest : AbstractAuthorizationTest() {

    // ------------------------------------------------------------------ //
    // GET /namespaces/{ns}/reviews/pending                                //
    // ------------------------------------------------------------------ //

    @Nested
    inner class ListPendingReviews {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ReviewsEndpointAuthzTest#listPendingMatrix")
        fun `list pending reviews authorization`(case: NsActorExpectation) {
            mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/reviews/pending").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // POST /namespaces/{ns}/reviews/{releaseId}/approve                   //
    // POST /namespaces/{ns}/reviews/{releaseId}/reject                    //
    // ------------------------------------------------------------------ //

    @Nested
    inner class ApproveRejectReview {

        @ParameterizedTest(name = "approve as {0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ReviewsEndpointAuthzTest#approveRejectDeniedMatrix")
        fun `approve review denied for unauthorized actors`(case: NsActorExpectation) {
            // Use a random UUID — the auth check happens before the release lookup
            val fakeReleaseId = UUID.randomUUID()
            mockMvc.perform(
                post("/api/v1/namespaces/${case.namespace}/reviews/$fakeReleaseId/approve")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }

        @ParameterizedTest(name = "reject as {0}")
        @MethodSource("io.plugwerk.server.e2e.auth.ReviewsEndpointAuthzTest#approveRejectDeniedMatrix")
        fun `reject review denied for unauthorized actors`(case: NsActorExpectation) {
            val fakeReleaseId = UUID.randomUUID()
            mockMvc.perform(
                post("/api/v1/namespaces/${case.namespace}/reviews/$fakeReleaseId/reject")
                    .actAs(case.actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    companion object {

        // listPendingReviews requires MEMBER
        @JvmStatic
        fun listPendingMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 200),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
            // NS2
            NsActorExpectation(Actor.SUPERADMIN, NS2, 200),
            NsActorExpectation(Actor.NS2_ADMIN, NS2, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS2, 403),
            NsActorExpectation(Actor.API_KEY_NS2, NS2, 403),
        )

        // approve/reject requires ADMIN
        @JvmStatic
        fun approveRejectDeniedMatrix(): Stream<NsActorExpectation> = Stream.of(
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 403),
            NsActorExpectation(Actor.NS1_MEMBER_NS2_RO, NS1, 403),
            NsActorExpectation(Actor.NS2_ADMIN, NS1, 403),
            NsActorExpectation(Actor.UNRELATED, NS1, 403),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 403),
        )
    }
}
