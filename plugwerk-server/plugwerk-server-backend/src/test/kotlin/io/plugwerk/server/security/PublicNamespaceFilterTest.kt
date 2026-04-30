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

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.repository.NamespaceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PublicNamespaceFilterTest {

    @Mock
    lateinit var namespaceRepository: NamespaceRepository

    private lateinit var filter: PublicNamespaceFilter

    @BeforeEach
    fun setUp() {
        filter = PublicNamespaceFilter(namespaceRepository)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun namespaceEntity(slug: String, publicCatalog: Boolean) =
        NamespaceEntity(slug = slug, name = "test-org", publicCatalog = publicCatalog)

    @Test
    fun `GET on public namespace sets non-anonymous public-catalog token (#374)`() {
        whenever(namespaceRepository.findBySlug("acme"))
            .thenReturn(Optional.of(namespaceEntity("acme", publicCatalog = true)))

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/acme/plugins")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        val auth = SecurityContextHolder.getContext().authentication
        // Token MUST NOT be AnonymousAuthenticationToken — Spring's
        // AuthenticatedAuthorizationManager.authenticated() rejects anonymous tokens
        // via AuthenticationTrustResolver, which was the root cause of issue #374.
        assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken::class.java)
        assertThat(auth).isNotInstanceOf(AnonymousAuthenticationToken::class.java)
        assertThat(auth!!.name).isEqualTo("public:acme")
        assertThat(auth.authorities.map { it.authority })
            .containsExactly(PublicNamespaceFilter.PUBLIC_CATALOG_AUTHORITY)
        assertThat(auth.isAuthenticated).isTrue()
    }

    @Test
    fun `GET on private namespace leaves authentication null`() {
        whenever(namespaceRepository.findBySlug("acme"))
            .thenReturn(Optional.of(namespaceEntity("acme", publicCatalog = false)))

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/acme/plugins")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `GET on unknown namespace leaves authentication null`() {
        whenever(namespaceRepository.findBySlug("unknown"))
            .thenReturn(Optional.empty())

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/unknown/plugins")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `POST on public namespace does not set authentication`() {
        val request = MockHttpServletRequest("POST", "/api/v1/namespaces/acme/plugin-releases")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `GET on public namespace nested catalog path sets authentication`() {
        whenever(namespaceRepository.findBySlug("acme"))
            .thenReturn(Optional.of(namespaceEntity("acme", publicCatalog = true)))

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/acme/plugins/my-plugin/releases/1.0.0")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(
            SecurityContextHolder.getContext().authentication,
        ).isInstanceOf(UsernamePasswordAuthenticationToken::class.java)
    }

    @Test
    fun `GET on non-catalog sub-path of public namespace leaves authentication null`() {
        // ADR-0011: the carve-out is scoped to read-only catalog endpoints. Members,
        // access keys, settings etc. must remain unauthenticated even on a public
        // namespace so the chain rejects them with 401.
        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/acme/members")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `GET on public namespace updates check sets authentication`() {
        whenever(namespaceRepository.findBySlug("acme"))
            .thenReturn(Optional.of(namespaceEntity("acme", publicCatalog = true)))

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/acme/updates/check")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(
            SecurityContextHolder.getContext().authentication,
        ).isInstanceOf(UsernamePasswordAuthenticationToken::class.java)
    }

    @Test
    fun `does not override existing authentication`() {
        val existingAuth = AnonymousAuthenticationToken(
            "existing",
            "user",
            listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
        )
        SecurityContextHolder.getContext().authentication = existingAuth

        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/acme/plugins")
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isSameAs(existingAuth)
    }

    @Test
    fun `filter chain is always called`() {
        val chain = MockFilterChain()
        val request = MockHttpServletRequest("GET", "/api/v1/namespaces/acme/plugins")
        whenever(namespaceRepository.findBySlug("acme"))
            .thenReturn(Optional.of(namespaceEntity("acme", publicCatalog = false)))

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(chain.request).isNotNull()
    }
}
