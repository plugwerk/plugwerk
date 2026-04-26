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

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.DbClientRegistrationRepository
import io.plugwerk.server.security.OidcProviderRegistry
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class OidcProviderService(
    private val oidcProviderRepository: OidcProviderRepository,
    private val oidcProviderRegistry: OidcProviderRegistry,
    private val dbClientRegistrationRepository: DbClientRegistrationRepository,
    private val oidcIdentityRepository: OidcIdentityRepository,
    private val userRepository: UserRepository,
    private val textEncryptor: TextEncryptor,
) {

    private val log = LoggerFactory.getLogger(OidcProviderService::class.java)

    /**
     * Rebuilds both registries that depend on the enabled-providers list:
     *   - [OidcProviderRegistry]            — JWKS-based JwtDecoders for the
     *     resource-server side (verify incoming bearer tokens, #77).
     *   - [DbClientRegistrationRepository]  — Spring Security ClientRegistrations
     *     for the browser login flow (#79).
     * Either-or refreshes drift the two registries against the database; calling
     * them together from a single helper guarantees they stay in lockstep.
     */
    private fun refreshAllRegistries() {
        oidcProviderRegistry.refresh()
        dbClientRegistrationRepository.refresh()
    }

    @Transactional(readOnly = true)
    fun findAll(): List<OidcProviderEntity> = oidcProviderRepository.findAll()

    @Transactional(readOnly = true)
    fun findById(id: UUID): OidcProviderEntity =
        oidcProviderRepository.findById(id).orElseThrow { EntityNotFoundException("OidcProvider", id.toString()) }

    fun create(
        name: String,
        providerType: OidcProviderType,
        clientId: String,
        clientSecret: String,
        issuerUri: String?,
        scope: String,
    ): OidcProviderEntity {
        val provider = oidcProviderRepository.save(
            OidcProviderEntity(
                name = name,
                providerType = providerType,
                clientId = clientId,
                clientSecretEncrypted = textEncryptor.encrypt(clientSecret),
                issuerUri = issuerUri,
                scope = scope,
                enabled = false,
            ),
        )
        return provider
    }

    fun setEnabled(id: UUID, enabled: Boolean): OidcProviderEntity {
        val provider = findById(id)
        provider.enabled = enabled
        val saved = oidcProviderRepository.save(provider)
        refreshAllRegistries()
        return saved
    }

    fun updateClientSecret(id: UUID, newSecret: String): OidcProviderEntity {
        val provider = findById(id)
        provider.clientSecretEncrypted = textEncryptor.encrypt(newSecret)
        val saved = oidcProviderRepository.save(provider)
        // The browser-flow ClientRegistration caches the decrypted secret in
        // the registration object; without a refresh, an updated secret would
        // not take effect until the next server restart.
        refreshAllRegistries()
        return saved
    }

    /**
     * Deletes an OIDC provider, applying issue #351's Politik C: any
     * `plugwerk_user` rows that authenticated through this provider get
     * `enabled = false` BEFORE the SQL cascade wipes their `oidc_identity`
     * rows. The user records survive for audit purposes — operators who
     * later want to permanently delete those users can do so via the admin
     * UI; until then the disabled flag prevents any new login attempt.
     */
    fun delete(id: UUID) {
        if (!oidcProviderRepository.existsById(id)) {
            throw EntityNotFoundException("OidcProvider", id.toString())
        }
        val orphanedUserIds = oidcIdentityRepository.findAllByOidcProviderId(id)
            .mapNotNull { it.user.id }
        if (orphanedUserIds.isNotEmpty()) {
            val disabled = userRepository.disableAll(orphanedUserIds)
            log.warn(
                "Disabled {} user(s) orphaned by deletion of OIDC provider {} (Politik C, issue #351)",
                disabled,
                id,
            )
        }
        // The CASCADE on oidc_identity.oidc_provider_id removes the identity rows
        // here. plugwerk_user rows stay (now flagged enabled=false above).
        oidcProviderRepository.deleteById(id)
        refreshAllRegistries()
    }
}
