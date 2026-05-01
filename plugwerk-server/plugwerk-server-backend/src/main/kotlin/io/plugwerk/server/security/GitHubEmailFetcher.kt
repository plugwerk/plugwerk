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

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * GitHub-specific helper that resolves a user's primary verified email out
 * of band, after the OAuth2 callback has supplied an access token but the
 * `/user` response did not include the email (the GitHub user has set their
 * primary email to private, so it is only reachable via `/user/emails` with
 * the `user:email` scope).
 *
 * Scoped to the GitHub adapter (#357 phase 3) — generic OIDC providers
 * surface email in the ID token claims and never need this path.
 *
 * Implementation notes:
 *   - The base URL is hard-coded to `https://api.github.com`. GitHub
 *     Enterprise Server has a different base; if Plugwerk ever needs to
 *     support that, this base URL becomes configurable on the provider entity.
 *   - The `Accept: application/vnd.github+json` header is the GitHub-recommended
 *     versioned content type. Sending the older `application/json` would still
 *     work but binds us to whatever GitHub's "v3" default is at the time of
 *     the call rather than the explicit versioned media type.
 *   - No retries. A failure here resolves to "no email" downstream, which the
 *     adapter then surfaces as `OidcEmailMissingException` — that path already
 *     has a user-actionable message (see #357 phase 2), so adding retry
 *     complexity would only mask a legitimate "user has no public verified
 *     primary email" condition.
 */
@Component
class GitHubEmailFetcher {

    private val log = LoggerFactory.getLogger(GitHubEmailFetcher::class.java)

    // Spring Boot does not autoconfigure a RestClient.Builder bean in our
    // dependency set, so building the client directly here avoids an
    // additional bean wiring step. Tests swap this field via reflection to
    // point at a MockWebServer — see GitHubEmailFetcherTest.
    private val restClient: RestClient = RestClient.builder()
        .baseUrl(GITHUB_API_BASE_URL)
        .defaultHeader(HttpHeaders.ACCEPT, GITHUB_API_MEDIA_TYPE)
        .build()

    /**
     * Returns the user's primary verified email address, or `null` if no such
     * row exists or the call fails. Caller (the adapter) is responsible for
     * translating a `null` return into the appropriate downstream behaviour
     * (`OidcEmailMissingException`).
     *
     * @param accessToken The bearer token Spring Security captured from the
     *   OAuth2 token-exchange response. Must carry the `user:email` scope.
     */
    fun fetchPrimaryVerified(accessToken: String): String? {
        val emails: List<GitHubEmail>? = runCatching {
            restClient.get()
                .uri("/user/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(EMAIL_LIST_TYPE)
        }.onFailure { ex ->
            log.warn("GitHub /user/emails fetch failed: {}", ex.message)
        }.getOrNull()

        return emails
            ?.firstOrNull { it.primary && it.verified }
            ?.email
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Subset of GitHub's `/user/emails` response shape. We only deserialize
     * the three fields we actually need; Jackson ignores anything else
     * (including `visibility`, which is not relevant to our primary-verified
     * lookup).
     */
    data class GitHubEmail(val email: String, val primary: Boolean, val verified: Boolean)

    companion object {
        const val GITHUB_API_BASE_URL = "https://api.github.com"
        const val GITHUB_API_MEDIA_TYPE = "application/vnd.github+json"

        private val EMAIL_LIST_TYPE = object : ParameterizedTypeReference<List<GitHubEmail>>() {}
    }
}
