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
 * How a [UserEntity] is authenticated. Discriminates between local
 * password-based accounts and accounts sourced from an external OIDC
 * provider (issue #351).
 *
 * The DB enforces this with `chk_plugwerk_user_source` and a per-source
 * credential CHECK constraint:
 *
 * - [LOCAL] rows MUST have `username` and `password_hash` populated.
 *   Email uniqueness is scoped to LOCAL rows only.
 *
 * - [OIDC] rows MUST have NULL `username` and NULL `password_hash`. The
 *   authenticating identity lives in `oidc_identity` (1:1 link via
 *   `user_id`). Two OIDC rows may legitimately share an email — Plugwerk
 *   does not perform identity linking; each OIDC subject is a distinct
 *   account by policy.
 */
enum class UserSource {
    LOCAL,
    OIDC,
}
