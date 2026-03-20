package io.plugwerk.common.extension

import org.pf4j.ExtensionPoint

/**
 * Facade extension point providing access to all Plugwerk SDK capabilities.
 * Host applications can retrieve this single extension point to access
 * catalog, installer, and update checker.
 */
interface PlugwerkMarketplace : ExtensionPoint {
    fun catalog(): PlugwerkCatalog
    fun installer(): PlugwerkInstaller
    fun updateChecker(): PlugwerkUpdateChecker
}
