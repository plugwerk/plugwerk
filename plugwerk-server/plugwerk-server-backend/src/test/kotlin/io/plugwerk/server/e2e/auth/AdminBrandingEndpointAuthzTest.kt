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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

/**
 * Branding authorization matrix (#254). Upload and reset are
 * superadmin-only. The public GET is anonymous-accessible by design —
 * the login page references it before any session exists.
 */
class AdminBrandingEndpointAuthzTest : AbstractAuthorizationTest() {

    @ParameterizedTest(name = "POST /admin/branding/[slot] {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `upload denied for non-superadmins`(case: ActorExpectation) {
        val file = MockMultipartFile(
            "file",
            "logomark.svg",
            "image/svg+xml",
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"/>".toByteArray(),
        )
        mockMvc.perform(
            multipart("/api/v1/admin/branding/logomark").file(file).actAs(case.actor),
        )
            .andExpect(status().`is`(case.expectedStatus))
    }

    @ParameterizedTest(name = "DELETE /admin/branding/[slot] {0}")
    @MethodSource("superadminOnlyDeniedMatrix")
    fun `reset denied for non-superadmins`(case: ActorExpectation) {
        mockMvc.perform(delete("/api/v1/admin/branding/logomark").actAs(case.actor))
            .andExpect(status().`is`(case.expectedStatus))
    }

    @Test
    fun `public GET is accessible without authentication`() {
        // 404 when nothing is uploaded yet — the contract says the
        // frontend falls back to the bundled default in that case.
        mockMvc.perform(get("/api/v1/branding/logomark"))
            .andExpect(status().isNotFound)
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
