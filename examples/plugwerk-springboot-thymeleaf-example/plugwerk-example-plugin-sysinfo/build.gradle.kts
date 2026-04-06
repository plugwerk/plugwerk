// PF4J + Plugwerk plugin metadata (all in MANIFEST.MF — no plugwerk.yml needed)
val pf4jPluginId          = "io.plugwerk.example.webapp.sysinfo"
val pf4jPluginClass       = "io.plugwerk.example.webapp.sysinfo.SysInfoPlugin"
val pf4jPluginVersion     = project.version.toString()
val pf4jPluginProvider    = "plugwerk-examples"
val pf4jPluginDescription = "Example webapp plugin — adds a system information page"

tasks.jar {
    manifest {
        attributes(
            // PF4J standard attributes
            "Plugin-Id"          to pf4jPluginId,
            "Plugin-Class"       to pf4jPluginClass,
            "Plugin-Version"     to pf4jPluginVersion,
            "Plugin-Provider"    to pf4jPluginProvider,
            "Plugin-Description" to pf4jPluginDescription,
            // Plugwerk custom attributes (read by the server during upload)
            "Plugin-Name"        to "SysInfo Page Plugin",
            "Plugin-License"     to "Apache-2.0",
            "Plugin-Tags"        to "sysinfo, demo, webapp, system, monitoring",
        )
    }
}

dependencies {
    // compileOnly — the host application provides this JAR (and all its transitive deps) at runtime
    compileOnly(project(":plugwerk-springboot-thymeleaf-example-api"))
    compileOnly(libs.pf4j)
    // PF4J annotation processor generates META-INF/extensions.idx from @Extension annotations
    annotationProcessor(libs.pf4j)
}

// ---------------------------------------------------------------------------
// PF4J Plugin ZIP (compatible with DefaultPluginManager)
//
//   io.plugwerk.example.webapp.sysinfo-<version>.zip
//   ├── META-INF/MANIFEST.MF   ← loose file (required by ManifestPluginDescriptorFinder)
//   └── lib/
//       └── plugwerk-example-plugin-sysinfo-<version>.jar
//
// All runtime dependencies (plugwerk-springboot-thymeleaf-example-api, pf4j)
// are provided by the host application and must NOT be bundled.
// ---------------------------------------------------------------------------
val hostProvidedPrefixes = setOf(
    "plugwerk-springboot-thymeleaf-example-api",
    "plugwerk-spi",
    "pf4j",
    "slf4j-api",
)

val pluginZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Assembles the PF4J plugin ZIP for upload to the Plugwerk server"
    archiveBaseName.set(pf4jPluginId)
    destinationDirectory.set(layout.buildDirectory.dir("pf4j"))

    // META-INF/MANIFEST.MF as a loose file at the ZIP root
    from(tasks.jar.map { jar ->
        project.zipTree(jar.archiveFile.get()).matching {
            include("META-INF/MANIFEST.MF")
        }
    })

    // Plugin JAR only — all dependencies are host-provided
    val bundledDeps = configurations.runtimeClasspath.map { cp ->
        cp.filter { file -> hostProvidedPrefixes.none { file.name.startsWith("$it-") } }
    }
    into("lib") {
        from(tasks.jar)
        from(bundledDeps)
    }
}

tasks.named("assemble") {
    dependsOn(pluginZip)
}
