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

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

/**
 * Tests update check endpoint. This endpoint is `permitAll()` in SecurityConfig —
 * no authentication or authorization required.
 */
class UpdateCheckEndpointAuthzTest : AbstractAuthorizationTest() {

    @ParameterizedTest(name = "POST /updates/check on {0}")
    @MethodSource("updateCheckMatrix")
    fun `update check authorization`(case: NsActorExpectation) {
        mockMvc.perform(
            post("/api/v1/namespaces/${case.namespace}/updates/check")
                .actAs(case.actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("plugins" to emptyList<Any>()))),
        )
            .andExpect(status().`is`(case.expectedStatus))
    }

    companion object {
        @JvmStatic
        fun updateCheckMatrix(): Stream<NsActorExpectation> = Stream.of(
            // NS1 (public) — permitAll
            NsActorExpectation(Actor.ANONYMOUS, NS1, 200),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 200),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 200),
            // NS2 (private) — still permitAll in SecurityConfig (no auth check in controller)
            NsActorExpectation(Actor.ANONYMOUS, NS2, 200),
            NsActorExpectation(Actor.SUPERADMIN, NS2, 200),
            NsActorExpectation(Actor.NS2_ADMIN, NS2, 200),
            NsActorExpectation(Actor.API_KEY_NS2, NS2, 200),
        )
    }
}
