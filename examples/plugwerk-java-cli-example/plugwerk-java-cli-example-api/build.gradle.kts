// Extension-point API for dynamically loadable CLI commands.
// Plugin authors depend on this artifact to contribute new subcommands.

dependencies {
    // ExtensionPoint interface lives in pf4j, exposed transitively via plugwerk-spi
    api(libs.plugwerk.spi)
    // picocli CommandLine is part of the public CliCommand contract
    api(libs.picocli)
}
