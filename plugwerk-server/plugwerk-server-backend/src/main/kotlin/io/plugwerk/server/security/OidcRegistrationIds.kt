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
package io.plugwerk.server.security

import io.plugwerk.server.domain.OidcProviderEntity

/**
 * Single source of truth for Spring Security `registrationId` values derived
 * from [OidcProviderEntity] rows (issue #79).
 *
 * Three places in the code base must agree on the exact same identifier:
 *
 *  - [io.plugwerk.server.controller.ConfigController] exposes it to the frontend
 *    via `/api/v1/config` so the login page can build the correct login URL.
 *  - The `DbClientRegistrationRepository` registers it as the Spring Security
 *    `ClientRegistration.registrationId`.
 *  - Spring Security's OAuth2 client filter uses it as the `{registrationId}`
 *    path segment in the redirect URI `/login/oauth2/code/{registrationId}` and
 *    the authorization start URL `/oauth2/authorization/{registrationId}`.
 *
 * Drift between these three would silently produce 404s on the authorization
 * endpoint, so the value is computed in exactly one place.
 *
 * ## Why the entity ID, not the entity name
 *
 * The provider's display name can be edited freely in the admin UI ("Keycloak"
 * → "Keycloak (production)"). If the `registrationId` were derived from the
 * name, every rename would change the OAuth2 redirect URI — silently breaking
 * the registered URIs at the upstream provider. The entity's UUID is immutable
 * and has been the row's primary key from creation, so its `registrationId` is
 * stable for the lifetime of the row.
 *
 * UUIDs are URL-safe by construction (RFC 4122 hex + hyphens), so we use them
 * verbatim — no slugging, no escaping.
 */
object OidcRegistrationIds {

    /**
     * Returns the Spring Security `registrationId` for [provider]. The provider
     * must have been persisted (its `id` must be non-null) — this is the case
     * for every row read from the database; a freshly-constructed in-memory
     * entity should never reach this helper.
     */
    fun of(provider: OidcProviderEntity): String = requireNotNull(provider.id) {
        "OidcProviderEntity must be persisted before its registrationId is derived"
    }.toString()
}
