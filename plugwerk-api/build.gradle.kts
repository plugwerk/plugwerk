plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.openapi.generator)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    implementation(platform(libs.jackson.bom))

    implementation(libs.swagger.annotations)
    implementation(libs.jakarta.validation.api)
    implementation(libs.jakarta.annotation.api)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.starter.web)
}

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$projectDir/src/main/resources/openapi/plugwerk-api.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated")
    apiPackage.set("io.plugwerk.api")
    modelPackage.set("io.plugwerk.api.model")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useTags" to "true",
            "useSpringBoot3" to "true",
            "documentationProvider" to "none",
            "serializationLibrary" to "jackson",
            "enumPropertyNaming" to "UPPERCASE",
            "reactive" to "false",
        ),
    )
}

sourceSets {
    main {
        kotlin {
            srcDir("${layout.buildDirectory.get()}/generated/src/main/kotlin")
        }
    }
}

tasks.compileKotlin {
    dependsOn(tasks.openApiGenerate)
}
