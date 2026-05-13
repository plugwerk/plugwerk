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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

/**
 * Authorization matrix for the read-only configuration endpoint (#522).
 * Superadmin-only — the response carries operational metadata (storage
 * paths, OIDC settings, …) that namespace admins have no business
 * with, even though no secrets are leaked.
 */
class AdminConfigurationEndpointAuthzTest : AbstractAuthorizationTest() {

    @ParameterizedTest(name = "GET /admin/configuration {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `configuration denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(get("/api/v1/admin/configuration").actAs(case.actor))
            .andExpect(status().`is`(case.expectedStatus))
    }

    @Test
    fun `superadmin can read configuration`() {
        mockMvc.perform(get("/api/v1/admin/configuration").actAs(Actor.SUPERADMIN))
            .andExpect(status().isOk)
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
