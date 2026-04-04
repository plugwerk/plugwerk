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

import io.plugwerk.example.cli.command.InstallCommand;
import io.plugwerk.example.cli.command.ListCommand;
import io.plugwerk.example.cli.command.SearchCommand;
import io.plugwerk.example.cli.command.UninstallCommand;
import io.plugwerk.example.cli.command.UpdateCommand;
import io.plugwerk.spi.extension.PlugwerkMarketplace;
import java.nio.file.Path;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Root picocli command for the Plugwerk CLI example.
 *
 * <p>Global connection options ({@code --server}, {@code --namespace}, {@code --plugins-dir}) are
 * declared here and accessible by all subcommands via {@code @ParentCommand}. The PF4J plugin
 * manager and the {@link PlugwerkMarketplace} facade are initialized lazily on the first call to
 * {@link #getMarketplace()}.
 *
 * <p>Usage:
 *
 * <pre>
 *   plugwerk-cli [--server=URL] [--namespace=NS] [--plugins-dir=DIR] &lt;subcommand&gt; [args...]
 * </pre>
 */
@Command(
    name = "plugwerk-cli",
    mixinStandardHelpOptions = true,
    version = "plugwerk-cli 0.1.0-SNAPSHOT",
    description = "CLI for managing PF4J plugins via the Plugwerk marketplace.",
    subcommands = {
      ListCommand.class,
      SearchCommand.class,
      InstallCommand.class,
      UninstallCommand.class,
      UpdateCommand.class,
      CommandLine.HelpCommand.class,
    })
public class PlugwerkCli implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(PlugwerkCli.class);

  @Option(
      names = {"--server", "-s"},
      description =
          "Plugwerk server base URL (env: PLUGWERK_SERVER_URL, default: ${DEFAULT-VALUE})",
      defaultValue = "${PLUGWERK_SERVER_URL:-http://localhost:8080}")
  public String serverUrl;

  @Option(
      names = {"--namespace", "-n"},
      description = "Namespace slug (env: PLUGWERK_NAMESPACE, default: ${DEFAULT-VALUE})",
      defaultValue = "${PLUGWERK_NAMESPACE:-default}")
  public String namespace;

  @Option(
      names = {"--plugins-dir"},
      description = "PF4J plugins directory (env: PLUGWERK_PLUGINS_DIR, default: ${DEFAULT-VALUE})",
      defaultValue = "${PLUGWERK_PLUGINS_DIR:-./plugins}")
  public Path pluginsDir;

  @Option(
      names = {"--access-token", "-t"},
      description = "Bearer token for server authentication (env: PLUGWERK_ACCESS_TOKEN)",
      defaultValue = "${PLUGWERK_ACCESS_TOKEN:}")
  public String accessToken;

  // Lazily initialized on first subcommand invocation
  private PluginManager pluginManager;
  private PlugwerkMarketplace marketplace;

  // Set by Main so that subcommands can trigger --help when needed
  private CommandLine commandLine;

  /**
   * Returns the {@link PlugwerkMarketplace} facade, initializing the PF4J plugin manager on first
   * call. If {@link #setPluginManager(PluginManager)} was already called (eager startup init), the
   * existing manager is reused.
   *
   * @return the marketplace facade connected to the configured Plugwerk server
   */
  public synchronized PlugwerkMarketplace getMarketplace() {
    if (marketplace == null) {
      if (pluginManager == null) {
        pluginManager = PluginManagerFactory.create(pluginsDir, serverUrl, namespace, accessToken);
        registerShutdownHook();
      }
      marketplace = PluginManagerFactory.getMarketplace(pluginManager);
    }
    return marketplace;
  }

  /**
   * Returns the plugin manager; {@code null} if neither eager init nor {@link #getMarketplace()}
   * was called.
   */
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  /**
   * Pre-sets the plugin manager created during eager startup initialization. Must be called before
   * {@link #getMarketplace()} to avoid double initialization.
   */
  public synchronized void setPluginManager(PluginManager pm) {
    if (this.pluginManager == null) {
      this.pluginManager = pm;
      registerShutdownHook();
    }
  }

  public void setCommandLine(CommandLine commandLine) {
    this.commandLine = commandLine;
  }

  public CommandLine getCommandLine() {
    return commandLine;
  }

  @Override
  public void run() {
    // Called when plugwerk-cli is invoked without a subcommand — print help
    commandLine.usage(System.out);
  }

  private void registerShutdownHook() {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (pluginManager != null) {
                    log.debug("Stopping PF4J plugin manager");
                    try {
                      pluginManager.stopPlugins();
                    } catch (Exception e) {
                      // PF4J 3.15 has a known bug where stopPlugins() causes a
                      // ConcurrentModificationException during JVM shutdown. Safe to ignore.
                      log.debug("Suppressed exception during plugin manager shutdown", e);
                    }
                  }
                }));
  }
}
