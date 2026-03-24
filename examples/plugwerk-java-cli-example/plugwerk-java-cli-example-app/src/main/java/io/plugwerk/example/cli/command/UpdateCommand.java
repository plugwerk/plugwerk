package io.plugwerk.example.cli.command;

import io.plugwerk.example.cli.PlugwerkCli;
import io.plugwerk.spi.extension.PlugwerkMarketplace;
import io.plugwerk.spi.model.InstallResult;
import io.plugwerk.spi.model.UpdateInfo;
import org.pf4j.PluginWrapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checks for available plugin updates and optionally installs them.
 *
 * <p>Usage:
 * <pre>
 *   plugwerk-cli update           # check only, print available updates
 *   plugwerk-cli update --apply   # check and install all available updates
 * </pre>
 */
@Command(
        name = "update",
        description = "Check for available plugin updates and optionally install them.",
        mixinStandardHelpOptions = true)
public class UpdateCommand implements Runnable {

    @ParentCommand
    private PlugwerkCli parent;

    @Option(names = {"--apply"}, description = "Install all available updates (default: check only)")
    private boolean apply;

    @Override
    public void run() {
        PlugwerkMarketplace marketplace = parent.getMarketplace();

        // Build a map of installed plugin ID → current version from PF4J plugin manager
        Map<String, String> installed = parent.getPluginManager().getPlugins().stream()
                .collect(Collectors.toMap(
                        PluginWrapper::getPluginId,
                        pw -> pw.getDescriptor().getVersion()));

        if (installed.isEmpty()) {
            System.out.println("No plugins currently installed.");
            return;
        }

        List<UpdateInfo> updates = marketplace.updateChecker().checkForUpdates(installed);

        if (updates.isEmpty()) {
            System.out.println("All plugins are up-to-date.");
            return;
        }

        System.out.printf("%-40s %-12s → %s%n", "PLUGIN ID", "CURRENT", "AVAILABLE");
        System.out.println("-".repeat(70));
        for (UpdateInfo u : updates) {
            System.out.printf("%-40s %-12s → %s%n",
                    u.getPluginId(), u.getCurrentVersion(), u.getAvailableVersion());
        }
        System.out.println();

        if (!apply) {
            System.out.printf("%d update(s) available. Run with --apply to install.%n", updates.size());
            return;
        }

        System.out.println("Applying updates …");
        int success = 0;
        int failed = 0;
        for (UpdateInfo u : updates) {
            InstallResult result = marketplace.installer().install(u.getPluginId(), u.getAvailableVersion());
            if (result instanceof InstallResult.Success s) {
                System.out.printf("  ✓ %s %s → %s%n", s.getPluginId(), u.getCurrentVersion(), s.getVersion());
                success++;
            } else if (result instanceof InstallResult.Failure f) {
                System.err.printf("  ✗ %s: %s%n", f.getPluginId(), f.getReason());
                failed++;
            }
        }
        System.out.printf("%nDone: %d updated, %d failed.%n", success, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
