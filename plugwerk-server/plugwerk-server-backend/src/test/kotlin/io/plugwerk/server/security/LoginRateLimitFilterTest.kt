/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.security

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
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LoginRateLimitFilterTest {

    private val rateLimitService: LoginRateLimitService = mock()
    private val filter = LoginRateLimitFilter(rateLimitService)

    @Test
    fun `allowed request proceeds through filter chain`() {
        whenever(rateLimitService.tryConsume(any()))
            .thenReturn(RateLimitResult.Allowed(remainingTokens = 9))

        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        verify(rateLimitService).tryConsume("127.0.0.1")
    }

    @Test
    fun `rejected request returns 429 with Retry-After header`() {
        whenever(rateLimitService.tryConsume(any()))
            .thenReturn(RateLimitResult.Rejected(retryAfterSeconds = 42))

        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
        assertEquals("42", response.getHeader("Retry-After"))
        assertContains(response.contentAsString, "Too many login attempts")
        assertContains(response.contentAsString, "\"status\":429")
    }

    @Test
    fun `non-login POST requests are not rate limited`() {
        val request = MockHttpServletRequest("POST", "/api/v1/auth/change-password")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verifyNoInteractions(rateLimitService)
    }

    @Test
    fun `GET requests to login path are not rate limited`() {
        val request = MockHttpServletRequest("GET", "/api/v1/auth/login")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verifyNoInteractions(rateLimitService)
    }

    @Test
    fun `X-Forwarded-For header is used as client IP`() {
        whenever(rateLimitService.tryConsume(any()))
            .thenReturn(RateLimitResult.Allowed(remainingTokens = 9))

        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verify(rateLimitService).tryConsume("203.0.113.50")
    }

    @Test
    fun `falls back to remoteAddr when X-Forwarded-For is absent`() {
        whenever(rateLimitService.tryConsume(any()))
            .thenReturn(RateLimitResult.Allowed(remainingTokens = 9))

        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
        request.remoteAddr = "192.168.1.100"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verify(rateLimitService).tryConsume("192.168.1.100")
    }
}
