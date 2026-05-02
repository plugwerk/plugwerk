plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.openapi.generator)
}

kotlin {
    jvmToolchain(21)
    // Preserve parameter names in bytecode so Spring Bean Validation can
    // produce field-named messages like "size: must be ..." instead of
    // "arg2: must be ..." for @RequestParam violations (#430).
    compilerOptions {
        javaParameters = true
    }
}

dependencies {
    api(project(":plugwerk-api:plugwerk-api-model"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    implementation(libs.swagger.annotations)
    implementation(libs.jakarta.validation.api)
    implementation(libs.jakarta.annotation.api)
    implementation(libs.jackson.annotations)
    implementation(libs.spring.boot.starter.web)
}

val specFile = "${rootProject.projectDir}/plugwerk-api/src/main/resources/openapi/plugwerk-api.yaml"

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set(specFile)
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
    globalProperties.set(
        mapOf(
            "apis" to "",
            "supportingFiles" to "",
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
