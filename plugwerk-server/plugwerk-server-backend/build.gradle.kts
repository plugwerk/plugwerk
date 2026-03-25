plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

kotlin {
    jvmToolchain(21)
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

    runtimeOnly(libs.yasson)
    runtimeOnly(libs.postgresql)
    runtimeOnly(project(path = ":plugwerk-server:plugwerk-server-frontend", configuration = "staticResources"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.webmvc.test)
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
