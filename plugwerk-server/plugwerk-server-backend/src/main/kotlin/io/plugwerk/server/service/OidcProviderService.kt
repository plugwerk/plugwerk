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
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.security.DbClientRegistrationRepository
import io.plugwerk.server.security.OidcProviderRegistry
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
    private val textEncryptor: TextEncryptor,
) {

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

    fun delete(id: UUID) {
        if (!oidcProviderRepository.existsById(id)) {
            throw EntityNotFoundException("OidcProvider", id.toString())
        }
        oidcProviderRepository.deleteById(id)
        refreshAllRegistries()
    }
}
