plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.compileKotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.compileJava {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

// PF4J plugin metadata — embedded in MANIFEST.MF so the SDK JAR is loadable as a PF4J plugin
val pf4jPluginId = "plugwerk-client-sdk-plugin"
val pf4jPluginClass = "io.plugwerk.client.PlugwerkMarketplacePlugin"
val pf4jPluginVersion: String = project.version.toString()
val pf4jPluginProvider = "devtank42 GmbH"
val pf4jPluginDescription = "Plugwerk Client SDK — catalog, install, update for PF4J host apps"

tasks.jar {
    manifest {
        attributes(
            "Plugin-Id" to pf4jPluginId,
            "Plugin-Class" to pf4jPluginClass,
            "Plugin-Version" to pf4jPluginVersion,
            "Plugin-Provider" to pf4jPluginProvider,
            "Plugin-Description" to pf4jPluginDescription,
        )
    }
}

// ---------------------------------------------------------------------------
// PF4J Classloader Model:
//
// The host application must have plugwerk-spi and pf4j on its classpath.
// PF4J's extension mechanism requires that ExtensionPoint interfaces (defined
// in plugwerk-spi) are loaded by the PARENT classloader — shared between
// host and plugin. Without this, getExtensions(PlugwerkCatalog::class.java)
// cannot match the plugin's implementation to the host's interface reference.
//
// Only the plugwerk-spi and pf4j JARs themselves are excluded from the ZIP.
// Their transitive dependencies (e.g. kotlin-stdlib) ARE bundled, because the
// host may be a pure Java application without Kotlin on its classpath.
// ---------------------------------------------------------------------------

dependencies {
    api(project(":plugwerk-spi"))
    api(project(":plugwerk-api:plugwerk-api-model"))

    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.okhttp)
    implementation(libs.pf4j)
    implementation(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// ---------------------------------------------------------------------------
// PF4J Plugin ZIP — bundles the plugin JAR + all runtime dependencies under lib/
//
// Format:  plugwerk-client-sdk-plugin-<version>.zip
//          ├── plugwerk-client-sdk-plugin-<version>.jar
//          └── lib/
//              ├── jackson-databind-x.y.z.jar
//              ├── okhttp-x.y.z.jar
//              └── ...
//
// Only the plugwerk-spi and pf4j JARs themselves are excluded — the host
// application must provide them on its classpath (PF4J parent classloader).
// All transitive dependencies (e.g. kotlin-stdlib) ARE bundled so that
// pure Java host applications work without Kotlin on their classpath.
// ---------------------------------------------------------------------------
val hostProvidedArtifacts = setOf("plugwerk-spi", "pf4j")

val pluginZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Assembles the PF4J plugin ZIP with the SDK JAR and all bundled dependencies"
    archiveBaseName.set(pf4jPluginId)
    destinationDirectory.set(layout.buildDirectory.dir("pf4j"))

    // Plugin JAR at ZIP root
    from(tasks.jar)

    // Runtime dependencies minus host-provided JARs → lib/
    val bundledDeps = configurations.runtimeClasspath.map { cp ->
        cp.filter { file -> hostProvidedArtifacts.none { file.name.startsWith("$it-") } }
    }
    into("lib") {
        from(bundledDeps)
    }
}

tasks.named("assemble") {
    dependsOn(pluginZip)
}
