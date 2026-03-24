// Shared conventions for all example projects.
// Individual examples add their own dependencies in their own build.gradle.kts files.

allprojects {
    group = "io.plugwerk.examples"
    version = "0.1.0-SNAPSHOT"

    repositories {
        // plugwerk-spi and plugwerk-client-plugin are resolved from Maven Local.
        // Run `./gradlew publishToMavenLocal` in the main project before building examples.
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    // java-library is a superset of java and adds the api() dependency configuration
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
