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

import io.plugwerk.server.domain.FileFormat
import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.spi.model.PluginStatus
import io.plugwerk.spi.model.ReleaseStatus
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.testcontainers.containers.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * Base class for authorization integration tests.
 *
 * Provides shared Testcontainers PostgreSQL, test data seeding, JWT token caching,
 * and helper methods for building authenticated MockMvc requests.
 *
 * All subclasses share the same Spring context and database container.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractAuthorizationTest {

    companion object {
        const val NS1 = "ns1"
        const val NS2 = "ns2"
        const val TEST_PASSWORD = "test-password-12!"

        /** Singleton container — shared with all integration tests via [SharedPostgresContainer]. */
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = io.plugwerk.server.SharedPostgresContainer.instance

        @DynamicPropertySource
        @JvmStatic
        fun overrideDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }

        /** JWT tokens keyed by actor — shared across all test classes in this context. */
        val tokenCache = ConcurrentHashMap<Actor, String>()

        /** API key plain-text values keyed by actor. */
        val apiKeyCache = ConcurrentHashMap<Actor, String>()

        /** Flag to prevent re-seeding when Spring context is reused across test classes. */
        @Volatile
        var seeded = false
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Autowired
    lateinit var namespaceMemberRepository: NamespaceMemberRepository

    @Autowired
    lateinit var pluginRepository: PluginRepository

    @Autowired
    lateinit var releaseRepository: PluginReleaseRepository

    @Autowired
    lateinit var accessKeyRepository: NamespaceAccessKeyRepository

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @BeforeAll
    fun seedTestData() {
        if (seeded) return
        synchronized(Companion) {
            if (seeded) return

            // The "admin" user is created automatically by Liquibase migration + admin-password config.
            // We need to make it a superadmin for our tests (it already is via bootstrap).
            // Create additional test users.
            val users = createTestUsers()
            val namespaces = createTestNamespaces()
            createTestMemberships(namespaces)
            createTestPluginsAndReleases(namespaces)
            createTestApiKeys(namespaces)
            loginAllJwtActors()

            seeded = true
        }
    }

    // ------------------------------------------------------------------ //
    // Auth helpers                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Obtains a fresh JWT token for the given actor by logging in again.
     *
     * Use this instead of [tokenCache] when the token will be consumed (e.g. logout),
     * so that the shared cached token is not revoked.
     */
    fun freshToken(actor: Actor): String {
        val actorUsernames = mapOf(
            Actor.SUPERADMIN to ("admin" to "smoke-test-password"),
            Actor.NS1_ADMIN to ("ns1-admin" to TEST_PASSWORD),
            Actor.NS1_READ_ONLY to ("ns1-readonly" to TEST_PASSWORD),
            Actor.NS1_RO_NS2_RO to ("ns1ro-ns2ro" to TEST_PASSWORD),
            Actor.NS1_MEMBER_NS2_RO to ("ns1member-ns2ro" to TEST_PASSWORD),
            Actor.NS2_ADMIN to ("ns2-admin" to TEST_PASSWORD),
            Actor.UNRELATED to ("unrelated-user" to TEST_PASSWORD),
        )
        val (username, password) = requireNotNull(actorUsernames[actor]) { "No username for $actor" }
        val result = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("username" to username, "password" to password))),
        ).andReturn()
        check(result.response.status == 200) {
            "Fresh login failed for $actor: ${result.response.status}"
        }
        val body = objectMapper.readValue(result.response.contentAsString, Map::class.java)
        return body["accessToken"] as String
    }

    /**
     * Adds the appropriate authentication header to a MockMvc request builder.
     *
     * - [Actor.ANONYMOUS]: no header added
     * - API key actors: `X-Api-Key` header
     * - JWT actors: `Authorization: Bearer <token>` header
     */
    fun MockHttpServletRequestBuilder.actAs(actor: Actor): MockHttpServletRequestBuilder = when {
        actor == Actor.ANONYMOUS -> this

        actor.isApiKey -> {
            val key = requireNotNull(apiKeyCache[actor]) { "No API key cached for $actor" }
            this.header("X-Api-Key", key)
        }

        else -> {
            val token = requireNotNull(tokenCache[actor]) { "No JWT cached for $actor" }
            this.header("Authorization", "Bearer $token")
        }
    }

    // ------------------------------------------------------------------ //
    // Ephemeral entity helpers (for destructive test operations)           //
    // ------------------------------------------------------------------ //

    /**
     * Creates a throwaway namespace for tests that need to delete a namespace.
     * Returns the namespace slug.
     */
    fun createEphemeralNamespace(slug: String = "ephemeral-${UUID.randomUUID().toString().take(8)}"): String {
        namespaceRepository.save(NamespaceEntity(slug = slug, name = "Ephemeral $slug", publicCatalog = true))
        return slug
    }

    /**
     * Creates a throwaway user for tests that need to delete a user.
     * Returns the user ID.
     */
    fun createEphemeralUser(username: String = "ephemeral-${UUID.randomUUID().toString().take(8)}"): UUID {
        val user = userRepository.save(
            UserEntity(
                username = username,
                passwordHash = requireNotNull(passwordEncoder.encode(TEST_PASSWORD)),
                passwordChangeRequired = false,
            ),
        )
        return user.id!!
    }

    /**
     * Creates a throwaway plugin with one PUBLISHED release in the given namespace.
     * Returns a pair of (pluginId string, releaseVersion string).
     */
    fun createEphemeralPlugin(
        namespaceSlug: String,
        pluginIdSuffix: String = UUID.randomUUID().toString().take(8),
    ): Pair<String, String> {
        val namespace = namespaceRepository.findBySlug(namespaceSlug).orElseThrow()
        val pluginId = "ephemeral-$pluginIdSuffix"
        val plugin = pluginRepository.save(
            PluginEntity(
                namespace = namespace,
                pluginId = pluginId,
                name = "Ephemeral Plugin $pluginIdSuffix",
                status = PluginStatus.ACTIVE,
                tags = emptyArray(),
            ),
        )
        val jarBytes = buildMinimalJar(pluginId, "1.0.0")
        releaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin,
                version = "1.0.0",
                status = ReleaseStatus.PUBLISHED,
                artifactSha256 = sha256(jarBytes),
                artifactKey = "$namespaceSlug/$pluginId/1.0.0",
                artifactSize = jarBytes.size.toLong(),
                fileFormat = FileFormat.JAR,
            ),
        )
        return pluginId to "1.0.0"
    }

    /**
     * Creates a throwaway namespace membership.
     */
    fun createEphemeralMembership(
        namespaceSlug: String,
        userSubject: String,
        role: NamespaceRole = NamespaceRole.READ_ONLY,
    ) {
        val namespace = namespaceRepository.findBySlug(namespaceSlug).orElseThrow()
        namespaceMemberRepository.save(
            NamespaceMemberEntity(namespace = namespace, userSubject = userSubject, role = role),
        )
    }

    // ------------------------------------------------------------------ //
    // JAR builder (copied from SmokeTest)                                 //
    // ------------------------------------------------------------------ //

    fun buildMinimalJar(pluginId: String, version: String): ByteArray {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", pluginId)
            mainAttributes.putValue("Plugin-Version", version)
            mainAttributes.putValue("Plugin-Name", "Test Plugin $pluginId")
            mainAttributes.putValue("Plugin-Description", "Auto-generated for auth/authz testing")
        }
        val out = ByteArrayOutputStream()
        JarOutputStream(out, manifest).use { jar ->
            jar.putNextEntry(JarEntry("dummy.txt"))
            jar.write("auth-test".toByteArray())
            jar.closeEntry()
        }
        return out.toByteArray()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ //
    // Parameterized test matrix helpers                                    //
    // ------------------------------------------------------------------ //

    data class ActorExpectation(val actor: Actor, val expectedStatus: Int) {
        override fun toString(): String = "$actor → $expectedStatus"
    }

    data class NsActorExpectation(val actor: Actor, val namespace: String, val expectedStatus: Int) {
        override fun toString(): String = "$actor on $namespace → $expectedStatus"
    }

    // ------------------------------------------------------------------ //
    // Private seeding methods                                             //
    // ------------------------------------------------------------------ //

    private fun createTestUsers(): Map<String, UserEntity> {
        // The "admin" user already exists from bootstrap (application-integration.yml admin-password).
        // Rename it to "superadmin" by updating. Actually, the bootstrap creates "admin" with the
        // configured password. For our tests we use "admin" as superadmin and map Actor.SUPERADMIN to it.
        // Let's just use the existing admin user and create additional users.

        val users = mutableMapOf<String, UserEntity>()

        // The built-in admin user (created by Liquibase + plugwerk.auth.admin-password)
        val admin = userRepository.findByUsername("admin").orElseThrow {
            IllegalStateException("Bootstrap admin user not found — check application-integration.yml")
        }
        // Ensure isSuperadmin is true
        if (!admin.isSuperadmin) {
            admin.isSuperadmin = true
            userRepository.save(admin)
        }
        // Ensure passwordChangeRequired is false so we can use the admin account
        if (admin.passwordChangeRequired) {
            admin.passwordChangeRequired = false
            userRepository.save(admin)
        }
        users["admin"] = admin

        // Create regular test users
        val userSpecs = listOf(
            "ns1-admin" to false,
            "ns1-readonly" to false,
            "ns1ro-ns2ro" to false,
            "ns1member-ns2ro" to false,
            "ns2-admin" to false,
            "unrelated-user" to false,
        )

        for ((username, isSuperadmin) in userSpecs) {
            if (userRepository.findByUsername(username).isPresent) continue
            val user = userRepository.save(
                UserEntity(
                    username = username,
                    passwordHash = requireNotNull(passwordEncoder.encode(TEST_PASSWORD)),
                    passwordChangeRequired = false,
                    isSuperadmin = isSuperadmin,
                ),
            )
            users[username] = user
        }

        return users
    }

    private fun createTestNamespaces(): Map<String, NamespaceEntity> {
        val namespaces = mutableMapOf<String, NamespaceEntity>()

        if (!namespaceRepository.existsBySlug(NS1)) {
            namespaces[NS1] = namespaceRepository.save(
                NamespaceEntity(slug = NS1, name = "Public Namespace", publicCatalog = true),
            )
        } else {
            namespaces[NS1] = namespaceRepository.findBySlug(NS1).orElseThrow()
        }

        if (!namespaceRepository.existsBySlug(NS2)) {
            namespaces[NS2] = namespaceRepository.save(
                NamespaceEntity(slug = NS2, name = "Private Namespace", publicCatalog = false),
            )
        } else {
            namespaces[NS2] = namespaceRepository.findBySlug(NS2).orElseThrow()
        }

        return namespaces
    }

    private fun createTestMemberships(namespaces: Map<String, NamespaceEntity>) {
        val ns1 = namespaces.getValue(NS1)
        val ns2 = namespaces.getValue(NS2)

        val memberships = listOf(
            Triple("ns1-admin", ns1, NamespaceRole.ADMIN),
            Triple("ns1-readonly", ns1, NamespaceRole.READ_ONLY),
            Triple("ns1ro-ns2ro", ns1, NamespaceRole.READ_ONLY),
            Triple("ns1ro-ns2ro", ns2, NamespaceRole.READ_ONLY),
            Triple("ns1member-ns2ro", ns1, NamespaceRole.MEMBER),
            Triple("ns1member-ns2ro", ns2, NamespaceRole.READ_ONLY),
            Triple("ns2-admin", ns2, NamespaceRole.ADMIN),
        )

        for ((subject, namespace, role) in memberships) {
            val exists = namespaceMemberRepository.findByNamespaceIdAndUserSubject(namespace.id!!, subject).isPresent
            if (!exists) {
                namespaceMemberRepository.save(
                    NamespaceMemberEntity(namespace = namespace, userSubject = subject, role = role),
                )
            }
        }
    }

    private fun createTestPluginsAndReleases(namespaces: Map<String, NamespaceEntity>) {
        for ((nsSlug, namespace) in namespaces) {
            val plugins = listOf(
                Triple("$nsSlug-pl1-active", PluginStatus.ACTIVE, arrayOf("tag-a")),
                Triple("$nsSlug-pl2-suspended", PluginStatus.SUSPENDED, arrayOf("tag-b")),
                Triple("$nsSlug-pl3-archived", PluginStatus.ARCHIVED, arrayOf("tag-a", "tag-c")),
                Triple("$nsSlug-pl4-draftonly", PluginStatus.ACTIVE, arrayOf("tag-d")),
            )

            for ((pluginId, status, tags) in plugins) {
                if (pluginRepository.existsByNamespaceAndPluginId(namespace, pluginId)) continue

                val plugin = pluginRepository.save(
                    PluginEntity(
                        namespace = namespace,
                        pluginId = pluginId,
                        name = "Test Plugin $pluginId",
                        status = status,
                        tags = tags,
                    ),
                )

                val releases = if (pluginId.endsWith("-draftonly")) {
                    listOf("0.1.0" to ReleaseStatus.DRAFT)
                } else {
                    listOf(
                        "1.0.0" to ReleaseStatus.DRAFT,
                        "1.1.0" to ReleaseStatus.PUBLISHED,
                        "1.2.0" to ReleaseStatus.DEPRECATED,
                        "1.3.0" to ReleaseStatus.YANKED,
                    )
                }

                for ((version, releaseStatus) in releases) {
                    val jarBytes = buildMinimalJar(pluginId, version)
                    releaseRepository.save(
                        PluginReleaseEntity(
                            plugin = plugin,
                            version = version,
                            status = releaseStatus,
                            artifactSha256 = sha256(jarBytes),
                            artifactKey = "$nsSlug/$pluginId/$version",
                            artifactSize = jarBytes.size.toLong(),
                            fileFormat = FileFormat.JAR,
                        ),
                    )
                }
            }
        }
    }

    private fun createTestApiKeys(namespaces: Map<String, NamespaceEntity>) {
        if (apiKeyCache.containsKey(Actor.API_KEY_NS1)) return

        val ns1 = namespaces.getValue(NS1)
        val ns2 = namespaces.getValue(NS2)

        // Create API keys directly via repository (AccessKeyService requires auth context)
        for ((actor, namespace, keyName) in listOf(
            Triple(Actor.API_KEY_NS1, ns1, "ci-key-ns1"),
            Triple(Actor.API_KEY_NS2, ns2, "ci-key-ns2"),
        )) {
            val plainKey =
                "pwk_" +
                    (1..40).map {
                        "abcdefghijklmnopqrstuvwxyz0123456789"[(it * 7 + actor.ordinal) % 36]
                    }.joinToString("")
            val keyHash = requireNotNull(passwordEncoder.encode(plainKey))
            accessKeyRepository.save(
                NamespaceAccessKeyEntity(
                    namespace = namespace,
                    keyHash = keyHash,
                    keyPrefix = plainKey.take(8),
                    name = keyName,
                ),
            )
            apiKeyCache[actor] = plainKey
        }
    }

    private fun loginAllJwtActors() {
        // Map Actor enum to actual usernames used for login
        val actorUsernames = mapOf(
            Actor.SUPERADMIN to "admin",
            Actor.NS1_ADMIN to "ns1-admin",
            Actor.NS1_READ_ONLY to "ns1-readonly",
            Actor.NS1_RO_NS2_RO to "ns1ro-ns2ro",
            Actor.NS1_MEMBER_NS2_RO to "ns1member-ns2ro",
            Actor.NS2_ADMIN to "ns2-admin",
            Actor.UNRELATED to "unrelated-user",
        )

        for ((actor, username) in actorUsernames) {
            if (tokenCache.containsKey(actor)) continue

            val password = if (actor == Actor.SUPERADMIN) "smoke-test-password" else TEST_PASSWORD

            val result = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("username" to username, "password" to password))),
            ).andReturn()

            check(result.response.status == 200) {
                "Login failed for actor $actor (username=$username): ${result.response.status} - ${result.response.contentAsString}"
            }

            val body = objectMapper.readValue(result.response.contentAsString, Map::class.java)
            val token = body["accessToken"] as String
            tokenCache[actor] = token
        }
    }
}
