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

import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import io.plugwerk.server.repository.NamespaceRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * End-to-end Spring Security chain assertions for the `publicCatalog = true` carve-out
 * (issue #374). Pins the contract that anonymous GETs against catalog read endpoints
 * succeed when (and only when) the namespace has `publicCatalog = true`.
 *
 * The pre-existing [PublicNamespaceFilterTest] exercises the filter in isolation —
 * it cannot detect the production failure mode where the filter sets a token that
 * Spring's authorization stage still rejects. This IT walks the full chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:public-catalog-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class PublicNamespaceCatalogIT {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var namespaceRepository: NamespaceRepository

    @Autowired private lateinit var apiKeyRepository: NamespaceAccessKeyRepository

    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var accessKeyHmac: AccessKeyHmac

    private lateinit var publicNamespace: NamespaceEntity
    private lateinit var privateNamespace: NamespaceEntity
    private lateinit var publicNamespaceApiKey: String

    @BeforeEach
    fun seed() {
        apiKeyRepository.deleteAll()
        namespaceRepository.deleteAll()
        publicNamespace = namespaceRepository.save(
            NamespaceEntity(slug = "public-ns", name = "public-ns", publicCatalog = true),
        )
        privateNamespace = namespaceRepository.save(
            NamespaceEntity(slug = "private-ns", name = "private-ns", publicCatalog = false),
        )
        publicNamespaceApiKey = "pwk_" + "a".repeat(40)
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = publicNamespace,
                keyHash = passwordEncoder.encode(publicNamespaceApiKey)!!,
                keyLookupHash = accessKeyHmac.compute(publicNamespaceApiKey),
                keyPrefix = publicNamespaceApiKey.take(8),
                name = "test-key",
            ),
        )
    }

    @Test
    fun `anonymous GET on public namespace catalog returns 200`() {
        mockMvc.get("/api/v1/namespaces/public-ns/plugins")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `anonymous GET on private namespace catalog returns 401`() {
        mockMvc.get("/api/v1/namespaces/private-ns/plugins")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `anonymous GET on unknown namespace returns 401`() {
        mockMvc.get("/api/v1/namespaces/no-such-ns/plugins")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `anonymous POST on public namespace stays unauthorized`() {
        mockMvc.post("/api/v1/namespaces/public-ns/plugins") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `anonymous GET on non-catalog sub-path of public namespace stays unauthorized`() {
        // The carve-out is scoped to read-only catalog endpoints (ADR-0011).
        // Members, access keys, settings etc. must not be readable anonymously
        // even when the namespace is marked public.
        mockMvc.get("/api/v1/namespaces/public-ns/members")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `X-Api-Key on public namespace overrides anonymous public token`() {
        mockMvc.get("/api/v1/namespaces/public-ns/plugins") {
            header("X-Api-Key", publicNamespaceApiKey)
        }.andExpect { status { isOk() } }
    }
}
