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

/**
 * How a [UserEntity] is authenticated. Discriminates between accounts whose
 * credentials live on the Plugwerk server (`INTERNAL`) and accounts whose
 * credentials live with an upstream identity provider (`EXTERNAL`).
 *
 * The DB enforces this with `chk_plugwerk_user_source` and a per-source
 * credential CHECK constraint:
 *
 * - [INTERNAL] rows MUST have `username` and `password_hash` populated.
 *   Email uniqueness is scoped to INTERNAL rows only.
 *
 * - [EXTERNAL] rows MUST have NULL `username` and NULL `password_hash`. The
 *   authenticating identity lives in `oidc_identity` (1:1 link via
 *   `user_id`). Two EXTERNAL rows may legitimately share an email — Plugwerk
 *   does not perform identity linking; each external subject is a distinct
 *   account by policy.
 *
 * The previous `LOCAL` / `OIDC` names mixed an implementation detail (OIDC
 * is one specific external-identity protocol) with a positional one
 * (LOCAL = "lives here"). The current names are protocol-agnostic on the
 * external side (covers OIDC, OAuth2, future SAML / LDAP) and consistent
 * with the system's invariant: external = credentials elsewhere, internal
 * = credentials on us.
 */
enum class UserSource {
    INTERNAL,
    EXTERNAL,
}
