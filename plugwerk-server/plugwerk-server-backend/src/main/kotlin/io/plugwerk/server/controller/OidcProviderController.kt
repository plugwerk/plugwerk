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
package io.plugwerk.server.controller

import io.plugwerk.api.OidcProvidersApi
import io.plugwerk.api.model.OidcDiscoveryRequest
import io.plugwerk.api.model.OidcDiscoveryResponse
import io.plugwerk.api.model.OidcProviderCreateRequest
import io.plugwerk.api.model.OidcProviderDto
import io.plugwerk.api.model.OidcProviderType
import io.plugwerk.api.model.OidcProviderUpdateRequest
import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.OidcProviderPatch
import io.plugwerk.server.service.OidcProviderService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        return ResponseEntity.ok(oidcProviderService.findAll().map { it.toDto() })
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun createOidcProvider(
        oidcProviderCreateRequest: OidcProviderCreateRequest,
    ): ResponseEntity<OidcProviderDto> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        val provider = oidcProviderService.create(
            name = oidcProviderCreateRequest.name,
            providerType = oidcProviderCreateRequest.providerType.toDomain(),
            clientId = oidcProviderCreateRequest.clientId,
            clientSecret = oidcProviderCreateRequest.clientSecret,
            issuerUri = oidcProviderCreateRequest.issuerUri,
            scope = oidcProviderCreateRequest.scope ?: "openid email profile",
            authorizationUri = oidcProviderCreateRequest.authorizationUri,
            tokenUri = oidcProviderCreateRequest.tokenUri,
            userInfoUri = oidcProviderCreateRequest.userInfoUri,
            jwkSetUri = oidcProviderCreateRequest.jwkSetUri,
            subjectAttribute = oidcProviderCreateRequest.subjectAttribute,
            emailAttribute = oidcProviderCreateRequest.emailAttribute,
            displayNameAttribute = oidcProviderCreateRequest.displayNameAttribute,
        )
        return ResponseEntity.created(URI("/api/v1/admin/oidc-providers/${provider.id}")).body(provider.toDto())
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun updateOidcProvider(
        providerId: UUID,
        oidcProviderUpdateRequest: OidcProviderUpdateRequest,
    ): ResponseEntity<OidcProviderDto> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        val patch = OidcProviderPatch(
            enabled = oidcProviderUpdateRequest.enabled,
            name = oidcProviderUpdateRequest.name,
            clientId = oidcProviderUpdateRequest.clientId,
            clientSecretPlaintext = oidcProviderUpdateRequest.clientSecret,
            issuerUri = oidcProviderUpdateRequest.issuerUri?.toString(),
            scope = oidcProviderUpdateRequest.scope,
            authorizationUri = oidcProviderUpdateRequest.authorizationUri?.toString(),
            tokenUri = oidcProviderUpdateRequest.tokenUri?.toString(),
            userInfoUri = oidcProviderUpdateRequest.userInfoUri?.toString(),
            jwkSetUri = oidcProviderUpdateRequest.jwkSetUri?.toString(),
            subjectAttribute = oidcProviderUpdateRequest.subjectAttribute,
            emailAttribute = oidcProviderUpdateRequest.emailAttribute,
            displayNameAttribute = oidcProviderUpdateRequest.displayNameAttribute,
        )
        val updated = oidcProviderService.update(providerId, patch)
        return ResponseEntity.ok(updated.toDto())
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun deleteOidcProvider(providerId: UUID): ResponseEntity<Unit> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        oidcProviderService.delete(providerId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun discoverOidcEndpoints(
        oidcDiscoveryRequest: OidcDiscoveryRequest,
    ): ResponseEntity<OidcDiscoveryResponse> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        val outcome = oidcProviderService.discoverEndpoints(oidcDiscoveryRequest.issuerUri)
        return ResponseEntity.ok(
            OidcDiscoveryResponse(
                success = outcome.success,
                authorizationUri = outcome.authorizationUri,
                tokenUri = outcome.tokenUri,
                userInfoUri = outcome.userInfoUri,
                jwkSetUri = outcome.jwkSetUri,
                error = outcome.error,
            ),
        )
    }

    private fun OidcProviderEntity.toDto() = OidcProviderDto(
        id = id!!,
        name = name,
        providerType = providerType.toDto(),
        enabled = enabled,
        clientId = clientId,
        issuerUri = issuerUri,
        scope = scope,
        authorizationUri = authorizationUri,
        tokenUri = tokenUri,
        userInfoUri = userInfoUri,
        jwkSetUri = jwkSetUri,
        subjectAttribute = subjectAttribute,
        emailAttribute = emailAttribute,
        displayNameAttribute = displayNameAttribute,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun DomainType.toDto(): OidcProviderType = when (this) {
        DomainType.OIDC -> OidcProviderType.OIDC
        DomainType.GITHUB -> OidcProviderType.GITHUB
        DomainType.GOOGLE -> OidcProviderType.GOOGLE
        DomainType.FACEBOOK -> OidcProviderType.FACEBOOK
        DomainType.OAUTH2_GENERIC -> OidcProviderType.OAUTH2_GENERIC
    }

    private fun OidcProviderType.toDomain(): DomainType = when (this) {
        OidcProviderType.OIDC -> DomainType.OIDC
        OidcProviderType.GITHUB -> DomainType.GITHUB
        OidcProviderType.GOOGLE -> DomainType.GOOGLE
        OidcProviderType.FACEBOOK -> DomainType.FACEBOOK
        OidcProviderType.OAUTH2_GENERIC -> DomainType.OAUTH2_GENERIC
    }
}
