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

import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream

/**
 * Tests catalog endpoint authorization: plugin visibility, release visibility,
 * download access, tag visibility, and plugins.json feed per actor and namespace.
 */
class CatalogEndpointAuthzTest : AbstractAuthorizationTest() {

    // ------------------------------------------------------------------ //
    // GET /namespaces/{ns}/plugins — Plugin list visibility               //
    // ------------------------------------------------------------------ //

    @Nested
    inner class PluginListVisibility {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.CatalogEndpointAuthzTest#pluginVisibilityNs1")
        fun `plugin visibility in NS1 (public)`(case: PluginVisibilityCase) {
            val result = mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/plugins?size=50").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))

            if (case.expectedStatus == 200) {
                val response = result.andReturn()
                val body = objectMapper.readValue(response.response.contentAsString, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                val content = body["content"] as List<Map<String, Any>>
                val returnedIds = content.map { it["pluginId"] as String }.toSet()

                assert(returnedIds == case.expectedPluginIds) {
                    "Actor ${case.actor} on ${case.namespace}: expected ${case.expectedPluginIds}, got $returnedIds"
                }

                // Check hasDraftOnly flag for draft-only plugin if it's visible
                if (case.hasDraftOnlyVisible) {
                    val draftOnlyPlugin = content.find { (it["pluginId"] as String).endsWith("-draftonly") }
                    assert(draftOnlyPlugin != null) { "Draft-only plugin should be visible for ${case.actor}" }
                    assert(draftOnlyPlugin!!["hasDraftOnly"] == true) {
                        "hasDraftOnly should be true for draft-only plugin, actor ${case.actor}"
                    }
                }
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.CatalogEndpointAuthzTest#pluginVisibilityNs2")
        fun `plugin visibility in NS2 (private)`(case: PluginVisibilityCase) {
            val result = mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/plugins?size=50").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))

            if (case.expectedStatus == 200) {
                val response = result.andReturn()
                val body = objectMapper.readValue(response.response.contentAsString, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                val content = body["content"] as List<Map<String, Any>>
                val returnedIds = content.map { it["pluginId"] as String }.toSet()

                assert(returnedIds == case.expectedPluginIds) {
                    "Actor ${case.actor} on ${case.namespace}: expected ${case.expectedPluginIds}, got $returnedIds"
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // GET /namespaces/{ns}/plugins/{pluginId} — Single plugin             //
    // ------------------------------------------------------------------ //

    @Nested
    inner class SinglePluginAccess {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.CatalogEndpointAuthzTest#singlePluginCases")
        fun `single plugin access`(case: SinglePluginCase) {
            mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/plugins/${case.pluginId}").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // GET /namespaces/{ns}/plugins/{pluginId}/releases — Release list     //
    // ------------------------------------------------------------------ //

    @Nested
    inner class ReleaseVisibility {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.plugwerk.server.e2e.auth.CatalogEndpointAuthzTest#releaseVisibilityCases")
        fun `release visibility per actor`(case: ReleaseVisibilityCase) {
            val result = mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/plugins/${case.pluginId}/releases?size=50").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))

            if (case.expectedStatus == 200) {
                val response = result.andReturn()
                val body = objectMapper.readValue(response.response.contentAsString, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                val content = body["content"] as List<Map<String, Any>>
                val returnedVersions = content.map { it["version"] as String }.toSet()

                assert(returnedVersions == case.expectedVersions) {
                    "Actor ${case.actor}: expected versions ${case.expectedVersions}, got $returnedVersions"
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // GET /namespaces/{ns}/tags                                           //
    // ------------------------------------------------------------------ //

    @Nested
    inner class TagVisibility {

        @ParameterizedTest(name = "tags as {0}")
        @MethodSource("io.plugwerk.server.e2e.auth.CatalogEndpointAuthzTest#tagVisibilityCases")
        fun `tag visibility per actor`(case: NsActorExpectation) {
            mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/tags").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // GET /namespaces/{ns}/plugins.json                                   //
    // ------------------------------------------------------------------ //

    @Nested
    inner class PluginsJsonFeed {

        @ParameterizedTest(name = "plugins.json as {0}")
        @MethodSource("io.plugwerk.server.e2e.auth.CatalogEndpointAuthzTest#pluginsJsonCases")
        fun `plugins json access per actor`(case: NsActorExpectation) {
            mockMvc.perform(
                get("/api/v1/namespaces/${case.namespace}/plugins.json").actAs(case.actor),
            )
                .andExpect(status().`is`(case.expectedStatus))
        }
    }

    // ------------------------------------------------------------------ //
    // Method sources                                                      //
    // ------------------------------------------------------------------ //

    companion object {

        /**
         * Each test case specifies an actor, namespace, and the set of plugin IDs expected to be visible.
         */
        data class PluginVisibilityCase(
            val actor: Actor,
            val namespace: String,
            val expectedPluginIds: Set<String>,
            val expectedStatus: Int = 200,
            val hasDraftOnlyVisible: Boolean = false,
        ) {
            override fun toString(): String =
                "$actor on $namespace → ${if (expectedStatus != 200) "$expectedStatus" else expectedPluginIds.joinToString()}"
        }

        data class SinglePluginCase(
            val actor: Actor,
            val namespace: String,
            val pluginId: String,
            val expectedStatus: Int,
        ) {
            override fun toString(): String = "$actor → $namespace/$pluginId → $expectedStatus"
        }

        data class ReleaseVisibilityCase(
            val actor: Actor,
            val namespace: String,
            val pluginId: String,
            val expectedVersions: Set<String>,
            val expectedStatus: Int = 200,
        ) {
            override fun toString(): String =
                "$actor → $namespace/$pluginId releases → ${if (expectedStatus != 200) "$expectedStatus" else expectedVersions.joinToString()}"
        }

        // -- Plugin list visibility in NS1 (public) --

        private val NS1_PUBLIC_PLUGINS = setOf("ns1-pl1-active")
        private val NS1_AUTHENTICATED_PLUGINS = setOf("ns1-pl1-active", "ns1-pl3-archived", "ns1-pl4-draftonly")
        private val NS1_ADMIN_PLUGINS =
            setOf("ns1-pl1-active", "ns1-pl2-suspended", "ns1-pl3-archived", "ns1-pl4-draftonly")

        @JvmStatic
        fun pluginVisibilityNs1(): Stream<PluginVisibilityCase> = Stream.of(
            // ANONYMOUS: PublicNamespaceFilter sets AnonymousAuth, but Spring Security 6
            // anyRequest().authenticated() rejects anonymous tokens → 401
            PluginVisibilityCase(Actor.ANONYMOUS, NS1, emptySet(), expectedStatus = 401),
            PluginVisibilityCase(Actor.SUPERADMIN, NS1, NS1_ADMIN_PLUGINS, hasDraftOnlyVisible = true),
            PluginVisibilityCase(Actor.NS1_ADMIN, NS1, NS1_ADMIN_PLUGINS, hasDraftOnlyVisible = true),
            PluginVisibilityCase(Actor.NS1_READ_ONLY, NS1, NS1_AUTHENTICATED_PLUGINS, hasDraftOnlyVisible = true),
            PluginVisibilityCase(Actor.NS1_RO_NS2_RO, NS1, NS1_AUTHENTICATED_PLUGINS, hasDraftOnlyVisible = true),
            PluginVisibilityCase(
                Actor.NS1_MEMBER_NS2_RO,
                NS1,
                NS1_AUTHENTICATED_PLUGINS,
                hasDraftOnlyVisible = true,
            ),
            // NS2_ADMIN has no membership in NS1 → resolveVisibility falls through to PUBLIC
            PluginVisibilityCase(Actor.NS2_ADMIN, NS1, NS1_PUBLIC_PLUGINS),
            // UNRELATED has no membership in NS1 → resolveVisibility falls through to PUBLIC
            PluginVisibilityCase(Actor.UNRELATED, NS1, NS1_PUBLIC_PLUGINS),
            PluginVisibilityCase(Actor.API_KEY_NS1, NS1, NS1_PUBLIC_PLUGINS),
            // API_KEY_NS2 on NS1: resolveVisibility returns PUBLIC for all API keys → 200
            PluginVisibilityCase(Actor.API_KEY_NS2, NS1, NS1_PUBLIC_PLUGINS),
        )

        // -- Plugin list visibility in NS2 (private) --

        private val NS2_AUTHENTICATED_PLUGINS = setOf("ns2-pl1-active", "ns2-pl3-archived", "ns2-pl4-draftonly")
        private val NS2_ADMIN_PLUGINS =
            setOf("ns2-pl1-active", "ns2-pl2-suspended", "ns2-pl3-archived", "ns2-pl4-draftonly")
        private val NS2_PUBLIC_PLUGINS = setOf("ns2-pl1-active")

        @JvmStatic
        fun pluginVisibilityNs2(): Stream<PluginVisibilityCase> = Stream.of(
            // ANONYMOUS on private NS2: Spring Security 6 rejects anonymous tokens → 401
            PluginVisibilityCase(Actor.ANONYMOUS, NS2, emptySet(), expectedStatus = 401),
            PluginVisibilityCase(Actor.SUPERADMIN, NS2, NS2_ADMIN_PLUGINS, hasDraftOnlyVisible = true),
            // Non-members on NS2: resolveVisibility catches ForbiddenException → falls through to PUBLIC
            // NS2 has public plugins (ns2-pl1-active) so they get 200 with PUBLIC visibility
            PluginVisibilityCase(Actor.NS1_ADMIN, NS2, NS2_PUBLIC_PLUGINS),
            PluginVisibilityCase(Actor.NS1_READ_ONLY, NS2, NS2_PUBLIC_PLUGINS),
            PluginVisibilityCase(Actor.NS1_RO_NS2_RO, NS2, NS2_AUTHENTICATED_PLUGINS, hasDraftOnlyVisible = true),
            PluginVisibilityCase(
                Actor.NS1_MEMBER_NS2_RO,
                NS2,
                NS2_AUTHENTICATED_PLUGINS,
                hasDraftOnlyVisible = true,
            ),
            PluginVisibilityCase(Actor.NS2_ADMIN, NS2, NS2_ADMIN_PLUGINS, hasDraftOnlyVisible = true),
            PluginVisibilityCase(Actor.UNRELATED, NS2, NS2_PUBLIC_PLUGINS),
            // API keys: resolveVisibility returns PUBLIC for all API key callers
            PluginVisibilityCase(Actor.API_KEY_NS1, NS2, NS2_PUBLIC_PLUGINS),
            PluginVisibilityCase(Actor.API_KEY_NS2, NS2, NS2_PUBLIC_PLUGINS),
        )

        // -- Single plugin access --

        @JvmStatic
        fun singlePluginCases(): Stream<SinglePluginCase> = Stream.of(
            // ANONYMOUS gets 401 (Spring Security 6 rejects anonymous tokens)
            SinglePluginCase(Actor.ANONYMOUS, NS1, "ns1-pl1-active", 401),
            SinglePluginCase(Actor.SUPERADMIN, NS1, "ns1-pl1-active", 200),
            SinglePluginCase(Actor.API_KEY_NS1, NS1, "ns1-pl1-active", 200),
            // SUSPENDED plugin in NS1 — getPlugin has no visibility check → 200 for all authenticated users
            SinglePluginCase(Actor.ANONYMOUS, NS1, "ns1-pl2-suspended", 401),
            SinglePluginCase(Actor.NS1_READ_ONLY, NS1, "ns1-pl2-suspended", 200),
            SinglePluginCase(Actor.NS1_ADMIN, NS1, "ns1-pl2-suspended", 200),
            SinglePluginCase(Actor.SUPERADMIN, NS1, "ns1-pl2-suspended", 200),
            SinglePluginCase(Actor.API_KEY_NS1, NS1, "ns1-pl2-suspended", 200),
            // Plugin in NS2 — getPlugin has no access check → 200 for any authenticated user, 401 for anonymous
            SinglePluginCase(Actor.ANONYMOUS, NS2, "ns2-pl1-active", 401),
            SinglePluginCase(Actor.NS1_ADMIN, NS2, "ns2-pl1-active", 200),
            SinglePluginCase(Actor.API_KEY_NS1, NS2, "ns2-pl1-active", 200),
            SinglePluginCase(Actor.NS2_ADMIN, NS2, "ns2-pl1-active", 200),
            SinglePluginCase(Actor.API_KEY_NS2, NS2, "ns2-pl1-active", 200),
        )

        // -- Release visibility --

        private val ALL_VERSIONS = setOf("1.0.0", "1.1.0", "1.2.0", "1.3.0")
        private val PUBLIC_VERSIONS = setOf("1.1.0", "1.2.0") // PUBLISHED + DEPRECATED

        @JvmStatic
        fun releaseVisibilityCases(): Stream<ReleaseVisibilityCase> = Stream.of(
            // NS1 PL1 (ACTIVE) — release visibility by actor
            // ANONYMOUS on NS1: Spring Security 6 authenticated() rejects anonymous → 401
            ReleaseVisibilityCase(Actor.ANONYMOUS, NS1, "ns1-pl1-active", emptySet(), expectedStatus = 401),
            ReleaseVisibilityCase(Actor.SUPERADMIN, NS1, "ns1-pl1-active", ALL_VERSIONS),
            ReleaseVisibilityCase(Actor.NS1_ADMIN, NS1, "ns1-pl1-active", ALL_VERSIONS),
            ReleaseVisibilityCase(Actor.NS1_READ_ONLY, NS1, "ns1-pl1-active", ALL_VERSIONS),
            ReleaseVisibilityCase(Actor.UNRELATED, NS1, "ns1-pl1-active", ALL_VERSIONS),
            // API keys: listReleases has no visibility filter → returns ALL versions
            ReleaseVisibilityCase(Actor.API_KEY_NS1, NS1, "ns1-pl1-active", ALL_VERSIONS),
            // NS2 — ANONYMOUS gets 401, not 403 (no auth set for private namespace)
            ReleaseVisibilityCase(Actor.ANONYMOUS, NS2, "ns2-pl1-active", emptySet(), expectedStatus = 401),
            ReleaseVisibilityCase(Actor.NS2_ADMIN, NS2, "ns2-pl1-active", ALL_VERSIONS),
            // API keys: listReleases has no visibility filter → returns ALL versions
            ReleaseVisibilityCase(Actor.API_KEY_NS2, NS2, "ns2-pl1-active", ALL_VERSIONS),
        )

        // -- Tag visibility --

        @JvmStatic
        fun tagVisibilityCases(): Stream<NsActorExpectation> = Stream.of(
            // NS1 (public) — ANONYMOUS gets 401: Spring Security 6 authenticated() rejects anonymous tokens
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS1, 200),
            NsActorExpectation(Actor.NS1_READ_ONLY, NS1, 200),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 200),
            // API_KEY_NS2 on NS1: resolveVisibility returns PUBLIC, list tags returns 200
            NsActorExpectation(Actor.API_KEY_NS2, NS1, 200),
            // NS2 (private) — ANONYMOUS gets 401; non-members get 200 (resolveVisibility → PUBLIC)
            NsActorExpectation(Actor.ANONYMOUS, NS2, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS2, 200),
            NsActorExpectation(Actor.NS1_ADMIN, NS2, 200),
            NsActorExpectation(Actor.NS1_RO_NS2_RO, NS2, 200),
            NsActorExpectation(Actor.NS2_ADMIN, NS2, 200),
            NsActorExpectation(Actor.API_KEY_NS1, NS2, 200),
            NsActorExpectation(Actor.API_KEY_NS2, NS2, 200),
        )

        // -- plugins.json feed --

        @JvmStatic
        fun pluginsJsonCases(): Stream<NsActorExpectation> = Stream.of(
            // ANONYMOUS on NS1: Spring Security 6 authenticated() rejects anonymous tokens → 401
            NsActorExpectation(Actor.ANONYMOUS, NS1, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS1, 200),
            NsActorExpectation(Actor.API_KEY_NS1, NS1, 200),
            // API_KEY_NS2 on NS1: Pf4jCompatibilityService has no namespace-level key restriction → 200
            NsActorExpectation(Actor.API_KEY_NS2, NS1, 200),
            // ANONYMOUS on NS2: no anonymous auth for private namespace → 401
            NsActorExpectation(Actor.ANONYMOUS, NS2, 401),
            NsActorExpectation(Actor.SUPERADMIN, NS2, 200),
            NsActorExpectation(Actor.NS2_ADMIN, NS2, 200),
            NsActorExpectation(Actor.API_KEY_NS2, NS2, 200),
            // buildPluginsJson has no namespace access check → 200 for any authenticated user
            NsActorExpectation(Actor.NS1_ADMIN, NS2, 200),
        )
    }
}
