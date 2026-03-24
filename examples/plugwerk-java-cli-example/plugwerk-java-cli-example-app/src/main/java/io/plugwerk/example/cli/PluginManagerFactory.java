package io.plugwerk.example.cli;

import io.plugwerk.spi.extension.PlugwerkMarketplace;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Creates and configures the PF4J {@link PluginManager} used by the CLI host.
 *
 * <p>The {@code plugwerk-client-plugin} ZIP must be present in the plugins directory
 * before calling {@link #create(Path, String, String)}. The SDK plugin is loaded and started
 * automatically; its {@link PlugwerkMarketplace} extension is then available via
 * {@link #getMarketplace(PluginManager)}.
 *
 * <p>System properties consumed by the SDK plugin (set by this factory before starting plugins):
 * <ul>
 *   <li>{@code plugwerk.serverUrl} — Plugwerk server base URL</li>
 *   <li>{@code plugwerk.namespace} — namespace slug</li>
 *   <li>{@code plugwerk.cacheDirectory} — directory where installed plugin artifacts are stored</li>
 * </ul>
 */
public class PluginManagerFactory {

    private static final Logger log = LoggerFactory.getLogger(PluginManagerFactory.class);

    private PluginManagerFactory() {}

    /**
     * Creates a {@link DefaultPluginManager}, sets the SDK system properties, and starts all plugins.
     *
     * <p>{@link DefaultPluginManager} is used (not {@code JarPluginManager}) because it includes
     * {@code DefaultPluginRepository}, which automatically extracts ZIP files to directories before
     * loading. {@code JarPluginManager} only handles plain {@code .jar} files.
     *
     * @param pluginsDir  directory containing the {@code plugwerk-client-plugin-*.zip}
     * @param serverUrl   Plugwerk server base URL (e.g. {@code http://localhost:8080})
     * @param namespace   namespace slug (e.g. {@code default})
     * @param accessToken optional Bearer token for authenticated servers (may be null or blank)
     * @return started plugin manager ready for extension queries
     */
    public static PluginManager create(Path pluginsDir, String serverUrl, String namespace, String accessToken) {
        // System properties are read by PlugwerkMarketplaceImpl's no-arg constructor,
        // which is called by PF4J via reflection when the plugin is started.
        // They must be set BEFORE startPlugins() is called.
        System.setProperty("plugwerk.serverUrl", serverUrl);
        System.setProperty("plugwerk.namespace", namespace);
        System.setProperty("plugwerk.cacheDirectory", pluginsDir.toAbsolutePath().toString());
        if (accessToken != null && !accessToken.isBlank()) {
            System.setProperty("plugwerk.accessToken", accessToken);
        }

        log.debug("Starting PF4J plugin manager with plugins directory: {}", pluginsDir.toAbsolutePath());

        DefaultPluginManager manager = new DefaultPluginManager(pluginsDir.toAbsolutePath());
        manager.loadPlugins();
        manager.startPlugins();

        log.debug("Loaded plugins: {}", manager.getPlugins().stream()
                .map(p -> p.getPluginId() + "@" + p.getDescriptor().getVersion())
                .toList());

        return manager;
    }

    /**
     * Retrieves the {@link PlugwerkMarketplace} extension from the running plugin manager.
     *
     * @param manager a started {@link PluginManager}
     * @return the first available {@link PlugwerkMarketplace} extension
     * @throws IllegalStateException if {@code plugwerk-client-plugin} is not loaded
     */
    public static PlugwerkMarketplace getMarketplace(PluginManager manager) {
        List<PlugwerkMarketplace> extensions = manager.getExtensions(PlugwerkMarketplace.class);
        if (extensions.isEmpty()) {
            throw new IllegalStateException("""
                    No PlugwerkMarketplace extension found.
                    Make sure plugwerk-client-plugin-<version>.zip is present in the plugins directory.
                    Run: cp <main-project>/plugwerk-client-plugin/build/pf4j/*.zip <plugins-dir>/
                    """);
        }
        return extensions.get(0);
    }
}
