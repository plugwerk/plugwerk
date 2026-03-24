package io.plugwerk.example.cli.sysinfo;

import io.plugwerk.example.cli.api.CliCommand;
import org.pf4j.Extension;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Example CLI command contributed dynamically via the Plugwerk server.
 *
 * <p>After uploading {@code sysinfo-cli-plugin-<version>.zip} to the server and
 * installing it via {@code cli install sysinfo-cli-plugin <version>}, this
 * subcommand becomes available in the host application:
 *
 * <pre>
 *   cli sysinfo
 *   Java:   21.0.3 (Eclipse Adoptium)
 *   OS:     Mac OS X 14.5 (aarch64)
 *   Heap:   256 MB free / 512 MB max
 * </pre>
 */
@Extension
@Command(
        name = "sysinfo",
        description = "Displays Java runtime and operating system information.",
        mixinStandardHelpOptions = true
)
public class SysinfoCommand implements CliCommand, Runnable {

    @Option(
            names = {"--all", "-a"},
            description = "Include all available system properties"
    )
    private boolean all;

    @Override
    public CommandLine toCommandLine() {
        return new CommandLine(this);
    }

    @Override
    public void run() {
        Runtime rt = Runtime.getRuntime();
        long freeHeapMb  = rt.freeMemory()  / (1024 * 1024);
        long totalHeapMb = rt.totalMemory() / (1024 * 1024);
        long maxHeapMb   = rt.maxMemory()   / (1024 * 1024);

        System.out.printf("Java:       %s (%s)%n",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"));
        System.out.printf("OS:         %s %s (%s)%n",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"));
        System.out.printf("Heap:       %d MB free / %d MB allocated / %d MB max%n",
                freeHeapMb, totalHeapMb, maxHeapMb);
        System.out.printf("Processors: %d%n", rt.availableProcessors());

        if (all) {
            System.out.println();
            System.out.println("--- All System Properties ---");
            System.getProperties().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(Object::toString)))
                    .forEach(e -> System.out.printf("  %-40s = %s%n", e.getKey(), e.getValue()));
        }
    }
}
