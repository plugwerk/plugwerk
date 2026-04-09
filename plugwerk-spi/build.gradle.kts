plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.plugwerk.maven-publish")
}

description = "Plugwerk SPI — extension point interfaces and shared model types for the PF4J plugin ecosystem"

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
    api(libs.pf4j)
    api(libs.semver4j)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
