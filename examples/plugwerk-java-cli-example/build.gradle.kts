// Shared conventions for all modules of the CLI example.

plugins {
    id("com.diffplug.spotless") version "7.1.0" apply false
}

// Lifecycle tasks on the root project — needed for composite build delegation
// from the examples/ aggregator (e.g. `cd examples && ./gradlew build`).
val lifecycleTasks = listOf("build", "clean", "assemble", "check")
lifecycleTasks.forEach { taskName ->
    tasks.register(taskName) {
        group = "build"
        dependsOn(subprojects.mapNotNull { it.tasks.findByName(taskName) })
        if (taskName == "build" || taskName == "assemble") {
            dependsOn(copyClientPlugin)
        }
    }
}

// Copy the plugwerk-client-plugin ZIP into plugins/ so the CLI app can load it at runtime.
// The ZIP is built by the included (composite) build; we reference its task explicitly.
val copyClientPlugin by tasks.registering(Copy::class) {
    group = "build"
    description = "Copies the plugwerk-client-plugin ZIP into the plugins directory"
    dependsOn(gradle.includedBuild("plugwerk").task(":plugwerk-client-plugin:pluginZip"))
    from(rootDir.resolve("../../plugwerk-client-plugin/build/pf4j")) {
        include("plugwerk-client-plugin-*.zip")
    }
    into(layout.projectDirectory.dir("plugins"))
}

allprojects {
    group = "io.plugwerk.examples"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        // Fallback: only needed when building outside of the composite build
        // (e.g. as a standalone project without the parent checkout).
        mavenLocal()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            licenseHeaderFile(rootProject.file("../../license-header.txt"))
            googleJavaFormat()
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
