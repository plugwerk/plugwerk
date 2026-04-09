plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.plugwerk.maven-publish")
}

description = "Plugwerk Client SDK — catalog, install, and update lifecycle for PF4J host applications"

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

// PF4J plugin metadata — embedded in MANIFEST.MF so the SDK JAR is loadable as a PF4J plugin
val pf4jPluginId = "plugwerk-client-plugin"
val pf4jPluginClass = "io.plugwerk.client.PlugwerkPluginImpl"
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
// PF4J Plugin ZIP Format (compatible with DefaultPluginManager):
//
//   plugwerk-client-plugin-<version>.zip
//   ├── META-INF/
//   │   └── MANIFEST.MF        ← loose file (not inside the JAR!)
//   └── lib/
//       ├── plugwerk-client-plugin-<version>.jar   ← main plugin JAR
//       ├── jackson-databind-x.y.z.jar
//       ├── okhttp-x.y.z.jar
//       └── ...
//
// Why this structure:
//   DefaultPluginRepository.extractZipFiles() unzips the ZIP to a directory.
//   ManifestPluginDescriptorFinder.readManifestFromDirectory() calls
//   FileUtils.findFile(dir, "MANIFEST.MF") — which scans for a LOOSE FILE named
//   MANIFEST.MF. It does NOT peek inside JARs. So the manifest must be at the
//   ZIP root as a real file, not only inside the inner JAR.
//
//   DefaultPluginClasspath scans lib/ for JARs (classesDirectories = ["classes"],
//   jarsDirectories = ["lib"]). Putting the main plugin JAR in lib/ ensures
//   DefaultPluginLoader adds it to the plugin classloader's classpath.
//
//   plugwerk-spi and pf4j JARs are excluded — the host app provides them on the
//   parent classloader so that ExtensionPoint interface identity is shared.
// ---------------------------------------------------------------------------
// slf4j-api must also be host-provided: the plugin must use the same SLF4J instance as
// the host so that LogbackServiceProvider (in the host classloader) is visible when
// ServiceLoader resolves SLF4JServiceProvider via the plugin classloader.
// Bundling slf4j-api in the plugin ZIP causes "not a subtype" errors at startup.
val hostProvidedArtifacts = setOf("plugwerk-spi", "pf4j", "slf4j-api")

val pluginZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Assembles the PF4J plugin ZIP with the SDK JAR and all bundled dependencies"
    archiveBaseName.set(pf4jPluginId)
    destinationDirectory.set(layout.buildDirectory.dir("pf4j"))

    // META-INF/MANIFEST.MF as a loose file at the ZIP root.
    // ManifestPluginDescriptorFinder searches for "MANIFEST.MF" by name in the extracted
    // directory; it reads loose files only, not JAR-embedded manifests.
    from(
        tasks.jar.map { jar ->
            project.zipTree(jar.archiveFile.get()).matching {
                include("META-INF/MANIFEST.MF")
            }
        },
    )

    // Plugin JAR + runtime dependencies (minus host-provided) → lib/
    // DefaultPluginClasspath scans lib/ for JARs; the main plugin JAR also goes here
    // so DefaultPluginLoader adds it to the plugin classloader.
    val bundledDeps = configurations.runtimeClasspath.map { cp ->
        cp.filter { file -> hostProvidedArtifacts.none { file.name.startsWith("$it-") } }
    }
    into("lib") {
        from(tasks.jar)
        from(bundledDeps)
    }
}

tasks.named("assemble") {
    dependsOn(pluginZip)
}
