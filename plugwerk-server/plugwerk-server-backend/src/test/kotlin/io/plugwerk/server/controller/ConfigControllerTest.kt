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

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PasswordResetRateLimitFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.security.RegisterRateLimitFilter
import io.plugwerk.server.service.VersionProvider
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@WebMvcTest(
    ConfigController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ChangePasswordRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RegisterRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordResetRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RefreshRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class ConfigControllerTest {

    @MockitoBean lateinit var jwtDecoder: JwtDecoder

    @MockitoBean lateinit var settingsService: ApplicationSettingsService

    @MockitoBean lateinit var versionProvider: VersionProvider

    @MockitoBean lateinit var oidcProviderRepository: OidcProviderRepository

    @Autowired private lateinit var mockMvc: MockMvc

    @org.junit.jupiter.api.BeforeEach
    fun setUpDefaultStubs() {
        // siteName() is called by every getServerConfig() invocation — stub a
        // sensible default so individual tests only override when they assert
        // on the value. Mockito's lenient mode is the project default for these
        // controller slices; #234.
        whenever(settingsService.siteName()).thenReturn("Plugwerk")
    }

    @Test
    fun `GET config returns upload limits, version, default timezone, and site name`() {
        whenever(settingsService.maxUploadSizeMb()).thenReturn(200)
        whenever(settingsService.defaultTimezone()).thenReturn("Europe/Berlin")
        whenever(settingsService.siteName()).thenReturn("Acme Plugin Hub")
        whenever(versionProvider.getVersion()).thenReturn("1.2.3")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(emptyList())

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.version") { value("1.2.3") }
                jsonPath("$.upload.maxFileSizeMb") { value(200) }
                jsonPath("$.general.defaultTimezone") { value("Europe/Berlin") }
                jsonPath("$.general.siteName") { value("Acme Plugin Hub") }
            }
    }

    @Test
    fun `GET config returns unknown when version is not available`() {
        whenever(settingsService.maxUploadSizeMb()).thenReturn(100)
        whenever(settingsService.defaultTimezone()).thenReturn("UTC")
        whenever(versionProvider.getVersion()).thenReturn("unknown")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(emptyList())

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.version") { value("unknown") }
            }
    }

    @Test
    fun `GET config returns empty oidcProviders list when no provider is enabled (#79)`() {
        whenever(settingsService.maxUploadSizeMb()).thenReturn(100)
        whenever(settingsService.defaultTimezone()).thenReturn("UTC")
        whenever(versionProvider.getVersion()).thenReturn("1.0.0")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(emptyList())

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.auth.oidcProviders") { isArray() }
                jsonPath("$.auth.oidcProviders.length()") { value(0) }
            }
    }

    @Test
    fun `GET config exposes enabled oidcProviders with id, name, and loginUrl (#79)`() {
        // The login page renders one button per entry. The id is the Spring
        // Security `registrationId` (provider UUID, stable across renames),
        // the loginUrl is the relative `/oauth2/authorization/{id}` path.
        val providerId = UUID.fromString("11111111-2222-3333-4444-555555555555")
        whenever(settingsService.maxUploadSizeMb()).thenReturn(100)
        whenever(settingsService.defaultTimezone()).thenReturn("UTC")
        whenever(versionProvider.getVersion()).thenReturn("1.0.0")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(
            listOf(
                OidcProviderEntity(
                    id = providerId,
                    name = "Keycloak Local",
                    providerType = OidcProviderType.OIDC,
                    enabled = true,
                    clientId = "plugwerk-local",
                    clientSecretEncrypted = "ignored-for-this-test",
                    issuerUri = "http://localhost:8081/realms/plugwerk",
                ),
            ),
        )

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.auth.oidcProviders.length()") { value(1) }
                jsonPath("$.auth.oidcProviders[0].id") { value(providerId.toString()) }
                jsonPath("$.auth.oidcProviders[0].name") { value("Keycloak Local") }
                jsonPath("$.auth.oidcProviders[0].loginUrl") {
                    value("/oauth2/authorization/$providerId")
                }
            }
    }

    @Test
    fun `GET config never exposes the issuerUri or client secret of OIDC providers (#79)`() {
        // Issuer URI and clientSecret are operator-only fields; the public
        // /config response must contain only id, name, and loginUrl per provider.
        // Pinning this as a contract test so a future careless schema change
        // cannot accidentally widen the surface.
        val providerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        whenever(settingsService.maxUploadSizeMb()).thenReturn(100)
        whenever(settingsService.defaultTimezone()).thenReturn("UTC")
        whenever(versionProvider.getVersion()).thenReturn("1.0.0")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(
            listOf(
                OidcProviderEntity(
                    id = providerId,
                    name = "Internal Keycloak",
                    providerType = OidcProviderType.OIDC,
                    enabled = true,
                    clientId = "plugwerk-internal",
                    clientSecretEncrypted = "{cipher}leaked-secret-must-not-appear",
                    issuerUri = "https://keycloak.internal.example.com/realms/internal",
                ),
            ),
        )

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.auth.oidcProviders[0].issuerUri") { doesNotExist() }
                jsonPath("$.auth.oidcProviders[0].clientId") { doesNotExist() }
                jsonPath("$.auth.oidcProviders[0].clientSecretEncrypted") { doesNotExist() }
                jsonPath("$.auth.oidcProviders[0].clientSecret") { doesNotExist() }
            }
    }

    @Test
    fun `GET config exposes per-provider iconKind for the login-page button glyph`() {
        // Closed enum the frontend uses to pick the right brand glyph.
        // Pinned across all five provider types so a refactor of the
        // domain enum cannot silently break the public surface.
        whenever(settingsService.maxUploadSizeMb()).thenReturn(100)
        whenever(settingsService.defaultTimezone()).thenReturn("UTC")
        whenever(versionProvider.getVersion()).thenReturn("1.0.0")
        val oidcId = UUID.fromString("aaaaaaaa-1111-0000-0000-000000000001")
        val googleId = UUID.fromString("aaaaaaaa-1111-0000-0000-000000000002")
        val githubId = UUID.fromString("aaaaaaaa-1111-0000-0000-000000000003")
        val facebookId = UUID.fromString("aaaaaaaa-1111-0000-0000-000000000004")
        val oauth2Id = UUID.fromString("aaaaaaaa-1111-0000-0000-000000000005")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(
            listOf(
                providerOf(oidcId, OidcProviderType.OIDC),
                providerOf(googleId, OidcProviderType.GOOGLE),
                providerOf(githubId, OidcProviderType.GITHUB),
                providerOf(facebookId, OidcProviderType.FACEBOOK),
                providerOf(oauth2Id, OidcProviderType.OAUTH2),
            ),
        )

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.auth.oidcProviders.length()") { value(5) }
                jsonPath("$.auth.oidcProviders[0].iconKind") { value("oidc") }
                jsonPath("$.auth.oidcProviders[1].iconKind") { value("google") }
                jsonPath("$.auth.oidcProviders[2].iconKind") { value("github") }
                jsonPath("$.auth.oidcProviders[3].iconKind") { value("facebook") }
                jsonPath("$.auth.oidcProviders[4].iconKind") { value("oauth2") }
            }
    }

    @Test
    fun `GET config exposes accountPickerLoginUrl=prompt=select_account for OIDC, GOOGLE, FACEBOOK (#410)`() {
        // The four OIDC-shaped provider types use the OIDC standard
        // `select_account` prompt to pop the account picker; all three
        // get the same shape of accountPickerLoginUrl.
        whenever(settingsService.maxUploadSizeMb()).thenReturn(100)
        whenever(settingsService.defaultTimezone()).thenReturn("UTC")
        whenever(versionProvider.getVersion()).thenReturn("1.0.0")
        val oidcId = UUID.fromString("11111111-0000-0000-0000-000000000001")
        val googleId = UUID.fromString("11111111-0000-0000-0000-000000000002")
        val facebookId = UUID.fromString("11111111-0000-0000-0000-000000000003")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(
            listOf(
                providerOf(oidcId, OidcProviderType.OIDC),
                providerOf(googleId, OidcProviderType.GOOGLE),
                providerOf(facebookId, OidcProviderType.FACEBOOK),
            ),
        )

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.auth.oidcProviders.length()") { value(3) }
                jsonPath("$.auth.oidcProviders[0].accountPickerLoginUrl") {
                    value("/oauth2/authorization/$oidcId?prompt=select_account")
                }
                jsonPath("$.auth.oidcProviders[1].accountPickerLoginUrl") {
                    value("/oauth2/authorization/$googleId?prompt=select_account")
                }
                jsonPath("$.auth.oidcProviders[2].accountPickerLoginUrl") {
                    value("/oauth2/authorization/$facebookId?prompt=select_account")
                }
            }
    }

    @Test
    fun `GET config exposes accountPickerLoginUrl=prompt=login for OAUTH2 generic providers (#410)`() {
        // Generic OAuth2 (post-#409) is best-effort — many but not all
        // OAuth2 providers honour `prompt=login`. The frontend renders
        // the link unconditionally; if the upstream ignores the value
        // the user sees a silent re-login (no harm).
        whenever(settingsService.maxUploadSizeMb()).thenReturn(100)
        whenever(settingsService.defaultTimezone()).thenReturn("UTC")
        whenever(versionProvider.getVersion()).thenReturn("1.0.0")
        val oauth2Id = UUID.fromString("22222222-0000-0000-0000-000000000001")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(
            listOf(providerOf(oauth2Id, OidcProviderType.OAUTH2)),
        )

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.auth.oidcProviders[0].accountPickerLoginUrl") {
                    value("/oauth2/authorization/$oauth2Id?prompt=login")
                }
            }
    }

    @Test
    fun `GET config returns null accountPickerLoginUrl for GITHUB providers (#410)`() {
        // GitHub does not implement OIDC `prompt`. We must surface this
        // as null so the frontend can render the textual hint instead of
        // a clickable link that would do nothing useful.
        whenever(settingsService.maxUploadSizeMb()).thenReturn(100)
        whenever(settingsService.defaultTimezone()).thenReturn("UTC")
        whenever(versionProvider.getVersion()).thenReturn("1.0.0")
        val githubId = UUID.fromString("33333333-0000-0000-0000-000000000001")
        whenever(oidcProviderRepository.findAllByEnabledTrue()).thenReturn(
            listOf(providerOf(githubId, OidcProviderType.GITHUB)),
        )

        mockMvc.get("/api/v1/config")
            .andExpect {
                status { isOk() }
                // OpenAPI generates `accountPickerLoginUrl` as a nullable
                // optional field; serialised as either `null` or absent
                // depending on Jackson defaults. Either is correct from
                // the schema's perspective; assert "not present as a
                // string value".
                jsonPath("$.auth.oidcProviders[0].id") { value(githubId.toString()) }
                jsonPath("$.auth.oidcProviders[0].accountPickerLoginUrl") {
                    value(null as String?)
                }
            }
    }

    private fun providerOf(id: UUID, type: OidcProviderType) = OidcProviderEntity(
        id = id,
        name = "Test ${type.name}",
        providerType = type,
        enabled = true,
        clientId = "test-client",
        clientSecretEncrypted = "{cipher}ignored",
        issuerUri = "https://example.test/realms/x",
    )
}
