// Spring Boot + Thymeleaf example host application with dynamic PF4J plugin pages.

plugins {
    java
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.1.0"
}

// Lifecycle tasks on the root project — needed for composite build delegation
// from the examples/ aggregator (e.g. `cd examples && ./gradlew build`).
val lifecycleTasks = listOf("build", "clean", "assemble", "check")
lifecycleTasks.forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(subprojects.mapNotNull { it.tasks.findByName(taskName) })
        if (taskName == "build" || taskName == "assemble") {
            dependsOn(copyClientPlugin)
        }
    }
}

// Copy the plugwerk-client-plugin ZIP into plugins/ so the app can load it at runtime.
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

val projectVersion = rootProject.file("../../VERSION").readText().trim()

allprojects {
    group = "io.plugwerk.examples"
    version = projectVersion

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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

spotless {
    java {
        target("src/**/*.java")
        licenseHeaderFile(rootProject.file("../../license-header.txt"))
        googleJavaFormat()
    }
}

dependencies {
    // Extension-point API
    implementation(project(":plugwerk-springboot-thymeleaf-example-api"))

    // plugwerk-spi must be on the HOST classpath so PF4J can match the interface
    // loaded by the parent classloader with the plugin's implementation.
    // plugwerk-client-plugin itself is NOT a compile dependency —
    // it is loaded at runtime as a PF4J plugin from the plugins directory.
    implementation(libs.plugwerk.spi)
    implementation(libs.pf4j)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    // Spring Boot + Thymeleaf
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation(libs.thymeleaf.layout.dialect)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

springBoot {
    mainClass.set("io.plugwerk.example.webapp.WebApp")
}
