plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.openapi.generator) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    group = "io.plugwerk"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            target("src/**/*.kt")
            licenseHeader(
                """
                /*
                 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
                 * Copyright (C) 2026 devtank42 GmbH
                 *
                 * This program is free software: you can redistribute it and/or modify
                 * it under the terms of the GNU Affero General Public License as
                 * published by the Free Software Foundation, either version 3 of the
                 * License, or (at your option) any later version.
                 *
                 * This program is distributed in the hope that it will be useful,
                 * but WITHOUT ANY WARRANTY; without even the implied warranty of
                 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
                 * GNU Affero General Public License for more details.
                 *
                 * You should have received a copy of the GNU Affero General Public License
                 * along with this program. If not, see <https://www.gnu.org/licenses/>.
                 */
                """.trimIndent(),
            )
            ktlint("1.8.0")
                .editorConfigOverride(
                    mapOf(
                        "ktlint_code_style" to "intellij_idea",
                    ),
                )
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.8.0")
                .editorConfigOverride(
                    mapOf(
                        "ktlint_code_style" to "intellij_idea",
                    ),
                )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
            .editorConfigOverride(
                mapOf(
                    "ktlint_code_style" to "intellij_idea",
                ),
            )
    }
}
