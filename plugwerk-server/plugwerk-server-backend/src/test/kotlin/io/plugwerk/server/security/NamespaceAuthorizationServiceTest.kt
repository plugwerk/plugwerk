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
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.NamespaceNotFoundException
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
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
    private val namespace = NamespaceEntity(slug = "acme", name = "ACME").also { it.id = nsId }

    private val aliceId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val strangerId = UUID.randomUUID()
    private val readerId = UUID.randomUUID()
    private val superadminId = UUID.randomUUID()

    private fun auth(subject: String) = TestingAuthenticationToken(subject, "")

    private fun localUser(id: UUID, isSuperadmin: Boolean = false) = UserEntity(
        id = id,
        username = "u-$id",
        displayName = "User $id",
        email = "$id@example.test",
        source = UserSource.INTERNAL,
        passwordHash = "\$2a\$12\$hash",
        isSuperadmin = isSuperadmin,
    )

    @Test
    fun `access key passes READ_ONLY for its own namespace`() {
        val auth = auth("key:acme")

        assertThatCode { service.requireRole("acme", auth, NamespaceRole.READ_ONLY) }.doesNotThrowAnyException()
    }

    @Test
    fun `access key is rejected for write operations on its own namespace`() {
        val auth = auth("key:acme")

        assertThatThrownBy { service.requireRole("acme", auth, NamespaceRole.MEMBER) }
            .isInstanceOf(ForbiddenException::class.java)
            .hasMessageContaining("read-only")

        assertThatThrownBy { service.requireRole("acme", auth, NamespaceRole.ADMIN) }
            .isInstanceOf(ForbiddenException::class.java)
            .hasMessageContaining("read-only")
    }

    @Test
    fun `access key is rejected for a different namespace`() {
        val auth = auth("key:acme-production")

        assertThatThrownBy { service.requireRole("acme-staging", auth, NamespaceRole.READ_ONLY) }
            .isInstanceOf(ForbiddenException::class.java)
            .hasMessageContaining("acme-staging")
    }

    @Test
    fun `throws NamespaceNotFoundException when namespace does not exist`() {
        whenever(namespaceRepository.findBySlug("missing")).thenReturn(Optional.empty())
        whenever(userRepository.findById(aliceId)).thenReturn(Optional.empty())

        assertThatThrownBy { service.requireRole("missing", auth(aliceId.toString()), NamespaceRole.MEMBER) }
            .isInstanceOf(NamespaceNotFoundException::class.java)
    }

    @Test
    fun `passes when user holds the exact required role`() {
        whenever(userRepository.findById(aliceId)).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
                eq(nsId),
                eq(aliceId),
                eq(listOf(NamespaceRole.MEMBER, NamespaceRole.ADMIN)),
            ),
        ).thenReturn(true)

        assertThatCode {
            service.requireRole("acme", auth(aliceId.toString()), NamespaceRole.MEMBER)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `ADMIN role satisfies MEMBER requirement`() {
        whenever(userRepository.findById(adminId)).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
                eq(nsId),
                eq(adminId),
                eq(listOf(NamespaceRole.MEMBER, NamespaceRole.ADMIN)),
            ),
        ).thenReturn(true)

        assertThatCode {
            service.requireRole("acme", auth(adminId.toString()), NamespaceRole.MEMBER)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `MEMBER role does not satisfy ADMIN requirement`() {
        whenever(userRepository.findById(memberId)).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
                eq(nsId),
                eq(memberId),
                eq(listOf(NamespaceRole.ADMIN)),
            ),
        ).thenReturn(false)

        assertThatThrownBy { service.requireRole("acme", auth(memberId.toString()), NamespaceRole.ADMIN) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `throws ForbiddenException when user has no role in namespace`() {
        whenever(userRepository.findById(strangerId)).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
                eq(nsId),
                eq(strangerId),
                any(),
            ),
        ).thenReturn(false)

        assertThatThrownBy { service.requireRole("acme", auth(strangerId.toString()), NamespaceRole.READ_ONLY) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `READ_ONLY requirement accepts all roles`() {
        whenever(userRepository.findById(readerId)).thenReturn(Optional.empty())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
                eq(nsId),
                eq(readerId),
                eq(NamespaceRole.entries),
            ),
        ).thenReturn(true)

        assertThatCode {
            service.requireRole("acme", auth(readerId.toString()), NamespaceRole.READ_ONLY)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `superadmin bypasses member check in requireRole`() {
        whenever(
            userRepository.findById(superadminId),
        ).thenReturn(Optional.of(localUser(superadminId, isSuperadmin = true)))

        assertThatCode {
            service.requireRole("acme", auth(superadminId.toString()), NamespaceRole.ADMIN)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `requireSuperadmin passes for superadmin user`() {
        whenever(
            userRepository.findById(superadminId),
        ).thenReturn(Optional.of(localUser(superadminId, isSuperadmin = true)))

        assertThatCode { service.requireSuperadmin(auth(superadminId.toString())) }.doesNotThrowAnyException()
    }

    @Test
    fun `requireSuperadmin throws for regular user`() {
        whenever(userRepository.findById(aliceId)).thenReturn(Optional.empty())

        assertThatThrownBy { service.requireSuperadmin(auth(aliceId.toString())) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `requireSuperadmin throws for access key principal`() {
        // Access key principals are never superadmin
        assertThatThrownBy { service.requireSuperadmin(auth("key:acme-production")) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `isSuperadmin rejects access key principal even when DB lookup is configured (RC-015 and KT-014)`() {
        // Defense-in-depth regression guard: the access-key-prefix guard must
        // short-circuit BEFORE any DB lookup. The lenient stub guarantees that
        // a future refactor that drops the guard would actually hit the DB
        // and return true — which would fail this assertion immediately.
        val auth = auth("key:acme")
        // No findById stub needed — the guard never reaches it.

        assert(!service.isSuperadmin(auth))
        // Suppress unused-stub warning if a maintainer later adds findById usage.
        @Suppress("UnusedExpression")
        lenient()
    }

    @Test
    fun `requireSuperadmin throws when JWT subject is not a UUID`() {
        // Pre-#351 / forged tokens land here. parseUserId returns null → not superadmin.
        assertThatThrownBy { service.requireSuperadmin(auth("alice-legacy-username")) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    // ---------------------------------------------------------------------------------
    // SpEL-friendly boolean mirrors used by @PreAuthorize (issue #257)
    // ---------------------------------------------------------------------------------

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun withAuth(subject: String) {
        SecurityContextHolder.getContext().authentication = auth(subject)
    }

    @Test
    fun `hasRole returns true when principal holds the minimum role`() {
        withAuth(aliceId.toString())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(userRepository.findById(aliceId)).thenReturn(Optional.empty())
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
                eq(nsId),
                eq(aliceId),
                any(),
            ),
        ).thenReturn(true)

        assert(service.hasRole("acme", NamespaceRole.MEMBER))
    }

    @Test
    fun `hasRole returns false when principal lacks the role`() {
        withAuth(aliceId.toString())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(userRepository.findById(aliceId)).thenReturn(Optional.empty())
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
                eq(nsId),
                eq(aliceId),
                any(),
            ),
        ).thenReturn(false)

        assert(!service.hasRole("acme", NamespaceRole.ADMIN))
    }

    @Test
    fun `hasRole returns false when no authentication is present`() {
        assert(!service.hasRole("acme", NamespaceRole.READ_ONLY))
    }

    @Test
    fun `hasRole returns false for unknown namespace`() {
        withAuth(aliceId.toString())
        whenever(namespaceRepository.findBySlug("ghost")).thenReturn(Optional.empty())
        whenever(userRepository.findById(aliceId)).thenReturn(Optional.empty())

        assert(!service.hasRole("ghost", NamespaceRole.READ_ONLY))
    }

    @Test
    fun `hasRole returns false for write role from access key`() {
        withAuth("key:acme")

        assert(!service.hasRole("acme", NamespaceRole.MEMBER))
    }

    @Test
    fun `hasRole propagates unexpected runtime errors instead of returning false`() {
        // KT-004 / #287 regression: the narrow catch on ForbiddenException +
        // NamespaceNotFoundException must NOT swallow DB connectivity failures.
        withAuth(aliceId.toString())
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(userRepository.findById(aliceId)).thenReturn(Optional.empty())
        whenever(
            namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
                eq(nsId),
                eq(aliceId),
                any(),
            ),
        ).thenThrow(RuntimeException("connection refused"))

        assertThatThrownBy { service.hasRole("acme", NamespaceRole.READ_ONLY) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("connection refused")
    }

    @Test
    fun `hasRole string overload delegates to enum-typed hasRole`() {
        withAuth("key:acme")

        assert(service.hasRole("acme", "READ_ONLY"))
        assert(!service.hasRole("acme", "ADMIN"))
    }

    @Test
    fun `isCurrentUserSuperadmin returns true for superadmin`() {
        withAuth(superadminId.toString())
        whenever(
            userRepository.findById(superadminId),
        ).thenReturn(Optional.of(localUser(superadminId, isSuperadmin = true)))

        assert(service.isCurrentUserSuperadmin())
    }

    @Test
    fun `isCurrentUserSuperadmin returns false without authentication`() {
        assert(!service.isCurrentUserSuperadmin())
    }

    @Test
    fun `isCurrentUserSuperadmin returns false for access key principal`() {
        withAuth("key:acme")

        assert(!service.isCurrentUserSuperadmin())
    }
}
