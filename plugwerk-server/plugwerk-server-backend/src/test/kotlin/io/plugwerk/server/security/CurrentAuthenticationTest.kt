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
package io.plugwerk.server.security

import io.plugwerk.server.service.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class CurrentAuthenticationTest {

    @AfterEach
    fun clear() {
        SecurityContextHolder.clearContext()
    }

    private fun setAuth(name: String) {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(name, "n/a", "ROLE_USER").apply { isAuthenticated = true }
    }

    // --- currentAuthentication() ----------------------------------------------------

    @Test
    fun `currentAuthentication returns the authentication when present`() {
        setAuth("alice")

        val auth = currentAuthentication()

        assertThat(auth.name).isEqualTo("alice")
    }

    @Test
    fun `currentAuthentication throws UnauthorizedException when absent`() {
        // SecurityContextHolder cleared by @AfterEach; here we start with no auth.
        assertThatThrownBy { currentAuthentication() }
            .isInstanceOf(UnauthorizedException::class.java)
            .hasMessage("Authentication required")
    }

    // --- currentAuthenticationOrNull() ----------------------------------------------

    @Test
    fun `currentAuthenticationOrNull returns the authentication when present`() {
        setAuth("alice")

        assertThat(currentAuthenticationOrNull()?.name).isEqualTo("alice")
    }

    @Test
    fun `currentAuthenticationOrNull returns null when absent`() {
        assertThat(currentAuthenticationOrNull()).isNull()
    }

    // --- currentAuthenticationOrElse(default, block) --------------------------------

    @Test
    fun `currentAuthenticationOrElse runs the block with the authentication when present`() {
        setAuth("alice")

        val result = currentAuthenticationOrElse(default = "anon") { auth -> auth.name }

        assertThat(result).isEqualTo("alice")
    }

    @Test
    fun `currentAuthenticationOrElse returns the default when no authentication is present`() {
        val result = currentAuthenticationOrElse(default = "anon") { auth -> auth.name }

        assertThat(result).isEqualTo("anon")
    }

    @Test
    fun `currentAuthenticationOrElse infers the result type from the default — Boolean`() {
        // No auth → false (the bare default). The `block` is never called, so the
        // inference site is the `default = false` argument.
        val result: Boolean = currentAuthenticationOrElse(default = false) { _ -> true }

        assertThat(result).isFalse()
    }

    @Test
    fun `currentAuthenticationOrElse infers the result type from the default — nullable Pair`() {
        val result: Pair<String?, Boolean?> =
            currentAuthenticationOrElse(default = null to null) { auth -> auth.name to true }

        assertThat(result).isEqualTo(null to null)
    }

    @Test
    fun `currentAuthenticationOrElse propagates exceptions from the block`() {
        setAuth("alice")

        assertThatThrownBy {
            currentAuthenticationOrElse(default = false) { _ ->
                throw IllegalStateException("boom")
            }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("boom")
    }
}
