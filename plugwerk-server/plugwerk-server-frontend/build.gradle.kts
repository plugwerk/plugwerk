plugins {
    base
}

fun npmCmd(vararg args: String): List<String> = if (System.getProperty("os.name").lowercase().contains("windows")) {
    listOf("cmd", "/c", "npm") + args
} else {
    listOf("bash", "-c", "npm ${args.joinToString(" ")}")
}

val npmInstall by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine(npmCmd("install"))
    inputs.file("package.json")
    inputs.file("package-lock.json")
    outputs.dir("node_modules")
}

val npmFormatCheck by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine(npmCmd("run", "format:check"))
    inputs.dir("src")
    inputs.file(".prettierrc")
    inputs.file(".prettierignore")
    outputs.upToDateWhen { true }
}

// ESLint gate (DEV-21). A non-zero `npm run lint` fails the Gradle build, so
// `./gradlew build` — and therefore CI — blocks any PR that introduces a lint
// error. ESLint warnings do not change the exit code, so they surface in the
// log without breaking the build.
val npmLint by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine(npmCmd("run", "lint"))
    inputs.dir("src")
    inputs.file("eslint.config.js")
    inputs.file("package.json")
    outputs.upToDateWhen { true }
}

// Vitest gate (DEV-29). The frontend suite was red on `main` and invisible to
// CI — only `format:check` + `build` were wired in, so a broken test never
// failed a build. `test:run` is non-watch and exits non-zero on any failure.
val npmTest by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine(npmCmd("run", "test:run"))
    inputs.dir("src")
    inputs.file("vitest.config.ts")
    outputs.upToDateWhen { true }
}

val npmBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall, npmFormatCheck, npmLint)
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

// `check` is wired into `build` by the `base` plugin, so attaching the Vitest
// suite here makes `./gradlew build` (CI) enforce it without touching ci.yml.
tasks.named("check") {
    dependsOn(npmTest)
}

// Make the static resources available as a runtime dependency
configurations.create("staticResources") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("staticResources", file("${layout.buildDirectory.get()}/resources/main")) {
        builtBy(copyFrontend)
    }
}
