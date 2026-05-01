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
package io.plugwerk.server.repository

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertFailsWith

class NamespaceMemberRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Autowired
    lateinit var memberRepository: NamespaceMemberRepository

    @Autowired
    lateinit var userRepository: UserRepository

    private lateinit var namespace: NamespaceEntity
    private lateinit var alice: UserEntity
    private lateinit var bob: UserEntity

    @BeforeEach
    fun setup() {
        namespace = namespaceRepository.save(NamespaceEntity(slug = "acme", name = "ACME Corp"))
        alice = userRepository.save(localUser("alice"))
        bob = userRepository.save(localUser("bob"))
    }

    private fun localUser(name: String) = UserEntity(
        username = name,
        displayName = name,
        email = "$name@example.test",
        source = UserSource.INTERNAL,
        passwordHash = "\$2a\$12\$hash",
    )

    private fun member(user: UserEntity, role: NamespaceRole = NamespaceRole.MEMBER) =
        NamespaceMemberEntity(namespace = namespace, user = user, role = role)

    @Test
    fun `findByNamespaceIdAndUserId returns member when present`() {
        memberRepository.save(member(alice))

        val found = memberRepository.findByNamespaceIdAndUserId(namespace.id!!, alice.id!!)

        assertThat(found).isPresent
        assertThat(found.get().user.id).isEqualTo(alice.id)
        assertThat(found.get().role).isEqualTo(NamespaceRole.MEMBER)
    }

    @Test
    fun `findByNamespaceIdAndUserId returns empty when not present`() {
        val ghost = userRepository.save(localUser("ghost"))
        val found = memberRepository.findByNamespaceIdAndUserId(namespace.id!!, ghost.id!!)

        assertThat(found).isEmpty
    }

    @Test
    fun `findAllByNamespaceId returns all members of namespace`() {
        memberRepository.save(member(alice, NamespaceRole.ADMIN))
        memberRepository.save(member(bob, NamespaceRole.MEMBER))

        val members = memberRepository.findAllByNamespaceId(namespace.id!!)

        assertThat(members).hasSize(2)
        assertThat(members.map { it.user.id }).containsExactlyInAnyOrder(alice.id, bob.id)
    }

    @Test
    fun `existsByNamespaceIdAndUserIdAndRoleIn returns true for matching role`() {
        memberRepository.save(member(alice, NamespaceRole.ADMIN))

        val result = memberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
            namespaceId = namespace.id!!,
            userId = alice.id!!,
            roles = listOf(NamespaceRole.ADMIN, NamespaceRole.MEMBER),
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `existsByNamespaceIdAndUserIdAndRoleIn returns false when role not in list`() {
        memberRepository.save(member(alice, NamespaceRole.READ_ONLY))

        val result = memberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
            namespaceId = namespace.id!!,
            userId = alice.id!!,
            roles = listOf(NamespaceRole.ADMIN, NamespaceRole.MEMBER),
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `deleteByNamespaceIdAndUserId removes the member`() {
        memberRepository.save(member(alice))
        memberRepository.flush()

        memberRepository.deleteByNamespaceIdAndUserId(namespace.id!!, alice.id!!)

        assertThat(memberRepository.findByNamespaceIdAndUserId(namespace.id!!, alice.id!!)).isEmpty
    }

    @Test
    fun `unique constraint prevents duplicate user in same namespace`() {
        memberRepository.save(member(alice, NamespaceRole.MEMBER))
        memberRepository.flush()

        assertFailsWith<DataIntegrityViolationException> {
            memberRepository.saveAndFlush(member(alice, NamespaceRole.ADMIN))
        }
    }

    @Test
    fun `same user may be member in different namespaces`() {
        val other = namespaceRepository.save(NamespaceEntity(slug = "other", name = "Other"))
        memberRepository.save(member(alice, NamespaceRole.ADMIN))
        memberRepository.flush()

        memberRepository.saveAndFlush(
            NamespaceMemberEntity(namespace = other, user = alice, role = NamespaceRole.MEMBER),
        )

        assertThat(memberRepository.findAllByNamespaceId(namespace.id!!)).hasSize(1)
        assertThat(memberRepository.findAllByNamespaceId(other.id!!)).hasSize(1)
    }
}
