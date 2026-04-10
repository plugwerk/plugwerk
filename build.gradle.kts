plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.openapi.generator) apply false
    alias(libs.plugins.spotless)
}

val projectVersion = file("VERSION").readText().trim()

allprojects {
    group = "io.plugwerk"
    version = projectVersion

    repositories {
        mavenCentral()
    }
}

val apacheModules = setOf("plugwerk-spi", "plugwerk-descriptor", "plugwerk-client-plugin", "plugwerk-api-model")

subprojects {
    apply(plugin = "com.diffplug.spotless")

    val headerFile = if (project.name in apacheModules) {
        rootProject.file("license-header-apache.txt")
    } else {
        rootProject.file("license-header.txt")
    }

    spotless {
        kotlin {
            target("src/**/*.kt")
            licenseHeaderFile(headerFile)
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
