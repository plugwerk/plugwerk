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

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.DbClientRegistrationRepository
import io.plugwerk.server.security.OidcProviderRegistry
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

/**
 * Patch payload for [OidcProviderService.update]. Each non-null field is
 * applied; null means "leave unchanged" (PATCH semantics). [clientSecretPlaintext]
 * is additionally treated as "leave unchanged" when blank, so a UI form that
 * submits an empty password input does not accidentally null out the stored
 * encrypted secret. `providerType` is intentionally absent from this type —
 * see the schema description for the rationale.
 */
data class OidcProviderPatch(
    val enabled: Boolean? = null,
    val name: String? = null,
    val clientId: String? = null,
    val clientSecretPlaintext: String? = null,
    val issuerUri: String? = null,
    val scope: String? = null,
    val authorizationUri: String? = null,
    val tokenUri: String? = null,
    val userInfoUri: String? = null,
    val jwkSetUri: String? = null,
    val subjectAttribute: String? = null,
    val emailAttribute: String? = null,
    val displayNameAttribute: String? = null,
)

@Service
@Transactional
class OidcProviderService(
    private val oidcProviderRepository: OidcProviderRepository,
    private val oidcProviderRegistry: OidcProviderRegistry,
    private val dbClientRegistrationRepository: DbClientRegistrationRepository,
    private val oidcIdentityRepository: OidcIdentityRepository,
    private val userRepository: UserRepository,
    private val textEncryptor: TextEncryptor,
) {

    private val log = LoggerFactory.getLogger(OidcProviderService::class.java)

    /**
     * Rebuilds both registries that depend on the enabled-providers list:
     *   - [OidcProviderRegistry]            — JWKS-based JwtDecoders for the
     *     resource-server side (verify incoming bearer tokens, #77).
     *   - [DbClientRegistrationRepository]  — Spring Security ClientRegistrations
     *     for the browser login flow (#79).
     * Either-or refreshes drift the two registries against the database; calling
     * them together from a single helper guarantees they stay in lockstep.
     */
    private fun refreshAllRegistries() {
        oidcProviderRegistry.refresh()
        dbClientRegistrationRepository.refresh()
    }

    @Transactional(readOnly = true)
    fun findAll(): List<OidcProviderEntity> = oidcProviderRepository.findAll()

    @Transactional(readOnly = true)
    fun findById(id: UUID): OidcProviderEntity =
        oidcProviderRepository.findById(id).orElseThrow { EntityNotFoundException("OidcProvider", id.toString()) }

    fun create(
        name: String,
        providerType: OidcProviderType,
        clientId: String,
        clientSecret: String,
        issuerUri: String?,
        scope: String,
        authorizationUri: String? = null,
        tokenUri: String? = null,
        userInfoUri: String? = null,
        jwkSetUri: String? = null,
        subjectAttribute: String? = null,
        emailAttribute: String? = null,
        displayNameAttribute: String? = null,
    ): OidcProviderEntity {
        // OAUTH2_GENERIC requires the three core endpoint URIs at create time.
        // The four URI columns are nullable in the schema (existing rows for
        // OIDC/GOOGLE/GITHUB/FACEBOOK don't need them) so service-layer
        // validation is the only line of defence — without it a half-configured
        // generic row reaches DbClientRegistrationRepository which would log+
        // skip it, leaving the operator wondering why their new provider does
        // not appear on the login page.
        if (providerType == OidcProviderType.OAUTH2_GENERIC) {
            requireValidHttpUri(authorizationUri, "authorizationUri", required = true)
            requireValidHttpUri(tokenUri, "tokenUri", required = true)
            requireValidHttpUri(userInfoUri, "userInfoUri", required = true)
            requireValidHttpUri(jwkSetUri, "jwkSetUri", required = false)
        }
        val provider = oidcProviderRepository.save(
            OidcProviderEntity(
                name = name,
                providerType = providerType,
                clientId = clientId,
                clientSecretEncrypted = textEncryptor.encrypt(clientSecret),
                issuerUri = issuerUri,
                scope = scope,
                enabled = false,
                authorizationUri = authorizationUri?.trim()?.takeIf { it.isNotEmpty() },
                tokenUri = tokenUri?.trim()?.takeIf { it.isNotEmpty() },
                userInfoUri = userInfoUri?.trim()?.takeIf { it.isNotEmpty() },
                jwkSetUri = jwkSetUri?.trim()?.takeIf { it.isNotEmpty() },
                subjectAttribute = subjectAttribute?.trim()?.takeIf { it.isNotEmpty() },
                emailAttribute = emailAttribute?.trim()?.takeIf { it.isNotEmpty() },
                displayNameAttribute = displayNameAttribute?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        return provider
    }

    /**
     * Validates that [value] (when present, or always when [required]) parses as
     * an http(s) URI. Mirrors the issuerUri rule used elsewhere — every URI
     * field on an OIDC/OAuth2 provider must be a real URL the server can later
     * hand to a browser or HTTP client without surprises.
     */
    private fun requireValidHttpUri(value: String?, fieldName: String, required: Boolean) {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) {
            require(!required) { "$fieldName is required for provider type OAUTH2_GENERIC" }
            return
        }
        val parsed = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("$fieldName is not a valid URI: ${e.message}")
        }
        require(parsed.scheme in setOf("http", "https")) {
            "$fieldName must use http or https scheme"
        }
    }

    /**
     * Single transactional patch path for an existing OIDC provider. Replaces
     * the previous setEnabled / updateClientSecret split so that a multi-field
     * UI patch produces exactly one row write and exactly one registry refresh.
     *
     * Validation rules (each applied only when the corresponding patch field
     * is non-null):
     *   - [OidcProviderPatch.name]: trimmed, must be 1..255 chars after trim.
     *   - [OidcProviderPatch.clientId]: trimmed, 1..255 chars. Note that
     *     changing this on an enabled provider invalidates every previously
     *     issued access token (different `aud` claim) — the UI must warn the
     *     operator before submitting; the service does not block.
     *   - [OidcProviderPatch.clientSecretPlaintext]: minimum 8 chars when set
     *     (the upstream provider enforces real strength). Blank is treated as
     *     "do not change" so an empty password input cannot accidentally null
     *     out the stored secret.
     *   - [OidcProviderPatch.issuerUri]: must parse as a valid http(s) URI.
     *     Reachability is verified lazily by [refreshAllRegistries] — failures
     *     there are logged-and-skipped, NOT propagated as a 4xx, so operators
     *     can patch the URI ahead of an upstream cutover.
     *   - [OidcProviderPatch.scope]: trimmed, non-blank. For [OidcProviderType.OIDC]
     *     the resulting scope set must include `openid`, otherwise the patch is
     *     rejected — an OIDC client without the openid scope cannot complete
     *     the OIDC flow at all, so this is a hard error not a warning.
     *
     * `refreshAllRegistries()` runs exactly once at the end, regardless of how
     * many fields changed. Both the resource-server [OidcProviderRegistry] and
     * the browser-flow [DbClientRegistrationRepository] pick up the changes
     * without a server restart.
     *
     * The provider's UUID is the source of `registrationId` (see
     * `OidcRegistrationIds`), so the redirect URI registered at the upstream
     * provider stays valid across name/clientId/issuer/scope edits.
     */
    fun update(id: UUID, patch: OidcProviderPatch): OidcProviderEntity {
        val provider = findById(id)

        patch.enabled?.let { provider.enabled = it }

        patch.name?.let {
            val trimmed = it.trim()
            require(trimmed.isNotEmpty()) { "name must not be blank" }
            require(trimmed.length <= 255) { "name must be at most 255 characters" }
            provider.name = trimmed
        }

        patch.clientId?.let {
            val trimmed = it.trim()
            require(trimmed.isNotEmpty()) { "clientId must not be blank" }
            require(trimmed.length <= 255) { "clientId must be at most 255 characters" }
            provider.clientId = trimmed
        }

        patch.clientSecretPlaintext?.takeIf { it.isNotBlank() }?.let {
            require(it.length >= 8) { "clientSecret must be at least 8 characters" }
            provider.clientSecretEncrypted = textEncryptor.encrypt(it)
        }

        patch.issuerUri?.let {
            val trimmed = it.trim()
            require(trimmed.isNotEmpty()) { "issuerUri must not be blank" }
            val parsed = try {
                URI(trimmed)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("issuerUri is not a valid URI: ${e.message}")
            }
            require(parsed.scheme in setOf("http", "https")) {
                "issuerUri must use http or https scheme"
            }
            provider.issuerUri = trimmed
        }

        patch.scope?.let {
            val trimmed = it.trim()
            require(trimmed.isNotEmpty()) { "scope must not be blank" }
            if (provider.providerType == OidcProviderType.OIDC) {
                val tokens = trimmed.split(' ').filter { token -> token.isNotEmpty() }
                require("openid" in tokens) {
                    "scope for OIDC providers must include 'openid'"
                }
            }
            provider.scope = trimmed
        }

        // OAUTH2_GENERIC URI/attribute fields. Each is independently optional
        // in the patch (PATCH semantics — null means leave unchanged) but the
        // three core endpoint URIs cannot be cleared back to null on a
        // OAUTH2_GENERIC row: doing so would brick the row's browser-flow
        // wiring with no recovery short of re-editing. To remove an
        // OAUTH2_GENERIC provider, delete and recreate it.
        applyOAuth2GenericUriPatch(provider, patch.authorizationUri, "authorizationUri") {
            provider.authorizationUri = it
        }
        applyOAuth2GenericUriPatch(provider, patch.tokenUri, "tokenUri") {
            provider.tokenUri = it
        }
        applyOAuth2GenericUriPatch(provider, patch.userInfoUri, "userInfoUri") {
            provider.userInfoUri = it
        }
        // jwkSetUri is opt-in (resource-server validation) — clearing it back
        // is allowed because the operator is signalling "this provider issues
        // opaque tokens, skip resource-server validation".
        patch.jwkSetUri?.let {
            val trimmed = it.trim()
            require(trimmed.isNotEmpty()) { "jwkSetUri must not be blank — to disable, omit the field" }
            requireValidHttpUri(trimmed, "jwkSetUri", required = false)
            provider.jwkSetUri = trimmed
        }

        applyAttributeNamePatch(patch.subjectAttribute, "subjectAttribute") {
            provider.subjectAttribute = it
        }
        applyAttributeNamePatch(patch.emailAttribute, "emailAttribute") {
            provider.emailAttribute = it
        }
        applyAttributeNamePatch(patch.displayNameAttribute, "displayNameAttribute") {
            provider.displayNameAttribute = it
        }

        val saved = oidcProviderRepository.save(provider)
        refreshAllRegistries()
        return saved
    }

    private fun applyOAuth2GenericUriPatch(
        provider: OidcProviderEntity,
        value: String?,
        fieldName: String,
        setter: (String) -> Unit,
    ) {
        if (value == null) return
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) {
            "$fieldName must not be blank — clearing the URI on a OAUTH2_GENERIC row " +
                "would break the browser-flow wiring; delete and recreate the provider instead"
        }
        require(provider.providerType == OidcProviderType.OAUTH2_GENERIC) {
            "$fieldName is only meaningful on OAUTH2_GENERIC providers (this is a ${provider.providerType})"
        }
        requireValidHttpUri(trimmed, fieldName, required = true)
        setter(trimmed)
    }

    private fun applyAttributeNamePatch(value: String?, fieldName: String, setter: (String) -> Unit) {
        if (value == null) return
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "$fieldName must not be blank" }
        setter(trimmed)
    }

    /**
     * Deletes an OIDC provider, applying issue #351's Politik C: any
     * `plugwerk_user` rows that authenticated through this provider get
     * `enabled = false` BEFORE the SQL cascade wipes their `oidc_identity`
     * rows. The user records survive for audit purposes — operators who
     * later want to permanently delete those users can do so via the admin
     * UI; until then the disabled flag prevents any new login attempt.
     */
    fun delete(id: UUID) {
        if (!oidcProviderRepository.existsById(id)) {
            throw EntityNotFoundException("OidcProvider", id.toString())
        }
        val orphanedUserIds = oidcIdentityRepository.findAllByOidcProviderId(id)
            .mapNotNull { it.user.id }
        if (orphanedUserIds.isNotEmpty()) {
            val disabled = userRepository.disableAll(orphanedUserIds)
            log.warn(
                "Disabled {} user(s) orphaned by deletion of OIDC provider {} (Politik C, issue #351)",
                disabled,
                id,
            )
        }
        // The CASCADE on oidc_identity.oidc_provider_id removes the identity rows
        // here. plugwerk_user rows stay (now flagged enabled=false above).
        oidcProviderRepository.deleteById(id)
        refreshAllRegistries()
    }
}
