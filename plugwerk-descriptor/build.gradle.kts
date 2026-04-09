plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.plugwerk.maven-publish")
}

description = "Plugwerk Descriptor — MANIFEST.MF parser and validator for PF4J plugin metadata"

kotlin {
    jvmToolchain(21)
}

tasks.compileKotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

tasks.compileJava {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

dependencies {
    api(project(":plugwerk-spi"))

    implementation(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}
