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
