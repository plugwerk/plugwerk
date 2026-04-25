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

import io.plugwerk.api.ServerConfigApi
import io.plugwerk.api.model.OidcProviderLoginInfo
import io.plugwerk.api.model.ServerConfigResponse
import io.plugwerk.api.model.ServerConfigResponseAuth
import io.plugwerk.api.model.ServerConfigResponseGeneral
import io.plugwerk.api.model.ServerConfigResponseUpload
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.security.OidcRegistrationIds
import io.plugwerk.server.service.VersionProvider
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class ConfigController(
    private val settingsService: ApplicationSettingsService,
    private val versionProvider: VersionProvider,
    private val oidcProviderRepository: OidcProviderRepository,
) : ServerConfigApi {

    override fun getServerConfig(): ResponseEntity<ServerConfigResponse> = ResponseEntity.ok(
        ServerConfigResponse(
            version = versionProvider.getVersion(),
            upload = ServerConfigResponseUpload(maxFileSizeMb = settingsService.maxUploadSizeMb()),
            general = ServerConfigResponseGeneral(defaultTimezone = settingsService.defaultTimezone()),
            auth = ServerConfigResponseAuth(oidcProviders = enabledOidcProviderLoginInfo()),
        ),
    )

    /**
     * Builds the public OIDC-provider login info shown on the frontend login page (#79).
     *
     * Only `enabled = true` providers are exposed — disabled rows from the
     * `oidc_provider` table are admin scaffolding and must never leak to the
     * unauthenticated `/config` consumer. The fields are deliberately limited to
     * `id`, `name`, and `loginUrl`; never the issuerUri, the encrypted client
     * secret, or any other operator-only data.
     *
     * The Spring Security `registrationId` is sourced from
     * [OidcRegistrationIds.of] so this controller and the
     * [io.plugwerk.server.security.DbClientRegistrationRepository] agree on the
     * exact same identifier — drift between the two would silently produce 404s
     * on the OAuth2 authorization endpoint.
     */
    private fun enabledOidcProviderLoginInfo(): List<OidcProviderLoginInfo> =
        oidcProviderRepository.findAllByEnabledTrue().map { provider ->
            val registrationId = OidcRegistrationIds.of(provider)
            OidcProviderLoginInfo(
                id = registrationId,
                name = provider.name,
                loginUrl = "/oauth2/authorization/$registrationId",
            )
        }
}
