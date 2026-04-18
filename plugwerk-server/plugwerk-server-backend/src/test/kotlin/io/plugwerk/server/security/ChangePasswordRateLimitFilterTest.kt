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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ChangePasswordRateLimitFilterTest {

    private val rateLimitService: ChangePasswordRateLimitService = mock()
    private val filter = ChangePasswordRateLimitFilter(rateLimitService)

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticate(subject: String) {
        val auth = TestingAuthenticationToken(subject, "")
        auth.isAuthenticated = true
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `allowed request proceeds through chain and consumes token for subject`() {
        authenticate("alice")
        whenever(rateLimitService.tryConsume(any()))
            .thenReturn(RateLimitResult.Allowed(remainingTokens = 4))

        val request = MockHttpServletRequest("POST", "/api/v1/auth/change-password")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        verify(rateLimitService).tryConsume("alice")
    }

    @Test
    fun `rejected request returns 429 with Retry-After and change-password envelope`() {
        authenticate("alice")
        whenever(rateLimitService.tryConsume(any()))
            .thenReturn(RateLimitResult.Rejected(retryAfterSeconds = 120))

        val request = MockHttpServletRequest("POST", "/api/v1/auth/change-password")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
        assertEquals("120", response.getHeader("Retry-After"))
        assertContains(response.contentAsString, "Too many password-change attempts")
        assertContains(response.contentAsString, "\"status\":429")
    }

    @Test
    fun `non-matching paths are not rate-limited`() {
        authenticate("alice")

        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verifyNoInteractions(rateLimitService)
    }

    @Test
    fun `GET on change-password path is not rate-limited`() {
        authenticate("alice")

        val request = MockHttpServletRequest("GET", "/api/v1/auth/change-password")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verifyNoInteractions(rateLimitService)
    }

    @Test
    fun `unauthenticated request passes through without consuming a bucket token`() {
        // No SecurityContext set

        val request = MockHttpServletRequest("POST", "/api/v1/auth/change-password")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verifyNoInteractions(rateLimitService)
        assertEquals(200, response.status) // Chain unblocks; downstream filters reject with 401.
    }

    @Test
    fun `anonymous authentication is treated as unauthenticated`() {
        val anonymous = AnonymousAuthenticationToken(
            "anon",
            "anonymousUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"),
        )
        SecurityContextHolder.getContext().authentication = anonymous

        val request = MockHttpServletRequest("POST", "/api/v1/auth/change-password")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verifyNoInteractions(rateLimitService)
    }
}
