plugins {
    base
}

val npmInstall by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine("npm", "install")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    outputs.dir("node_modules")
}

val npmBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine("npm", "run", "build")
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
