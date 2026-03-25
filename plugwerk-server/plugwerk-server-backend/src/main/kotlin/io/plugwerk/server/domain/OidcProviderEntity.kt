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
 * `clientSecret`. Generic providers ([GENERIC_OIDC], [KEYCLOAK]) require an [issuerUri]
 * so the server can discover their JWKS endpoint at runtime.
 */
enum class OidcProviderType {
    /** Arbitrary OIDC-compliant provider (e.g. Auth0, Authentik, Dex). Requires [issuerUri]. */
    GENERIC_OIDC,

    /** Keycloak instance. Requires [issuerUri] (realm URL). */
    KEYCLOAK,

    /** GitHub OAuth2 (no OIDC discovery; uses fixed token/JWKS endpoints). */
    GITHUB,

    /** Google Identity Platform. Uses well-known OIDC discovery. */
    GOOGLE,

    /** Facebook Login. Uses well-known OAuth2 endpoints. */
    FACEBOOK,
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
 * @property clientId OAuth2 client ID registered with the provider.
 * @property clientSecretEncrypted Encrypted client secret. Use [OidcProviderService] to
 *   encrypt/decrypt — never access this field directly from controllers.
 * @property issuerUri OIDC issuer URI (required for [OidcProviderType.GENERIC_OIDC] and
 *   [OidcProviderType.KEYCLOAK]). Used for JWKS endpoint discovery.
 * @property scope Space-separated OAuth2 scopes requested during token validation.
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
