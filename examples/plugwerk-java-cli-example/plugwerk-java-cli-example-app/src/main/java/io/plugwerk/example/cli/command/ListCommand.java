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
import io.plugwerk.spi.model.PluginInfo;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Lists all active plugins available in the configured namespace.
 *
 * <p>Usage:
 *
 * <pre>
 *   plugwerk-cli list
 *   plugwerk-cli list --verbose
 * </pre>
 */
@Command(
    name = "list",
    description = "List all available plugins in the namespace.",
    mixinStandardHelpOptions = true)
public class ListCommand implements Runnable {

  @ParentCommand private PlugwerkCli parent;

  @Option(
      names = {"--verbose", "-v"},
      description = "Show additional metadata (provider, license, homepage)")
  private boolean verbose;

  @Override
  public void run() {
    List<PluginInfo> plugins = parent.getMarketplace().catalog().listPlugins();

    if (plugins.isEmpty()) {
      System.out.println("No plugins found in namespace '" + parent.namespace + "'.");
      return;
    }

    if (verbose) {
      printVerbose(plugins);
    } else {
      printTable(plugins);
    }
  }

  private void printTable(List<PluginInfo> plugins) {
    String fmt = "%-40s %-12s %-10s %s%n";
    System.out.printf(fmt, "PLUGIN ID", "VERSION", "STATUS", "NAME");
    System.out.println("-".repeat(90));
    for (PluginInfo p : plugins) {
      System.out.printf(
          fmt,
          truncate(p.getPluginId(), 40),
          orDash(p.getLatestVersion()),
          p.getStatus(),
          truncate(p.getName(), 30));
    }
    System.out.println();
    System.out.printf("%d plugin(s) found.%n", plugins.size());
  }

  private void printVerbose(List<PluginInfo> plugins) {
    for (PluginInfo p : plugins) {
      System.out.println("─".repeat(60));
      System.out.printf("  ID:       %s%n", p.getPluginId());
      System.out.printf("  Name:     %s%n", p.getName());
      System.out.printf("  Version:  %s%n", orDash(p.getLatestVersion()));
      System.out.printf("  Status:   %s%n", p.getStatus());
      if (p.getDescription() != null) System.out.printf("  Desc:     %s%n", p.getDescription());
      if (p.getProvider() != null) System.out.printf("  Provider: %s%n", p.getProvider());
      if (p.getLicense() != null) System.out.printf("  License:  %s%n", p.getLicense());
      if (p.getHomepage() != null) System.out.printf("  Homepage: %s%n", p.getHomepage());
      if (!p.getCategories().isEmpty())
        System.out.printf("  Categories: %s%n", String.join(", ", p.getCategories()));
      if (!p.getTags().isEmpty())
        System.out.printf("  Tags:     %s%n", String.join(", ", p.getTags()));
    }
    System.out.println("─".repeat(60));
    System.out.printf("%n%d plugin(s) found.%n", plugins.size());
  }

  private static String truncate(String s, int max) {
    if (s == null) return "-";
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }

  private static String orDash(String s) {
    return s != null ? s : "-";
  }
}
