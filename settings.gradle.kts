pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gradleup.nmcp.settings").version("1.4.4")
}

nmcpSettings {
    centralPortal {
        username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME").orNull
        password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").orNull
        publishingType = "USER_MANAGED"
    }
}

rootProject.name = "plugwerk"

include("plugwerk-api:plugwerk-api-model")
include("plugwerk-api:plugwerk-api-endpoint")
include("plugwerk-spi")
include("plugwerk-descriptor")
include("plugwerk-server:plugwerk-server-backend")
include("plugwerk-server:plugwerk-server-frontend")
include("plugwerk-client-plugin")

// Examples — composite build so they resolve plugwerk-spi (and other modules)
// directly from the main build without publishToMavenLocal.
// Guarded: the examples directory is not present in Docker builds.
if (file("examples").isDirectory) {
    includeBuild("examples")
}
