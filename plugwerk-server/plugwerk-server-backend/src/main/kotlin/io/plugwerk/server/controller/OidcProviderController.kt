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
package io.plugwerk.server.controller

import io.plugwerk.api.OidcProvidersApi
import io.plugwerk.api.model.OidcProviderCreateRequest
import io.plugwerk.api.model.OidcProviderDto
import io.plugwerk.api.model.OidcProviderType
import io.plugwerk.api.model.OidcProviderUpdateRequest
import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.OidcProviderService
import io.plugwerk.server.service.UnauthorizedException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID
import io.plugwerk.server.domain.OidcProviderType as DomainType

@RestController
@RequestMapping("/api/v1")
class OidcProviderController(
    private val oidcProviderService: OidcProviderService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : OidcProvidersApi {

    override fun listOidcProviders(): ResponseEntity<List<OidcProviderDto>> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        return ResponseEntity.ok(oidcProviderService.findAll().map { it.toDto() })
    }

    override fun createOidcProvider(
        oidcProviderCreateRequest: OidcProviderCreateRequest,
    ): ResponseEntity<OidcProviderDto> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        val provider = oidcProviderService.create(
            name = oidcProviderCreateRequest.name,
            providerType = oidcProviderCreateRequest.providerType.toDomain(),
            clientId = oidcProviderCreateRequest.clientId,
            clientSecret = oidcProviderCreateRequest.clientSecret,
            issuerUri = oidcProviderCreateRequest.issuerUri,
            scope = oidcProviderCreateRequest.scope ?: "openid email profile",
        )
        return ResponseEntity.created(URI("/api/v1/admin/oidc-providers/${provider.id}")).body(provider.toDto())
    }

    override fun updateOidcProvider(
        providerId: UUID,
        oidcProviderUpdateRequest: OidcProviderUpdateRequest,
    ): ResponseEntity<OidcProviderDto> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        var provider = oidcProviderService.findById(providerId)
        oidcProviderUpdateRequest.enabled?.let { provider = oidcProviderService.setEnabled(providerId, it) }
        oidcProviderUpdateRequest.clientSecret?.let {
            provider = oidcProviderService.updateClientSecret(providerId, it)
        }
        return ResponseEntity.ok(provider.toDto())
    }

    override fun deleteOidcProvider(providerId: UUID): ResponseEntity<Unit> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        oidcProviderService.delete(providerId)
        return ResponseEntity.noContent().build()
    }

    private fun OidcProviderEntity.toDto() = OidcProviderDto(
        id = id!!,
        name = name,
        providerType = providerType.toDto(),
        enabled = enabled,
        clientId = clientId,
        issuerUri = issuerUri,
        scope = scope,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun DomainType.toDto(): OidcProviderType = when (this) {
        DomainType.GENERIC_OIDC -> OidcProviderType.GENERIC_OIDC
        DomainType.KEYCLOAK -> OidcProviderType.KEYCLOAK
        DomainType.GITHUB -> OidcProviderType.GITHUB
        DomainType.GOOGLE -> OidcProviderType.GOOGLE
        DomainType.FACEBOOK -> OidcProviderType.FACEBOOK
    }

    private fun OidcProviderType.toDomain(): DomainType = when (this) {
        OidcProviderType.GENERIC_OIDC -> DomainType.GENERIC_OIDC
        OidcProviderType.KEYCLOAK -> DomainType.KEYCLOAK
        OidcProviderType.GITHUB -> DomainType.GITHUB
        OidcProviderType.GOOGLE -> DomainType.GOOGLE
        OidcProviderType.FACEBOOK -> DomainType.FACEBOOK
    }
}
