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
package io.plugwerk.server.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Type of OIDC / OAuth2 provider.
 *
 * Pre-configured providers ([GITHUB], [GOOGLE], [FACEBOOK]) have their well-known
 * endpoints embedded in the server — the admin only needs to supply `clientId` and
 * `clientSecret`. The generic [OIDC] type covers any standards-compliant
 * OpenID Connect provider (Keycloak, Authentik, Auth0, Dex, …) and requires an
 * [issuerUri] so the server can discover its JWKS endpoint at runtime.
 */
enum class OidcProviderType {
    /** Any OIDC-compliant provider (Keycloak, Authentik, Auth0, Dex, …). Requires [issuerUri]. */
    OIDC,

    /** GitHub OAuth2 (no OIDC discovery; uses fixed token/JWKS endpoints). */
    GITHUB,

    /** Google Identity Platform. Uses well-known OIDC discovery. */
    GOOGLE,

    /** Facebook Login. Uses well-known OAuth2 endpoints. */
    FACEBOOK,

    /**
     * Generic OAuth2 provider — operator supplies authorization-uri,
     * token-uri, user-info-uri, and (optionally) JWK-set-uri plus the
     * attribute names to read subject / email / displayName from. Use this
     * when the target provider is OAuth2-conformant but neither OIDC-
     * discoverable nor one of the four hard-wired vendors.
     *
     * Example operator scenarios:
     *   - GitLab (cloud or self-hosted)
     *   - Bitbucket
     *   - Discord (also OIDC-discoverable, but workable here too)
     *   - Custom enterprise IdPs without `/.well-known/openid-configuration`
     */
    OAUTH2,
}

/**
 * JPA entity representing an externally configured OIDC / OAuth2 provider.
 *
 * Plugwerk acts as an **OAuth2 Resource Server** for these providers: it validates
 * `Authorization: Bearer <token>` JWTs that were issued by an enabled provider.
 * (The full browser-based Authorization Code Flow is tracked separately in issue #79.)
 *
 * **Security note:** [clientSecretEncrypted] stores the client secret encrypted with
 * Spring's `TextEncryptor` using the key from `plugwerk.auth.encryption-key`. The
 * plain-text secret is never logged or returned via the API.
 *
 * **Defaults:** [enabled] is `false` — a newly created provider must be explicitly
 * activated by an administrator. This ensures OIDC support is opt-in.
 *
 * **Data model:** Maps to the `oidc_provider` table.
 *
 * @property id Primary key, UUIDv7.
 * @property name Human-readable display name shown in the admin UI (e.g. `Company Keycloak`).
 * @property providerType Determines which well-known endpoints to use. See [OidcProviderType].
 * @property enabled When `false`, this provider is ignored by the JWT decoder registry.
 * @property clientId OAuth2 client ID registered with the provider. Also used as the
 *   expected value of the `aud` claim in incoming OIDC tokens — a token whose `aud`
 *   claim does not contain this client_id is rejected by
 *   [io.plugwerk.server.security.OidcJwtValidators]. Operators who set `clientId` to
 *   a human-readable name rather than the provider-registered audience string will
 *   see every token rejected.
 * @property clientSecretEncrypted Encrypted client secret. Use [OidcProviderService] to
 *   encrypt/decrypt — never access this field directly from controllers.
 * @property issuerUri OIDC issuer URI (required for [OidcProviderType.OIDC]). Used for
 *   JWKS endpoint discovery. Ignored for the three vendor types
 *   ([OidcProviderType.GITHUB], [OidcProviderType.GOOGLE], [OidcProviderType.FACEBOOK])
 *   which use hardcoded canonical issuers — see
 *   [io.plugwerk.server.security.OidcJwtValidators].
 * @property scope Space-separated OAuth2 scopes requested during token validation.
 *   For [OidcProviderType.GITHUB] and [OidcProviderType.FACEBOOK] this value is
 *   ignored — the right scopes are hardcoded inside `DbClientRegistrationRepository`
 *   for those provider types.
 * @property authorizationUri OAuth2 authorize-endpoint URL. Required when
 *   [providerType] is [OidcProviderType.OAUTH2]; ignored otherwise.
 * @property tokenUri OAuth2 token-endpoint URL. Required when [providerType] is
 *   [OidcProviderType.OAUTH2]; ignored otherwise.
 * @property userInfoUri OAuth2 user-info endpoint URL — Plugwerk fetches the
 *   subject / email / displayName from here after token exchange. Required
 *   when [providerType] is [OidcProviderType.OAUTH2]; ignored otherwise.
 * @property jwkSetUri Optional JWK Set URL for providers that issue JWT access
 *   tokens (so the resource-server side can validate them). Only meaningful for
 *   [OidcProviderType.OAUTH2]. Leave `null` for opaque-token providers.
 * @property subjectAttribute Name of the user-info JSON key that carries the
 *   stable user identifier. `null` means the application default (`sub`). Only
 *   read when [providerType] is [OidcProviderType.OAUTH2].
 * @property emailAttribute Name of the user-info JSON key that carries the user's
 *   email address. `null` means the application default (`email`). Only read when
 *   [providerType] is [OidcProviderType.OAUTH2].
 * @property displayNameAttribute Name of the user-info JSON key that carries the
 *   display name. `null` means the application default (`name`). Only read when
 *   [providerType] is [OidcProviderType.OAUTH2].
 * @property createdAt Creation timestamp (immutable).
 * @property updatedAt Last modification timestamp.
 */
@Entity
@Table(name = "oidc_provider")
class OidcProviderEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 50)
    var providerType: OidcProviderType,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = false,

    @Column(name = "client_id", nullable = false, length = 255)
    var clientId: String,

    @Column(name = "client_secret_encrypted", nullable = false, length = 1024)
    var clientSecretEncrypted: String,

    @Column(name = "issuer_uri", length = 2048)
    var issuerUri: String? = null,

    @Column(name = "scope", nullable = false, length = 255)
    var scope: String = "openid email profile",

    @Column(name = "authorization_uri", columnDefinition = "text")
    var authorizationUri: String? = null,

    @Column(name = "token_uri", columnDefinition = "text")
    var tokenUri: String? = null,

    @Column(name = "user_info_uri", columnDefinition = "text")
    var userInfoUri: String? = null,

    @Column(name = "jwk_set_uri", columnDefinition = "text")
    var jwkSetUri: String? = null,

    @Column(name = "subject_attribute", columnDefinition = "text")
    var subjectAttribute: String? = null,

    @Column(name = "email_attribute", columnDefinition = "text")
    var emailAttribute: String? = null,

    @Column(name = "display_name_attribute", columnDefinition = "text")
    var displayNameAttribute: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
