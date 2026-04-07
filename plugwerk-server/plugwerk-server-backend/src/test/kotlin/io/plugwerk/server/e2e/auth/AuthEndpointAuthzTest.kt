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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

class AuthEndpointAuthzTest : AbstractAuthorizationTest() {

    // ------------------------------------------------------------------ //
    // POST /auth/login                                                    //
    // ------------------------------------------------------------------ //

    @Test
    fun `login with valid credentials returns 200 and token`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "username" to "admin",
                            "password" to "smoke-test-password",
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.isSuperadmin").value(true))
    }

    @Test
    fun `login with valid non-superadmin credentials returns isSuperadmin false`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(mapOf("username" to "ns1-admin", "password" to TEST_PASSWORD)),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isSuperadmin").value(false))
    }

    @Test
    fun `login with wrong password returns 401`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("username" to "admin", "password" to "wrong-password"))),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login with unknown user returns 401`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("username" to "nonexistent", "password" to "whatever"))),
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------ //
    // POST /auth/logout                                                   //
    // ------------------------------------------------------------------ //

    @ParameterizedTest(name = "logout as {0}")
    @MethodSource("logoutMatrix")
    fun `logout authorization`(expectation: ActorExpectation) {
        // Use a fresh token so that revoking it does not break other tests that
        // rely on the shared tokenCache entries.
        val request = post("/api/v1/auth/logout")
        val authenticatedRequest = when {
            expectation.actor == Actor.ANONYMOUS -> request

            expectation.actor.isApiKey -> {
                val key = requireNotNull(apiKeyCache[expectation.actor])
                request.header("X-Api-Key", key)
            }

            else -> {
                val token = freshToken(expectation.actor)
                request.header("Authorization", "Bearer $token")
            }
        }
        mockMvc.perform(authenticatedRequest)
            .andExpect(status().`is`(expectation.expectedStatus))
    }

    // ------------------------------------------------------------------ //
    // POST /auth/change-password                                          //
    // ------------------------------------------------------------------ //

    @ParameterizedTest(name = "change-password as {0}")
    @MethodSource("changePasswordDeniedMatrix")
    fun `change-password denied for unauthorized actors`(expectation: ActorExpectation) {
        mockMvc.perform(
            post("/api/v1/auth/change-password")
                .actAs(expectation.actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("currentPassword" to "wrong", "newPassword" to "new-password-12345!"),
                    ),
                ),
        )
            .andExpect(status().`is`(expectation.expectedStatus))
    }

    // ------------------------------------------------------------------ //
    // Method sources                                                      //
    // ------------------------------------------------------------------ //

    companion object {
        @JvmStatic
        fun logoutMatrix(): Stream<ActorExpectation> = Stream.of(
            ActorExpectation(Actor.ANONYMOUS, 401),
            // logout returns 204 No Content on success
            ActorExpectation(Actor.SUPERADMIN, 204),
            ActorExpectation(Actor.NS1_ADMIN, 204),
            ActorExpectation(Actor.NS1_READ_ONLY, 204),
            ActorExpectation(Actor.UNRELATED, 204),
            ActorExpectation(Actor.API_KEY_NS1, 401),
        )

        @JvmStatic
        fun changePasswordDeniedMatrix(): Stream<ActorExpectation> = Stream.of(
            // change-password is under /auth/** (permitAll), so security passes.
            // The controller then looks up the user by name (anonymous / key principal)
            // and fails with EntityNotFoundException → 404.
            ActorExpectation(Actor.ANONYMOUS, 404),
            ActorExpectation(Actor.API_KEY_NS1, 404),
        )
    }
}
