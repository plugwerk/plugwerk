plugins {
    application
}

application {
    mainClass.set("io.plugwerk.example.cli.Main")
}

dependencies {
    // CliCommand extension-point interface
    implementation(project(":plugwerk-java-cli-example:plugwerk-java-cli-example-api"))

    // plugwerk-spi must be on the HOST classpath so PF4J can match the interface
    // loaded by the parent classloader with the plugin's implementation.
    // plugwerk-client-sdk-plugin itself is NOT a compile dependency —
    // it is loaded at runtime as a PF4J plugin from the plugins directory.
    implementation(libs.plugwerk.spi)
    implementation(libs.pf4j)
    implementation(libs.picocli)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Fat JAR — bundles all runtime dependencies for standalone execution.
// Usage: java -jar build/libs/plugwerk-java-cli-example-app-<version>-fat.jar <command>
val fatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assembles a self-contained fat JAR with all runtime dependencies"
    archiveClassifier.set("fat")
    manifest {
        attributes["Main-Class"] = "io.plugwerk.example.cli.Main"
    }
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    from(sourceSets["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.MF")
}

tasks.named("assemble") {
    dependsOn(fatJar)
}
