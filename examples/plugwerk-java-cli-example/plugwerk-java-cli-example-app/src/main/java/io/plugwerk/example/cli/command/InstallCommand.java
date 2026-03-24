package io.plugwerk.example.cli.command;

import io.plugwerk.example.cli.DynamicCommandLoader;
import io.plugwerk.example.cli.PlugwerkCli;
import io.plugwerk.spi.model.InstallResult;
import org.pf4j.PluginManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
            // Load and start only the newly installed plugin, then register its extensions.
            // Using loadPlugin(path) instead of loadPlugins() avoids a PluginAlreadyLoadedException
            // for plugins that are already running (e.g. plugwerk-client-plugin).
            PluginManager pm = parent.getPluginManager();
            if (pm != null) {
                Path artifact = findInstalledArtifact(parent.pluginsDir, pluginId, version);
                if (artifact != null) {
                    pm.loadPlugin(artifact);
                }
                pm.startPlugin(pluginId);
            }
            DynamicCommandLoader.reload(parent.getCommandLine(), pm);
        } else if (result instanceof InstallResult.Failure f) {
            System.err.printf("✗ Installation failed: %s%n", f.getReason());
            System.exit(1);
        }
    }

    private static Path findInstalledArtifact(Path pluginsDir, String pluginId, String version) {
        String prefix = pluginId + "-" + version + ".";
        try (var stream = Files.list(pluginsDir)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix)
                                && (name.endsWith(".zip") || name.endsWith(".jar"));
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
