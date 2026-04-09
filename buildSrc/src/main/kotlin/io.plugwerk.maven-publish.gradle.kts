plugins {
    java
    `maven-publish`
    signing
}

// Sources JAR
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set(project.name)
                description.set(provider { project.description ?: project.name })
                url.set("https://github.com/plugwerk/plugwerk")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

// Skip signing on local builds that lack GPG keys
tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("signingKey") }
}
