package io.plugwerk.example.cli;

import io.plugwerk.example.cli.api.CliCommand;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.List;

/**
 * Discovers {@link CliCommand} extensions from loaded PF4J plugins and registers them
 * as picocli subcommands on the root {@link CommandLine}.
 *
 * <p>Call {@link #loadAll(CommandLine, PluginManager)} once at startup after all plugins
 * have been started. Call {@link #reload(CommandLine, PluginManager)} after installing
 * a new plugin to pick up any commands it contributes.
 *
 * <p>Example flow:
 * <ol>
 *   <li>PF4J starts and loads {@code plugwerk-client-sdk-plugin}.</li>
 *   <li>{@code DynamicCommandLoader.loadAll()} scans for {@link CliCommand} extensions — none yet.</li>
 *   <li>User runs {@code plugwerk-cli install io.example.analytics-plugin 1.0.0}.</li>
 *   <li>{@code InstallCommand} calls {@code DynamicCommandLoader.reload()} after install.</li>
 *   <li>The {@code analyze} subcommand is now registered and usable in the same process.</li>
 * </ol>
 */
public class DynamicCommandLoader {

    private static final Logger log = LoggerFactory.getLogger(DynamicCommandLoader.class);

    private DynamicCommandLoader() {}

    /**
     * Scans all loaded plugins for {@link CliCommand} extensions and registers any new ones
     * as subcommands on {@code rootCommand}.
     *
     * <p>Commands already registered (by name) are skipped to avoid duplicates.
     *
     * @param rootCommand the picocli root {@link CommandLine} to add subcommands to
     * @param manager     the running {@link PluginManager}
     */
    public static void loadAll(CommandLine rootCommand, PluginManager manager) {
        if (manager == null) {
            return;
        }

        List<CliCommand> extensions = manager.getExtensions(CliCommand.class);
        log.debug("Found {} CliCommand extension(s) in loaded plugins", extensions.size());

        for (CliCommand extension : extensions) {
            CommandLine subCommand = extension.toCommandLine();
            String name = subCommand.getCommandName();

            if (rootCommand.getSubcommands().containsKey(name)) {
                log.debug("Skipping already-registered subcommand '{}'", name);
                continue;
            }

            rootCommand.addSubcommand(name, subCommand);
            log.debug("Registered dynamic subcommand '{}'", name);
            System.out.printf("  [plugin] Registered dynamic command: %s%n", name);
        }
    }

    /**
     * Convenience method to call after installing a new plugin.
     * Equivalent to {@link #loadAll(CommandLine, PluginManager)}.
     *
     * @param rootCommand the picocli root {@link CommandLine}
     * @param manager     the running {@link PluginManager}
     */
    public static void reload(CommandLine rootCommand, PluginManager manager) {
        loadAll(rootCommand, manager);
    }
}
