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
package io.plugwerk.example.cli.command;

import io.plugwerk.example.cli.PlugwerkCli;
import io.plugwerk.spi.model.InstallResult;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Unloads and removes an installed plugin.
 *
 * <p>Usage:
 *
 * <pre>
 *   plugwerk-cli uninstall io.example.my-plugin
 * </pre>
 */
@Command(
    name = "uninstall",
    description = "Unload and remove an installed plugin.",
    mixinStandardHelpOptions = true)
public class UninstallCommand implements Runnable {

  @ParentCommand private PlugwerkCli parent;

  @Parameters(index = "0", description = "Plugin ID to remove (e.g. io.example.my-plugin)")
  private String pluginId;

  @Override
  public void run() {
    System.out.printf("Uninstalling %s …%n", pluginId);

    // Stop and unload the plugin from the running PF4J plugin manager before
    // deleting the files, so the classloader releases any file handles.
    PluginManager pm = parent.getPluginManager();
    if (pm != null && pm.getPlugin(pluginId) != null) {
      if (pm.getPlugin(pluginId).getPluginState() == PluginState.STARTED) {
        pm.stopPlugin(pluginId);
      }
      pm.unloadPlugin(pluginId);
    }

    InstallResult result = parent.getMarketplace().installer().uninstall(pluginId);

    if (result instanceof InstallResult.Success s) {
      System.out.printf("✓ Successfully uninstalled %s%n", s.getPluginId());
    } else if (result instanceof InstallResult.Failure f) {
      System.err.printf("✗ Uninstall failed: %s%n", f.getReason());
      System.exit(1);
    }
  }
}
