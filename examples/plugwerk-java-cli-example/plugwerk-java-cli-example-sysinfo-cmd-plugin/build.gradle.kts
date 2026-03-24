// PF4J plugin metadata
val pf4jPluginId          = "plugwerk-java-cli-example-sysinfo-cmd-plugin"
val pf4jPluginClass       = "io.plugwerk.example.cli.sysinfo.SysinfoPlugin"
val pf4jPluginVersion     = project.version.toString()
val pf4jPluginProvider    = "plugwerk-examples"
val pf4jPluginDescription = "Example CLI plugin — adds a 'sysinfo' system information subcommand"

tasks.jar {
    manifest {
        attributes(
            "Plugin-Id"          to pf4jPluginId,
            "Plugin-Class"       to pf4jPluginClass,
            "Plugin-Version"     to pf4jPluginVersion,
            "Plugin-Provider"    to pf4jPluginProvider,
            "Plugin-Description" to pf4jPluginDescription,
        )
    }
}

dependencies {
    // compileOnly — the host application provides this JAR (and all its transitive deps) at runtime
    compileOnly(project(":plugwerk-java-cli-example:plugwerk-java-cli-example-api"))
    // PF4J annotation processor generates META-INF/extensions.idx from @Extension annotations
    annotationProcessor(libs.pf4j)
}

// ---------------------------------------------------------------------------
// PF4J Plugin ZIP (compatible with DefaultPluginManager)
//
//   plugwerk-java-cli-example-sysinfo-cmd-plugin-<version>.zip
//   ├── META-INF/MANIFEST.MF   ← loose file (required by ManifestPluginDescriptorFinder)
//   └── lib/
//       └── plugwerk-java-cli-example-sysinfo-cmd-plugin-<version>.jar
//
// All runtime dependencies (plugwerk-java-cli-example-api, plugwerk-spi, pf4j, picocli)
// are provided by the host application and must NOT be bundled.
// ---------------------------------------------------------------------------
val hostProvidedPrefixes = setOf(
    "plugwerk-java-cli-example-api",
    "plugwerk-spi",
    "pf4j",
    "slf4j-api",
    "picocli",
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
