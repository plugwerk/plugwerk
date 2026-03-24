package io.plugwerk.example.cli.command;

import io.plugwerk.example.cli.DynamicCommandLoader;
import io.plugwerk.example.cli.PlugwerkCli;
import io.plugwerk.spi.model.InstallResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Downloads and installs a plugin from the Plugwerk server.
 *
 * <p>Usage:
 * <pre>
 *   plugwerk-cli install io.example.my-plugin 1.2.0
 * </pre>
 *
 * <p>After a successful install, any {@link io.plugwerk.example.cli.api.CliCommand} extensions
 * provided by the installed plugin are registered as new subcommands automatically.
 */
@Command(
        name = "install",
        description = "Download and install a plugin from the Plugwerk server.",
        mixinStandardHelpOptions = true)
public class InstallCommand implements Runnable {

    @ParentCommand
    private PlugwerkCli parent;

    @Parameters(index = "0", description = "Plugin ID (e.g. io.example.my-plugin)")
    private String pluginId;

    @Parameters(index = "1", description = "Version to install (e.g. 1.2.0)")
    private String version;

    @Override
    public void run() {
        System.out.printf("Installing %s@%s …%n", pluginId, version);

        InstallResult result = parent.getMarketplace().installer().install(pluginId, version);

        if (result instanceof InstallResult.Success s) {
            System.out.printf("✓ Successfully installed %s@%s%n", s.getPluginId(), s.getVersion());
            DynamicCommandLoader.reload(parent.getCommandLine(), parent.getPluginManager());
        } else if (result instanceof InstallResult.Failure f) {
            System.err.printf("✗ Installation failed: %s%n", f.getReason());
            System.exit(1);
        }
    }
}
