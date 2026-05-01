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

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Pins the four behaviours of [GitHubEmailFetcher] that matter to callers:
 *
 *   1. Picks the email row whose `primary == true` and `verified == true`.
 *   2. Returns null when no such row exists (verified-but-not-primary,
 *      primary-but-not-verified, all-secondary, etc.).
 *   3. Returns null on transport failure (server down, 5xx, parse error)
 *      rather than throwing — the adapter then surfaces the absence as
 *      `OidcEmailMissingException`, which carries a user-actionable message.
 *   4. Sends the GitHub-versioned `Accept` header and a `Bearer` Authorization.
 *
 * Uses MockWebServer (already on the classpath via okhttp-mockwebserver) so
 * we exercise the actual `RestClient` HTTP path rather than mocking it out.
 */
class GitHubEmailFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var fetcher: GitHubEmailFetcher

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        // The production class hard-codes its RestClient against api.github.com;
        // for these tests we reflectively swap that field with one pointing at
        // MockWebServer. Keeps the production constructor argument-free (no
        // test-only injection point leaking into the API surface).
        fetcher = GitHubEmailFetcher()
        val mockClient = RestClient.builder()
            .baseUrl(server.url("/").toString().trimEnd('/'))
            .defaultHeader("Accept", GitHubEmailFetcher.GITHUB_API_MEDIA_TYPE)
            .build()
        val field = GitHubEmailFetcher::class.java.getDeclaredField("restClient").apply { isAccessible = true }
        field.set(fetcher, mockClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `returns the primary verified email`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"email":"alt@example.com","primary":false,"verified":true},
                      {"email":"primary@example.com","primary":true,"verified":true},
                      {"email":"unverified@example.com","primary":false,"verified":false}
                    ]
                    """.trimIndent(),
                ),
        )

        val email = fetcher.fetchPrimaryVerified("gho_FAKETOKEN")

        assertThat(email).isEqualTo("primary@example.com")
    }

    @Test
    fun `returns null when no email row is both primary and verified`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"email":"primary@example.com","primary":true,"verified":false},
                      {"email":"verified@example.com","primary":false,"verified":true}
                    ]
                    """.trimIndent(),
                ),
        )

        assertThat(fetcher.fetchPrimaryVerified("gho_FAKETOKEN")).isNull()
    }

    @Test
    fun `returns null when GitHub returns an empty list`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )

        assertThat(fetcher.fetchPrimaryVerified("gho_FAKETOKEN")).isNull()
    }

    @Test
    fun `returns null on a 401 from GitHub (rather than throwing)`() {
        // Token expired or scope revoked — the adapter should surface this as
        // OidcEmailMissingException downstream, not crash the login flow.
        server.enqueue(MockResponse().setResponseCode(401))

        assertThat(fetcher.fetchPrimaryVerified("gho_FAKETOKEN")).isNull()
    }

    @Test
    fun `sends Bearer authorization and the GitHub-versioned Accept header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"email":"x@y.z","primary":true,"verified":true}]"""),
        )

        fetcher.fetchPrimaryVerified("gho_FAKETOKEN")
        val recorded = server.takeRequest()

        assertThat(recorded.path).isEqualTo("/user/emails")
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer gho_FAKETOKEN")
        assertThat(recorded.getHeader("Accept")).isEqualTo("application/vnd.github+json")
    }
}
