// Extension-point API for dynamically loadable web page contributions.
// Plugin authors depend on this artifact to contribute new pages to the web app.

dependencies {
    // ExtensionPoint interface lives in pf4j
    compileOnly(libs.pf4j)
}
