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

import java.nio.file.Path;
import picocli.CommandLine;

/**
 * Entry point for the Plugwerk Java CLI example.
 *
 * <p>Run via Gradle:
 *
 * <pre>
 * ./gradlew :plugwerk-java-cli-example-app:run \
 *     --args="--server=http://localhost:8080 list"
 * </pre>
 *
 * <p>Or with the fat JAR after {@code ./gradlew assemble}:
 *
 * <pre>
 * java -jar build/libs/plugwerk-java-cli-example-app-*-fat.jar list
 * </pre>
 */
public class Main {

  public static void main(String[] args) {
    PlugwerkCli cli = new PlugwerkCli();
    CommandLine commandLine = new CommandLine(cli);
    cli.setCommandLine(commandLine);

    // Pre-parse global options so that pluginsDir, serverUrl etc. are populated
    // from command-line args and env vars before we initialize the plugin manager.
    // Errors (e.g. unknown subcommand before dynamic commands are registered) are
    // intentionally ignored — execute() will handle them properly.
    boolean helpRequested = false;
    try {
      CommandLine.ParseResult parseResult = commandLine.parseArgs(args);
      helpRequested = parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested();
    } catch (Exception ignored) {
    }

    // Short-circuit for --help / --version: print usage without initializing the
    // plugin manager (which requires the plugwerk-client plugin ZIP to be present).
    if (helpRequested) {
      int exitCode = commandLine.execute(args);
      System.exit(exitCode);
      return;
    }

    // Eagerly initialize the plugin manager so that already-installed plugins are
    // loaded and their CliCommand extensions are registered as picocli subcommands
    // before execute() tries to match the user's subcommand name.
    // parseArgs() may have failed before applying picocli defaults, so fall back to
    // env vars / hardcoded defaults for any field that is still null.
    Path pluginsDir =
        cli.pluginsDir != null
            ? cli.pluginsDir
            : Path.of(System.getenv().getOrDefault("PLUGWERK_PLUGINS_DIR", "./plugins"));
    String serverUrl =
        cli.serverUrl != null
            ? cli.serverUrl
            : System.getenv().getOrDefault("PLUGWERK_SERVER_URL", "http://localhost:8080");
    String namespace =
        cli.namespace != null
            ? cli.namespace
            : System.getenv().getOrDefault("PLUGWERK_NAMESPACE", "default");

    org.pf4j.PluginManager pm =
        PluginManagerFactory.create(pluginsDir, serverUrl, namespace, cli.apiKey);
    cli.setPluginManager(pm);
    DynamicCommandLoader.loadAll(commandLine, pm);

    int exitCode = commandLine.execute(args);
    System.exit(exitCode);
  }
}
