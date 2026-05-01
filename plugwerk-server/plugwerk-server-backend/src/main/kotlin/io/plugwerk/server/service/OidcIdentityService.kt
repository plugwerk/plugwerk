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
package io.plugwerk.server.service

import io.plugwerk.server.domain.OidcIdentityEntity
import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * OIDC-side counterpart of [UserService]. Materialises Plugwerk identities for
 * OIDC subjects on the first successful callback and refreshes `lastLoginAt`
 * on every subsequent one (issue #351, replaces PR #350's JIT-`plugwerk_user`
 * hack).
 *
 * **No identity linking by policy.** A given `(provider, subject)` pair always
 * maps to exactly one [UserEntity]; the schema enforces this with
 * `UNIQUE(oidc_provider_id, subject)` and `UNIQUE(user_id)` on `oidc_identity`.
 * Same human across multiple providers ⇒ multiple unrelated [UserEntity] rows.
 */
@Service
@Transactional
class OidcIdentityService(
    private val oidcIdentityRepository: OidcIdentityRepository,
    private val userRepository: UserRepository,
    private val userService: UserService,
) {

    private val log = LoggerFactory.getLogger(OidcIdentityService::class.java)

    /**
     * Resolves or creates the [UserEntity] for an OIDC callback. Returns the
     * resolved user — never null. Side effects:
     *
     *   - **Existing identity** → bumps `lastLoginAt`, returns the linked user.
     *   - **New identity** → creates a fresh [UserEntity] with `source = OIDC`
     *     plus the matching [OidcIdentityEntity] row, returns the new user.
     *
     * @param provider Source OIDC provider — the row is required to have a
     *   non-null `id` (already persisted at the time of the call).
     * @param subject Upstream `sub` claim — provider-local identifier.
     * @param claims Decoded ID-token claims. `email` must be present and
     *   non-blank — Plugwerk requires an email for every account (see
     *   [OidcEmailMissingException] callers and the `email NOT NULL`
     *   constraint introduced in migration 0017).
     */
    fun upsertOnLogin(provider: OidcProviderEntity, subject: String, claims: Map<String, Any>): UserEntity {
        val providerId = requireNotNull(provider.id) {
            "OidcProviderEntity must be persisted before upsertOnLogin"
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val existing = oidcIdentityRepository.findByOidcProviderIdAndSubject(providerId, subject).orElse(null)
        if (existing != null) {
            // Two distinct timestamps end up equal in the happy path but track different
            // things conceptually: the binding-level last_login_at on oidc_identity (used
            // later for stale-binding cleanup) and the user-level last_login_at on
            // plugwerk_user (issue #367, surfaced in UserDto).
            existing.lastLoginAt = now
            return userService.bumpLastLogin(existing.user.id!!, now)
        }
        return createNewIdentityAndUser(provider, subject, claims, now)
    }

    private fun createNewIdentityAndUser(
        provider: OidcProviderEntity,
        subject: String,
        claims: Map<String, Any>,
        loginAt: OffsetDateTime,
    ): UserEntity {
        val email = (claims["email"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: throw OidcEmailMissingException(provider.name)
        // displayName precedence:
        //   1. `name` claim (full name, what most IdPs render in their own UIs)
        //   2. `preferred_username` claim (handle / login alias)
        //   3. `subject` itself, as a last-resort visible identifier
        val displayName = (claims["name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: (claims["preferred_username"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: subject
        // First OIDC login is still a login (issue #367) — set lastLoginAt at
        // creation so the user surfaces as "active" immediately rather than as
        // "never logged in" until the second callback.
        val user = userRepository.save(
            UserEntity(
                displayName = displayName,
                email = email,
                source = UserSource.OIDC,
                username = null,
                passwordHash = null,
                enabled = true,
                passwordChangeRequired = false,
                isSuperadmin = false,
                lastLoginAt = loginAt,
            ),
        )
        oidcIdentityRepository.save(
            OidcIdentityEntity(
                oidcProvider = provider,
                subject = subject,
                user = user,
            ),
        )
        log.info(
            "Provisioned new OIDC identity: provider={} sub={} user_id={}",
            provider.name,
            subject,
            user.id,
        )
        return user
    }
}

/**
 * Thrown when an OIDC callback delivers no `email` claim. Plugwerk requires
 * an email per account (issue #351 — `plugwerk_user.email` is NOT NULL); the
 * IdP must be configured to include `email` in the returned scope.
 */
class OidcEmailMissingException(providerName: String) :
    RuntimeException(
        "OIDC provider '$providerName' returned no `email` claim — configure the IdP to include " +
            "`email` in the requested scope (default scope is 'openid email profile').",
    )
