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
package io.plugwerk.server.e2e.auth

/**
 * Represents the actors (user roles / auth mechanisms) used in authorization integration tests.
 *
 * @property username The username for JWT-authenticated actors. Empty for anonymous and API key actors.
 * @property isSuperadmin Whether the actor is a system superadmin.
 * @property isApiKey Whether this actor authenticates via `X-Api-Key` header instead of JWT.
 */
enum class Actor(val username: String, val isSuperadmin: Boolean = false, val isApiKey: Boolean = false) {
    /** No credentials — unauthenticated requests. */
    ANONYMOUS(""),

    /** System superadmin with full access to everything. */
    SUPERADMIN("superadmin", isSuperadmin = true),

    /** Namespace admin for NS1 (public). */
    NS1_ADMIN("ns1-admin"),

    /** Read-only member of NS1 only. */
    NS1_READ_ONLY("ns1-readonly"),

    /** Read-only in both NS1 and NS2. */
    NS1_RO_NS2_RO("ns1ro-ns2ro"),

    /** Member in NS1, read-only in NS2. */
    NS1_MEMBER_NS2_RO("ns1member-ns2ro"),

    /** Namespace admin for NS2 (private). */
    NS2_ADMIN("ns2-admin"),

    /** Authenticated user with no namespace memberships. */
    UNRELATED("unrelated-user"),

    /** API key scoped to NS1 — authenticates via X-Api-Key header. */
    API_KEY_NS1("", isApiKey = true),

    /** API key scoped to NS2 — authenticates via X-Api-Key header. */
    API_KEY_NS2("", isApiKey = true),
}
