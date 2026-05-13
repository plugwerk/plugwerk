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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf

class ConfigurationKeyRedactorTest {

    @Test
    fun `flags the four canonical credential-suffix shapes`() {
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("jwtSecret")).isTrue
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("accessKey")).isTrue
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("secretKey")).isTrue
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("scrapePassword")).isTrue
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("authToken")).isTrue
    }

    @Test
    fun `does not flag compound names whose sensitive-looking segment is not the last word`() {
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("keyPrefix")).isFalse
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("keyLookupHash")).isFalse
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("passwordChangeRequired")).isFalse
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("tokenValidityMinutes")).isFalse
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("secretManagerEnabled")).isFalse
    }

    @Test
    fun `kebab-case input also routes through the suffix rule`() {
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("jwt-secret")).isTrue
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("access-key")).isTrue
        assertThat(ConfigurationKeyRedactor.isSensitiveCamel("key-prefix")).isFalse
    }

    /**
     * The whole point of the redactor (#522) is that adding a new
     * sensitive field to `PlugwerkProperties` does not require remembering
     * to extend an allow-list. This test walks the declared properties of
     * `PlugwerkProperties` and every nested @ConfigurationProperties data
     * class and asserts that every field name ending in one of the four
     * suffix tokens is recognised as sensitive. If someone adds a new
     * credential without the conventional naming, this test stays green
     * but the redactor will leak the value — so the convention itself is
     * also documented in [ConfigurationKeyRedactor].
     */
    @Test
    fun `every PlugwerkProperties field whose last word matches a sensitive suffix is redacted`() {
        val suffixes = listOf("Secret", "Password", "Key", "Token")
        val offenders = collectPropertyNames(PlugwerkProperties::class)
            .filter { name ->
                // Last camelCase word is one of the sensitive suffixes …
                val lastWord = name.split(Regex("(?=[A-Z])")).last()
                lastWord in suffixes
            }
            .filter { name ->
                // … but the redactor does NOT classify it as sensitive.
                !ConfigurationKeyRedactor.isSensitiveCamel(name)
            }
            // The compound names we explicitly allow-listed are expected
            // here (e.g. `keyPrefix`); they must be the only survivors.
            .filter { it.lowercase() !in setOf("keyprefix") }

        assertThat(offenders)
            .withFailMessage(
                "These PlugwerkProperties fields look sensitive by name but " +
                    "ConfigurationKeyRedactor does not redact them — either " +
                    "extend the redactor or rename the field: %s",
                offenders,
            )
            .isEmpty()
    }

    private fun collectPropertyNames(
        klass: kotlin.reflect.KClass<*>,
        seen: MutableSet<kotlin.reflect.KClass<*>> = mutableSetOf(),
    ): List<String> {
        if (!seen.add(klass)) return emptyList()
        val results = mutableListOf<String>()
        for (prop in klass.declaredMemberProperties) {
            results += prop.name
            val returnType = prop.returnType.classifier as? kotlin.reflect.KClass<*> ?: continue
            // Recurse into nested data classes inside the same package family.
            if (returnType.qualifiedName?.startsWith("io.plugwerk.server.PlugwerkProperties") == true ||
                returnType.isSubclassOf(Any::class) &&
                returnType.qualifiedName?.startsWith("io.plugwerk.") == true &&
                returnType.isData
            ) {
                results += collectPropertyNames(returnType, seen)
            }
        }
        return results
    }
}
