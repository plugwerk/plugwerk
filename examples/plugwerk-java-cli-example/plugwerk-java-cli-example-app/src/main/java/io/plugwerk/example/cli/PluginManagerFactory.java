/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.example.cli;

import io.plugwerk.spi.PlugwerkConfig;
import io.plugwerk.spi.PlugwerkPlugin;
import io.plugwerk.spi.extension.PlugwerkMarketplace;
import java.nio.file.Path;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and configures the PF4J {@link PluginManager} used by the CLI host.
 *
 * <p>The {@code plugwerk-client-plugin} ZIP must be present in the plugins directory before calling
 * {@link #create(Path, String, String, String)}. The SDK plugin is loaded and started
 * automatically; its {@link PlugwerkMarketplace} instance is then available via {@link
 * #getMarketplace(PluginManager)}.
 */
public class PluginManagerFactory {

  private static final Logger log = LoggerFactory.getLogger(PluginManagerFactory.class);
  private static final String PLUGIN_ID = PlugwerkPlugin.PLUGIN_ID;

  private PluginManagerFactory() {}

  /**
   * Creates a {@link DefaultPluginManager}, configures the Plugwerk SDK plugin, and starts all
   * plugins.
   *
   * <p>{@link DefaultPluginManager} is used (not {@code JarPluginManager}) because it includes
   * {@code DefaultPluginRepository}, which automatically extracts ZIP files to directories before
   * loading. {@code JarPluginManager} only handles plain {@code .jar} files.
   *
   * @param pluginsDir directory containing the {@code plugwerk-client-plugin-*.zip}
   * @param serverUrl Plugwerk server base URL (e.g. {@code http://localhost:8080})
   * @param namespace namespace slug (e.g. {@code default})
   * @param apiKey optional namespace-scoped API key (may be null or blank)
   * @return started plugin manager ready for marketplace queries
   */
  public static PluginManager create(
      Path pluginsDir, String serverUrl, String namespace, String apiKey) {
    log.debug(
        "Starting PF4J plugin manager with plugins directory: {}", pluginsDir.toAbsolutePath());

    DefaultPluginManager manager = new DefaultPluginManager(pluginsDir.toAbsolutePath());
    manager.loadPlugins();
    manager.startPlugins();

    log.debug(
        "Loaded plugins: {}",
        manager.getPlugins().stream()
            .map(p -> p.getPluginId() + "@" + p.getDescriptor().getVersion())
            .toList());

    // Configure the Plugwerk SDK plugin with server connection details.
    // This must happen after startPlugins() and before getMarketplace().
    PlugwerkConfig.Builder configBuilder =
        new PlugwerkConfig.Builder(serverUrl, namespace)
            .pluginDirectory(pluginsDir.toAbsolutePath());
    if (apiKey != null && !apiKey.isBlank()) {
      configBuilder.apiKey(apiKey);
    }

    PluginWrapper wrapper = manager.getPlugin(PLUGIN_ID);
    if (wrapper == null) {
      throw new IllegalStateException(
          """
                    Plugin '%s' not found.
                    Make sure plugwerk-client-plugin-<version>.zip is present in the plugins directory.
                    Run: cp <main-project>/plugwerk-client-plugin/build/pf4j/*.zip %s/
                    """
              .formatted(PLUGIN_ID, pluginsDir.toAbsolutePath()));
    }
    ((PlugwerkPlugin) wrapper.getPlugin()).configure(configBuilder.build());

    return manager;
  }

  /**
   * Retrieves the {@link PlugwerkMarketplace} instance from the configured plugin.
   *
   * @param manager a started and configured {@link PluginManager}
   * @return the {@link PlugwerkMarketplace} facade
   * @throws IllegalStateException if {@code plugwerk-client-plugin} is not loaded or not configured
   */
  public static PlugwerkMarketplace getMarketplace(PluginManager manager) {
    PluginWrapper wrapper = manager.getPlugin(PLUGIN_ID);
    if (wrapper == null) {
      throw new IllegalStateException(
          """
                    No '%s' plugin found.
                    Make sure plugwerk-client-plugin-<version>.zip is present in the plugins directory.
                    """
              .formatted(PLUGIN_ID));
    }
    return ((PlugwerkPlugin) wrapper.getPlugin()).marketplace();
  }
}
