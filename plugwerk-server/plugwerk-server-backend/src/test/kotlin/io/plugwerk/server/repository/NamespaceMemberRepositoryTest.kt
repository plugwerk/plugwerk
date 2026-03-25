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
package io.plugwerk.server.repository

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.domain.NamespaceRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID
import kotlin.test.assertFailsWith

class NamespaceMemberRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Autowired
    lateinit var memberRepository: NamespaceMemberRepository

    private lateinit var namespace: NamespaceEntity

    @BeforeEach
    fun setup() {
        namespace = namespaceRepository.save(NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp"))
    }

    private fun member(subject: String, role: NamespaceRole = NamespaceRole.MEMBER) =
        NamespaceMemberEntity(namespace = namespace, userSubject = subject, role = role)

    @Test
    fun `findByNamespaceIdAndUserSubject returns member when present`() {
        memberRepository.save(member("alice"))

        val found = memberRepository.findByNamespaceIdAndUserSubject(namespace.id!!, "alice")

        assertThat(found).isPresent
        assertThat(found.get().userSubject).isEqualTo("alice")
        assertThat(found.get().role).isEqualTo(NamespaceRole.MEMBER)
    }

    @Test
    fun `findByNamespaceIdAndUserSubject returns empty when not present`() {
        val found = memberRepository.findByNamespaceIdAndUserSubject(namespace.id!!, "nobody")

        assertThat(found).isEmpty
    }

    @Test
    fun `findAllByNamespaceId returns all members of namespace`() {
        memberRepository.save(member("alice", NamespaceRole.ADMIN))
        memberRepository.save(member("bob", NamespaceRole.MEMBER))

        val members = memberRepository.findAllByNamespaceId(namespace.id!!)

        assertThat(members).hasSize(2)
        assertThat(members.map { it.userSubject }).containsExactlyInAnyOrder("alice", "bob")
    }

    @Test
    fun `existsByNamespaceIdAndUserSubjectAndRoleIn returns true for matching role`() {
        memberRepository.save(member("alice", NamespaceRole.ADMIN))

        val result = memberRepository.existsByNamespaceIdAndUserSubjectAndRoleIn(
            namespaceId = namespace.id!!,
            userSubject = "alice",
            roles = listOf(NamespaceRole.ADMIN, NamespaceRole.MEMBER),
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `existsByNamespaceIdAndUserSubjectAndRoleIn returns false when role not in list`() {
        memberRepository.save(member("alice", NamespaceRole.READ_ONLY))

        val result = memberRepository.existsByNamespaceIdAndUserSubjectAndRoleIn(
            namespaceId = namespace.id!!,
            userSubject = "alice",
            roles = listOf(NamespaceRole.ADMIN, NamespaceRole.MEMBER),
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `deleteByNamespaceIdAndUserSubject removes the member`() {
        memberRepository.save(member("alice"))
        memberRepository.flush()

        memberRepository.deleteByNamespaceIdAndUserSubject(namespace.id!!, "alice")

        assertThat(memberRepository.findByNamespaceIdAndUserSubject(namespace.id!!, "alice")).isEmpty
    }

    @Test
    fun `unique constraint prevents duplicate subject in same namespace`() {
        memberRepository.save(member("alice", NamespaceRole.MEMBER))
        memberRepository.flush()

        assertFailsWith<DataIntegrityViolationException> {
            memberRepository.saveAndFlush(member("alice", NamespaceRole.ADMIN))
        }
    }

    @Test
    fun `same subject may be member in different namespaces`() {
        val other = namespaceRepository.save(NamespaceEntity(slug = "other", ownerOrg = "Other"))
        memberRepository.save(member("alice", NamespaceRole.ADMIN))
        memberRepository.flush()

        // should not throw
        memberRepository.saveAndFlush(
            NamespaceMemberEntity(namespace = other, userSubject = "alice", role = NamespaceRole.MEMBER),
        )

        assertThat(memberRepository.findAllByNamespaceId(namespace.id!!)).hasSize(1)
        assertThat(memberRepository.findAllByNamespaceId(other.id!!)).hasSize(1)
    }
}
