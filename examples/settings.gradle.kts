rootProject.name = "plugwerk-examples"

// Each example is an independent Gradle project composed via includeBuild().
// To add a new example, add another includeBuild() directive here.
includeBuild("plugwerk-java-cli-example")
includeBuild("plugwerk-springboot-thymeleaf-example")
