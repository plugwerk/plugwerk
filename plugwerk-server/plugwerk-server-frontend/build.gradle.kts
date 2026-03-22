plugins {
    base
}

fun npmCmd(vararg args: String): List<String> =
    if (System.getProperty("os.name").lowercase().contains("windows"))
        listOf("cmd", "/c", "npm") + args
    else
        listOf("bash", "-c", "npm ${args.joinToString(" ")}")

val npmInstall by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine(npmCmd("install"))
    inputs.file("package.json")
    inputs.file("package-lock.json")
    outputs.dir("node_modules")
}

val npmBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine(npmCmd("run", "build"))
    inputs.dir("src")
    inputs.file("index.html")
    inputs.file("vite.config.ts")
    inputs.file("tsconfig.json")
    outputs.dir("${layout.buildDirectory.get()}/dist")
}

val copyFrontend by tasks.registering(Copy::class) {
    dependsOn(npmBuild)
    from("${layout.buildDirectory.get()}/dist")
    into("${layout.buildDirectory.get()}/resources/main/static")
}

tasks.named("build") {
    dependsOn(copyFrontend)
}

// Make the static resources available as a runtime dependency
configurations.create("staticResources") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("staticResources", file("${layout.buildDirectory.get()}/resources/main/static")) {
        builtBy(copyFrontend)
    }
}
