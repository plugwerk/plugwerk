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
package io.plugwerk.server.service

import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import io.plugwerk.server.repository.NamespaceRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Service for managing namespace-scoped API access keys.
 *
 * Access keys are long-lived credentials suitable for CI/CD pipelines and service accounts.
 * The plain-text key is returned exactly once at creation time; only its BCrypt hash is persisted.
 *
 * Generated keys use the format `pwk_<40 random alphanumeric chars>`.
 */
@Service
@Transactional(readOnly = true)
class AccessKeyService(
    private val accessKeyRepository: NamespaceAccessKeyRepository,
    private val namespaceRepository: NamespaceRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    companion object {
        private const val KEY_PREFIX = "pwk_"
        private const val KEY_RANDOM_LENGTH = 40
        private val ALPHANUMERIC = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        private val SECURE_RANDOM = SecureRandom()
    }

    /**
     * Lists all access keys for the given namespace (active and revoked).
     */
    fun listByNamespace(namespaceSlug: String): List<NamespaceAccessKeyEntity> {
        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }
        return accessKeyRepository.findAllByNamespace(namespace)
    }

    /**
     * Generates a new access key for the namespace.
     *
     * @return a pair of the persisted entity and the plain-text key (returned only once).
     */
    @Transactional
    fun create(
        namespaceSlug: String,
        name: String,
        expiresAt: OffsetDateTime?,
    ): Pair<NamespaceAccessKeyEntity, String> {
        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }

        if (accessKeyRepository.existsByNamespaceAndName(namespace, name)) {
            throw ConflictException("An access key named '$name' already exists in namespace '$namespaceSlug'")
        }

        val plainKey = generatePlainKey()
        val keyHash = requireNotNull(passwordEncoder.encode(plainKey)) { "PasswordEncoder returned null" }

        val entity = accessKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = keyHash,
                keyPrefix = plainKey.take(8),
                name = name,
                expiresAt = expiresAt,
            ),
        )
        return entity to plainKey
    }

    /**
     * Revokes an access key by setting [NamespaceAccessKeyEntity.revoked] to `true`.
     *
     * @throws EntityNotFoundException if no key with the given ID exists in the namespace.
     */
    @Transactional
    fun revoke(namespaceSlug: String, keyId: UUID) {
        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }

        val key = accessKeyRepository.findById(keyId)
            .filter { it.namespace.id == namespace.id }
            .orElseThrow { EntityNotFoundException("AccessKey", keyId.toString()) }

        key.revoked = true
        accessKeyRepository.save(key)
    }

    private fun generatePlainKey(): String {
        val randomPart = (1..KEY_RANDOM_LENGTH)
            .map { ALPHANUMERIC[SECURE_RANDOM.nextInt(ALPHANUMERIC.size)] }
            .joinToString("")
        return "$KEY_PREFIX$randomPart"
    }
}
