package io.plugwerk.example.cli;

import io.plugwerk.example.cli.command.InstallCommand;
import io.plugwerk.example.cli.command.ListCommand;
import io.plugwerk.example.cli.command.SearchCommand;
import io.plugwerk.example.cli.command.UninstallCommand;
import io.plugwerk.example.cli.command.UpdateCommand;
import io.plugwerk.spi.extension.PlugwerkMarketplace;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Root picocli command for the Plugwerk CLI example.
 *
 * <p>Global connection options ({@code --server}, {@code --namespace}, {@code --plugins-dir})
 * are declared here and accessible by all subcommands via {@code @ParentCommand}.
 * The PF4J plugin manager and the {@link PlugwerkMarketplace} facade are initialized lazily
 * on the first call to {@link #getMarketplace()}.
 *
 * <p>Usage:
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
            description = "Plugwerk server base URL (env: PLUGWERK_SERVER_URL, default: ${DEFAULT-VALUE})",
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

    // Lazily initialized on first subcommand invocation
    private PluginManager pluginManager;
    private PlugwerkMarketplace marketplace;

    // Set by Main so that subcommands can trigger --help when needed
    private CommandLine commandLine;

    /**
     * Returns the {@link PlugwerkMarketplace} facade, initializing the PF4J plugin manager
     * on first call.
     *
     * @return the marketplace facade connected to the configured Plugwerk server
     */
    public synchronized PlugwerkMarketplace getMarketplace() {
        if (marketplace == null) {
            pluginManager = PluginManagerFactory.create(pluginsDir, serverUrl, namespace);
            marketplace = PluginManagerFactory.getMarketplace(pluginManager);
            registerShutdownHook();
        }
        return marketplace;
    }

    /** Returns the plugin manager; {@code null} if {@link #getMarketplace()} was not yet called. */
    public PluginManager getPluginManager() {
        return pluginManager;
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (pluginManager != null) {
                log.debug("Stopping PF4J plugin manager");
                pluginManager.stopPlugins();
            }
        }));
    }
}
