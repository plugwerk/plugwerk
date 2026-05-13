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
import jakarta.validation.constraints.AssertTrue
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

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
     * JSON field names produced by Jackson for Bean-Validation methods
     * (`@AssertTrue fun isFoo(): Boolean` → JSON field `foo`). These
     * are validation checks, not configurable properties, so they must
     * not surface in the admin UI tree. Computed once at bean
     * construction by walking `PlugwerkProperties` and every nested
     * `@ConfigurationProperties`-style data class via Kotlin reflection.
     */
    private val hiddenFieldNames: Set<String> =
        collectAssertTrueFieldNames(PlugwerkProperties::class)

    /**
     * Returns the redacted tree, ready to serialise as the admin
     * endpoint's response. Field names are converted to kebab-case
     * during the walk so the rendered paths match the yaml the
     * operator actually edits (`path-style-access` instead of
     * `pathStyleAccess`).
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
                // Skip @AssertTrue validation methods serialised as
                // is*() getters — they are checks, not configuration,
                // and would otherwise pollute the admin UI with
                // pseudo-properties like
                // `is-s3-config-present-when-s3-selected`.
                if (fieldName in hiddenFieldNames) return@forEach
                val value = node.get(fieldName)
                // Convert camelCase property names to kebab-case so the
                // rendered path matches the yaml the operator edits.
                // Redaction is decided against the original (camelCase)
                // name because the suffix rule has both shapes covered
                // anyway and the original keeps the intent obvious.
                val renderedName = toKebabCase(fieldName)
                replacement.set(
                    renderedName,
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
     * Converts a camelCase identifier to kebab-case
     * (`pathStyleAccess` → `path-style-access`). Identifiers that are
     * already kebab-case or single-word are returned unchanged.
     */
    private fun toKebabCase(camel: String): String = camel
        .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
        .lowercase()

    /**
     * Walks the `@ConfigurationProperties` class graph starting at
     * [root] and collects the JSON field names that correspond to
     * `@AssertTrue` Bean-Validation methods. The Jackson convention
     * for `isFoo()` → JSON `foo`; we honour that so a Set lookup on
     * the raw field name works in [redact].
     *
     * Only types in the `io.plugwerk.` package family are recursed
     * into so we don't drift into JDK / library types like `Duration`.
     */
    private fun collectAssertTrueFieldNames(root: KClass<*>): Set<String> {
        val seen = mutableSetOf<KClass<*>>()
        val hidden = mutableSetOf<String>()
        fun visit(klass: KClass<*>) {
            if (!seen.add(klass)) return
            for (fn in klass.declaredMemberFunctions) {
                if (fn.findAnnotation<AssertTrue>() == null) continue
                val name = fn.name
                // Jackson strips a leading `is` and lowercases the
                // first remaining char: `isFooBar` → `fooBar`.
                if (name.length > 2 && name.startsWith("is") && name[2].isUpperCase()) {
                    hidden += name[2].lowercaseChar() + name.substring(3)
                }
            }
            for (prop in klass.declaredMemberProperties) {
                val type = prop.returnType.classifier as? KClass<*> ?: continue
                if (type.qualifiedName?.startsWith("io.plugwerk.") == true) {
                    visit(type)
                }
            }
        }
        visit(root)
        return hidden
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
