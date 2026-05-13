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
package io.plugwerk.server.service.configuration

import io.plugwerk.server.PlugwerkProperties
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

/**
 * Builds a read-only JSON tree of the effective `plugwerk.*` configuration
 * for the admin UI (#522).
 *
 * The body of the tree is whatever Jackson produces for
 * [PlugwerkProperties] — the same bean Spring Boot has bound at startup.
 * A recursive walker then replaces any leaf whose property name is
 * classified sensitive by [ConfigurationKeyRedactor] with the redacted
 * marker `{ "_secret": true, "configured": <bool> }` so the dashboard
 * can render a "configured / not configured" chip without ever seeing
 * the value.
 */
@Service
class ConfigurationTreeBuilder(private val objectMapper: ObjectMapper, private val properties: PlugwerkProperties) {
    /**
     * Returns the redacted tree, ready to serialise as the admin
     * endpoint's response.
     */
    fun build(): ObjectNode {
        val raw = objectMapper.valueToTree<JsonNode>(properties)
        require(raw is ObjectNode) {
            "PlugwerkProperties must serialise to an object, got ${raw.nodeType}"
        }
        return redact(raw) as ObjectNode
    }

    private fun redact(node: JsonNode): JsonNode = when (node) {
        is ObjectNode -> {
            val replacement = objectMapper.createObjectNode()
            node.propertyNames().forEach { fieldName ->
                val value = node.get(fieldName)
                replacement.set(
                    fieldName,
                    if (ConfigurationKeyRedactor.isSensitiveCamel(fieldName)) {
                        redactedLeaf(value)
                    } else {
                        redact(value)
                    },
                )
            }
            replacement
        }

        is ArrayNode -> {
            val replacement = objectMapper.createArrayNode()
            for (i in 0 until node.size()) {
                replacement.add(redact(node.get(i)))
            }
            replacement
        }

        else -> node
    }

    /**
     * Replaces a sensitive leaf with the redacted marker. "Configured"
     * means the value is present and non-empty; null and the empty
     * string both count as "not configured" so an operator can tell at
     * a glance whether the deployment ever set the credential.
     */
    private fun redactedLeaf(value: JsonNode?): ObjectNode {
        val configured = value != null &&
            !value.isNull &&
            !(value.isTextual && value.textValue().isBlank())
        val node = objectMapper.createObjectNode()
        node.put("_secret", true)
        node.put("configured", configured)
        return node
    }
}
