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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Verifies the filter-chain-level rate limit introduced by issue #259.
 *
 * The default change-password limit is 5 attempts per 300s per subject. These tests drive
 * the live Spring context so they exercise the exact filter ordering in production: JWT
 * resource-server authenticates → [io.plugwerk.server.security.ChangePasswordRateLimitFilter]
 * consumes a bucket token → the controller runs the business logic.
 *
 * Uses [Actor.UNRELATED] so the bucket starts clean (no other change-password test uses
 * that actor). Uses wrong-current-password payloads — the filter consumes a token regardless
 * of the controller outcome, which is the point of brute-force protection.
 */
class ChangePasswordRateLimitIntegrationTest : AbstractAuthorizationTest() {

    @Test
    fun `sixth rapid change-password attempt returns 429 with Retry-After and ErrorResponse envelope`() {
        val actor = Actor.UNRELATED
        val payload = objectMapper.writeValueAsString(
            mapOf("currentPassword" to "wrong", "newPassword" to "new-password-12345!"),
        )

        // First 5 attempts pass the filter; controller returns 401 (wrong password).
        repeat(5) { i ->
            mockMvc.perform(
                post("/api/v1/auth/change-password")
                    .actAs(actor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            )
                .andExpect(status().isUnauthorized)
                .andExpect { assert(it.response.status != 429) { "Attempt ${i + 1} unexpectedly 429" } }
        }

        // Sixth attempt: filter short-circuits with 429.
        mockMvc.perform(
            post("/api/v1/auth/change-password")
                .actAs(actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.error").value("Too Many Requests"))
            .andExpect(jsonPath("$.message").value("Too many password-change attempts. Please try again later."))
    }

    @Test
    fun `login rate-limit is independent of change-password rate-limit`() {
        // Drain the change-password bucket for the SUPERADMIN subject.
        val payload = objectMapper.writeValueAsString(
            mapOf("currentPassword" to "wrong", "newPassword" to "new-password-12345!"),
        )
        repeat(5) {
            mockMvc.perform(
                post("/api/v1/auth/change-password")
                    .actAs(Actor.SUPERADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            )
        }
        mockMvc.perform(
            post("/api/v1/auth/change-password")
                .actAs(Actor.SUPERADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        ).andExpect(status().isTooManyRequests)

        // Login still works on its own bucket (IP-keyed, not subject-keyed).
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("username" to "admin", "password" to "smoke-test-password"),
                    ),
                ),
        )
            .andExpect(status().isOk)
    }
}
