plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.cyclonedx.bom)
    `maven-publish`
    signing
}

// CycloneDX SBOM generation for CI vulnerability scanning (issue #297, ADR-0030).
// Restricted to runtimeClasspath: only deps that ship in the production artefact
// are security-relevant. Test/compile-only deps would add false-positive noise.
tasks.cyclonedxDirectBom {
    projectType = org.cyclonedx.model.Component.Type.APPLICATION
    includeConfigs = listOf("runtimeClasspath")
    xmlOutput.unsetConvention()
    jsonOutput =
        layout.buildDirectory
            .file("reports/bom.json")
            .get()
            .asFile
}

kotlin {
    jvmToolchain(21)
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(project(":plugwerk-api:plugwerk-api-endpoint"))
    implementation(project(":plugwerk-spi"))
    implementation(project(":plugwerk-descriptor"))

    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.boot.starter.liquibase)
    implementation(libs.bucket4j.core)
    implementation(libs.caffeine)

    runtimeOnly(libs.yasson)
    runtimeOnly(libs.postgresql)
    runtimeOnly(project(path = ":plugwerk-server:plugwerk-server-frontend", configuration = "staticResources"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mock.oauth2.server)

    testRuntimeOnly(libs.h2)
}

// Copy the canonical OpenAPI spec into static resources so it is served at /api-docs/openapi.yaml
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("plugwerk-api/src/main/resources/openapi/plugwerk-api.yaml")) {
        into("static/api-docs")
        rename { "openapi.yaml" }
        filter { line ->
            // Replace the OpenAPI info.version (indented with 2 spaces) with the project version
            if (line.matches(Regex("^  version: .+"))) {
                "  version: ${project.version}"
            } else {
                line
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Distribution ZIP: fat JAR + start scripts + Dockerfile + docker-compose
// ---------------------------------------------------------------------------

val serverDistZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Assembles a distribution ZIP with fat JAR, start scripts, Docker files, and docs"
    archiveBaseName.set("plugwerk-server")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    into("plugwerk-server-${project.version}") {
        from(tasks.named("bootJar"))
        from("src/dist") {
            include("start.sh", "start.bat", "Dockerfile", "docker-compose.yml")
            // Ensure shell script is executable
            filePermissions { unix("rwxr-xr-x") }
        }
        from(rootProject.file("LICENSE"))
        from(rootProject.file("README.md"))
    }
}

tasks.named("assemble") {
    dependsOn(serverDistZip)
}

// ---------------------------------------------------------------------------
// Publish only the distribution ZIP to Maven Central (no JAR, no sources)
// ---------------------------------------------------------------------------

publishing {
    publications {
        create<MavenPublication>("serverDist") {
            groupId = project.group.toString()
            artifactId = "plugwerk-server"
            version = project.version.toString()

            artifact(serverDistZip)

            pom {
                name.set("Plugwerk Server")
                description.set("Plugwerk Server distribution — Spring Boot application for plugin registry management")
                url.set("https://github.com/plugwerk/plugwerk")
                packaging = "zip"

                licenses {
                    license {
                        name.set("GNU Affero General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("devtank42")
                        name.set("devtank42 GmbH")
                        url.set("https://github.com/plugwerk")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/plugwerk/plugwerk.git")
                    developerConnection.set("scm:git:ssh://git@github.com:plugwerk/plugwerk.git")
                    url.set("https://github.com/plugwerk/plugwerk")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/plugwerk/plugwerk")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").getOrElse("")
                password = providers.environmentVariable("GITHUB_TOKEN").getOrElse("")
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["serverDist"])
}

tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("signingKey") }
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against PostgreSQL via Testcontainers (requires Docker)"
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    classpath = sourceSets["test"].runtimeClasspath
    testClassesDirs = sourceSets["test"].output.classesDirs
    shouldRunAfter(tasks.named("test"))
}
