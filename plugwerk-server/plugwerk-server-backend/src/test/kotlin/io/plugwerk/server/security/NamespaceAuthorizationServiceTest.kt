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

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.NamespaceNotFoundException
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.TestingAuthenticationToken
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NamespaceAuthorizationServiceTest {

    @Mock
    private lateinit var namespaceRepository: NamespaceRepository

    @Mock
    private lateinit var namespaceMemberRepository: NamespaceMemberRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @InjectMocks
    private lateinit var service: NamespaceAuthorizationService

    private val nsId = UUID.randomUUID()
    private val namespace = NamespaceEntity(slug = "acme", ownerOrg = "ACME").also { it.id = nsId }

    private fun auth(subject: String) = TestingAuthenticationToken(subject, "")

    private fun superadminUser(username: String) = UserEntity(
        username = username,
        passwordHash = "\$2a\$12\$hash",
        isSuperadmin = true,
    )

    @Test
    fun `access key principal bypasses member check`() {
        val auth = auth("key:acme-production")

        // No repository interaction expected — should pass without throwing
        assertThatCode { service.requireRole("acme", auth, NamespaceRole.ADMIN) }.doesNotThrowAnyException()
    }

    @Test
    fun `throws NamespaceNotFoundException when namespace does not exist`() {
        whenever(namespaceRepository.findBySlug("missing")).thenReturn(Optional.empty())
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.empty())
        val auth = auth("alice")

        assertThatThrownBy { service.requireRole("missing", auth, NamespaceRole.MEMBER) }
            .isInstanceOf(NamespaceNotFoundException::class.java)
    }

    @Test
    fun `passes when user holds the exact required role`() {
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserSubjectAndRoleIn(
                eq(nsId),
                eq("alice"),
                eq(listOf(NamespaceRole.MEMBER, NamespaceRole.ADMIN)),
            ),
        ).thenReturn(true)

        assertThatCode { service.requireRole("acme", auth("alice"), NamespaceRole.MEMBER) }.doesNotThrowAnyException()
    }

    @Test
    fun `ADMIN role satisfies MEMBER requirement`() {
        whenever(userRepository.findByUsername("admin")).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserSubjectAndRoleIn(
                eq(nsId),
                eq("admin"),
                eq(listOf(NamespaceRole.MEMBER, NamespaceRole.ADMIN)),
            ),
        ).thenReturn(true)

        assertThatCode { service.requireRole("acme", auth("admin"), NamespaceRole.MEMBER) }.doesNotThrowAnyException()
    }

    @Test
    fun `MEMBER role does not satisfy ADMIN requirement`() {
        whenever(userRepository.findByUsername("member")).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserSubjectAndRoleIn(
                eq(nsId),
                eq("member"),
                eq(listOf(NamespaceRole.ADMIN)),
            ),
        ).thenReturn(false)

        assertThatThrownBy { service.requireRole("acme", auth("member"), NamespaceRole.ADMIN) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `throws ForbiddenException when user has no role in namespace`() {
        whenever(userRepository.findByUsername("stranger")).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserSubjectAndRoleIn(
                eq(nsId),
                eq("stranger"),
                any(),
            ),
        ).thenReturn(false)

        assertThatThrownBy { service.requireRole("acme", auth("stranger"), NamespaceRole.READ_ONLY) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `READ_ONLY requirement accepts all roles`() {
        whenever(userRepository.findByUsername("reader")).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserSubjectAndRoleIn(
                eq(nsId),
                eq("reader"),
                eq(NamespaceRole.entries),
            ),
        ).thenReturn(true)

        assertThatCode {
            service.requireRole("acme", auth("reader"), NamespaceRole.READ_ONLY)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `superadmin bypasses member check in requireRole`() {
        whenever(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superadminUser("superadmin")))

        // No namespace or member repository interaction expected
        assertThatCode {
            service.requireRole("acme", auth("superadmin"), NamespaceRole.ADMIN)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `requireSuperadmin passes for superadmin user`() {
        whenever(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superadminUser("superadmin")))

        assertThatCode { service.requireSuperadmin(auth("superadmin")) }.doesNotThrowAnyException()
    }

    @Test
    fun `requireSuperadmin throws for regular user`() {
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.empty())

        assertThatThrownBy { service.requireSuperadmin(auth("alice")) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `requireSuperadmin throws for access key principal`() {
        // Access key principals are never superadmin
        assertThatThrownBy { service.requireSuperadmin(auth("key:acme-production")) }
            .isInstanceOf(ForbiddenException::class.java)
    }
}
