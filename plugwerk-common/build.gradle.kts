plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(libs.pf4j)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}
