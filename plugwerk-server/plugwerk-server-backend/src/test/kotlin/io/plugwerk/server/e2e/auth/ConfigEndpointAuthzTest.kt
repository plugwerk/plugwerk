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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

class ConfigEndpointAuthzTest : AbstractAuthorizationTest() {

    @ParameterizedTest(name = "GET /config as {0}")
    @MethodSource("allActors")
    fun `config endpoint is public for all actors`(expectation: ActorExpectation) {
        mockMvc.perform(get("/api/v1/config").actAs(expectation.actor))
            .andExpect(status().isOk)
    }

    companion object {
        @JvmStatic
        fun allActors(): Stream<ActorExpectation> = Stream.of(
            ActorExpectation(Actor.ANONYMOUS, 200),
            ActorExpectation(Actor.SUPERADMIN, 200),
            ActorExpectation(Actor.NS1_ADMIN, 200),
            ActorExpectation(Actor.NS1_READ_ONLY, 200),
            ActorExpectation(Actor.UNRELATED, 200),
            ActorExpectation(Actor.API_KEY_NS1, 200),
        )
    }
}
