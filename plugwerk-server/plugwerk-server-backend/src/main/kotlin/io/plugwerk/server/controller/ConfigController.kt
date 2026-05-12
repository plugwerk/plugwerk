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
import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
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
            general = ServerConfigResponseGeneral(
                defaultTimezone = settingsService.defaultTimezone(),
                siteName = settingsService.siteName(),
            ),
            auth = ServerConfigResponseAuth(
                oidcProviders = enabledOidcProviderLoginInfo(),
                selfRegistrationEnabled = settingsService.selfRegistrationEnabled(),
                passwordResetEnabled = settingsService.passwordResetEnabled(),
            ),
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
            val loginUrl = "/oauth2/authorization/$registrationId"
            OidcProviderLoginInfo(
                id = registrationId,
                name = provider.name,
                loginUrl = loginUrl,
                accountPickerLoginUrl = accountPickerLoginUrlFor(provider, loginUrl),
                accountSwitchHintUrl = accountSwitchHintUrlFor(provider),
                iconKind = iconKindFor(provider),
            )
        }

    /**
     * Brand-agnostic icon identifier the frontend renders on the provider
     * button. Stable enum surface that does **not** mirror
     * [OidcProviderType] one-to-one — both `OIDC` and `OAUTH2` map to a
     * generic key glyph because there is no vendor mark to display.
     * Using a separate enum keeps the public `/config` response decoupled
     * from internal domain-type evolution.
     */
    private fun iconKindFor(provider: OidcProviderEntity): OidcProviderLoginInfo.IconKind =
        when (provider.providerType) {
            OidcProviderType.GITHUB -> OidcProviderLoginInfo.IconKind.GITHUB
            OidcProviderType.GOOGLE -> OidcProviderLoginInfo.IconKind.GOOGLE
            OidcProviderType.FACEBOOK -> OidcProviderLoginInfo.IconKind.FACEBOOK
            OidcProviderType.OIDC -> OidcProviderLoginInfo.IconKind.OIDC
            OidcProviderType.OAUTH2 -> OidcProviderLoginInfo.IconKind.OAUTH2
        }

    /**
     * Per-provider-type "use a different account" URL (issue #410).
     *
     * The OIDC `prompt` query parameter the upstream accepts is not
     * uniform across the provider types Plugwerk supports:
     *
     *   - OIDC / Google / Facebook → `select_account` (OIDC standard
     *     for "show the account picker"). Google uses the same value
     *     in its specific dialect.
     *   - Generic OAuth2 → `login` (OIDC standard for "force a fresh
     *     login"). Best-effort hint; not all OAuth2 providers honour it
     *     but the well-behaved ones do.
     *   - GitHub → `null`. GitHub's authorize endpoint does not implement
     *     `prompt`; the frontend renders a textual "sign out at
     *     github.com" hint instead of a clickable link.
     *
     * The value forwarded upstream is allow-listed by
     * [PromptAwareOAuth2AuthorizationRequestResolver] so this mapping
     * stays the single source of truth — operators cannot inject
     * arbitrary `prompt` values via crafted query strings.
     */
    private fun accountPickerLoginUrlFor(provider: OidcProviderEntity, loginUrl: String): String? =
        when (provider.providerType) {
            OidcProviderType.OIDC,
            OidcProviderType.GOOGLE,
            OidcProviderType.FACEBOOK,
            -> "$loginUrl?prompt=select_account"

            OidcProviderType.OAUTH2 -> "$loginUrl?prompt=login"

            OidcProviderType.GITHUB -> null
        }

    /**
     * Upstream sign-out URL for providers that do not honour `prompt`,
     * surfaced by the frontend as a textual hint instead of an in-product
     * picker (issue #410). Today only GitHub, where session termination
     * happens at github.com/logout — the user then comes back and clicks
     * the primary button to actually sign in with a different account.
     */
    private fun accountSwitchHintUrlFor(provider: OidcProviderEntity): String? = when (provider.providerType) {
        OidcProviderType.GITHUB -> "https://github.com/logout"
        else -> null
    }
}
