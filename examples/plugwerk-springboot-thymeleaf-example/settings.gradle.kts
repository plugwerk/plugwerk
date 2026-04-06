rootProject.name = "plugwerk-springboot-thymeleaf-example"

// Composite build: resolve plugwerk-spi (and other io.plugwerk modules)
// directly from the main project — no publishToMavenLocal needed.
includeBuild("../..")

include("plugwerk-springboot-thymeleaf-example-api")
include("plugwerk-example-plugin-sysinfo")
include("plugwerk-example-plugin-env")
