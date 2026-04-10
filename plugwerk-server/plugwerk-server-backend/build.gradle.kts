plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("io.plugwerk.maven-publish")
}

kotlin {
    jvmToolchain(21)
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(project(":plugwerk-api:plugwerk-api-endpoint"))
    implementation(project(":plugwerk-spi"))
    implementation(project(":plugwerk-descriptor"))

    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.boot.starter.liquibase)
    implementation(libs.bucket4j.core)
    implementation(libs.caffeine)

    runtimeOnly(libs.yasson)
    runtimeOnly(libs.postgresql)
    runtimeOnly(project(path = ":plugwerk-server:plugwerk-server-frontend", configuration = "staticResources"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)

    testRuntimeOnly(libs.h2)
}

// Copy the canonical OpenAPI spec into static resources so it is served at /api-docs/openapi.yaml
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("plugwerk-api/src/main/resources/openapi/plugwerk-api.yaml")) {
        into("static/api-docs")
        rename { "openapi.yaml" }
        filter { line ->
            // Replace the OpenAPI info.version (indented with 2 spaces) with the project version
            if (line.matches(Regex("^  version: .+"))) {
                "  version: ${project.version}"
            } else {
                line
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Publishing: fat JAR (bootJar) + distribution ZIP to Maven Central
// ---------------------------------------------------------------------------

// Spring Boot disables the plain JAR by default — re-enable it for the
// maven-publish convention plugin which publishes from components["java"]
tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
    enabled = true
}

val serverDistZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Assembles a distribution ZIP with the fat JAR, config, and start scripts"
    archiveBaseName.set("plugwerk-server")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    into("plugwerk-server-${project.version}") {
        from(tasks.named("bootJar"))
        from(rootProject.file("LICENSE"))
        from(rootProject.file("README.md"))
    }
}

tasks.named("assemble") {
    dependsOn(serverDistZip)
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            // Add the fat JAR as the primary artifact (replaces plain JAR)
            artifact(tasks.named("bootJar")) {
                classifier = null as String?
            }
            // Add the distribution ZIP
            artifact(serverDistZip) {
                classifier = "dist"
                extension = "zip"
            }
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against PostgreSQL via Testcontainers (requires Docker)"
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    classpath = sourceSets["test"].runtimeClasspath
    testClassesDirs = sourceSets["test"].output.classesDirs
    shouldRunAfter(tasks.named("test"))
}
