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
import io.plugwerk.server.security.ResolvedPrincipal
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * OIDC-side counterpart of [UserService]. Materialises Plugwerk identities for
 * OIDC subjects on the first successful callback and bumps
 * `plugwerk_user.last_login_at` (#367) on every subsequent one (issue #351,
 * replaces PR #350's JIT-`plugwerk_user` hack).
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
     * Resolves or creates the [UserEntity] for an OIDC / OAuth2 callback.
     * Returns the resolved user — never null. Side effects:
     *
     *   - **Existing identity** → bumps `plugwerk_user.last_login_at` via
     *     [UserService.bumpLastLogin] (#367), returns the linked user.
     *   - **New identity** → creates a fresh [UserEntity] with `source = OIDC`
     *     plus the matching [OidcIdentityEntity] row, returns the new user.
     *
     * @param provider Source OIDC provider — the row is required to have a
     *   non-null `id` (already persisted at the time of the call).
     * @param principal Provider-agnostic snapshot produced by a
     *   [io.plugwerk.server.security.ProviderPrincipalAdapter] (#357 Phase 0).
     *   `principal.email == null` triggers [OidcEmailMissingException] —
     *   `plugwerk_user.email` is `NOT NULL` (migration 0017, ADR-0029 §5).
     */
    fun upsertOnLogin(provider: OidcProviderEntity, principal: ResolvedPrincipal): UserEntity {
        val providerId = requireNotNull(provider.id) {
            "OidcProviderEntity must be persisted before upsertOnLogin"
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val existing = oidcIdentityRepository
            .findByOidcProviderIdAndSubject(providerId, principal.subject)
            .orElse(null)
        if (existing != null) {
            return userService.bumpLastLogin(existing.user.id!!, now)
        }
        return createNewIdentityAndUser(provider, principal, now)
    }

    private fun createNewIdentityAndUser(
        provider: OidcProviderEntity,
        principal: ResolvedPrincipal,
        loginAt: OffsetDateTime,
    ): UserEntity {
        val email = principal.email?.trim()?.takeIf { it.isNotBlank() }
            ?: throw OidcEmailMissingException(provider.name)
        // displayName falls back to subject as a last-resort visible identifier when
        // the adapter could not produce one (e.g. a provider that returns no name claim).
        val displayName = principal.displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: principal.subject
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
                subject = principal.subject,
                user = user,
            ),
        )
        log.info(
            "Provisioned new OIDC identity: provider={} sub={} user_id={}",
            provider.name,
            principal.subject,
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
