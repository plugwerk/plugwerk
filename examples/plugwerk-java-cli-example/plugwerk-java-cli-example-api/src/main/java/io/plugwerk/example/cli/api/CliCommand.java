package io.plugwerk.example.cli.api;

import org.pf4j.ExtensionPoint;
import picocli.CommandLine;

/**
 * Extension point for CLI commands that can be dynamically loaded from the Plugwerk server.
 *
 * <p>Implement this interface in a PF4J plugin and annotate the implementing class with
 * {@code @picocli.CommandLine.Command} to contribute new subcommands to the
 * {@code plugwerk-cli} host application at runtime.
 *
 * <p>The plugin JAR must be uploaded to the Plugwerk server. When the host application
 * installs the plugin via {@code plugwerk-cli install <id> <version>}, the new subcommand
 * becomes available immediately after the next CLI invocation.
 *
 * <p>Example implementation:
 * <pre>{@code
 * @Extension
 * @Command(name = "analyze", description = "Analyze collected data")
 * public class AnalyzeCommand implements CliCommand, Runnable {
 *
 *     @Option(names = "--input", required = true, description = "Input file")
 *     private Path inputFile;
 *
 *     @Override
 *     public CommandLine toCommandLine() {
 *         return new CommandLine(this);
 *     }
 *
 *     @Override
 *     public void run() {
 *         System.out.println("Analyzing " + inputFile);
 *     }
 * }
 * }</pre>
 *
 * <p>Kotlin example:
 * <pre>{@code
 * @Extension
 * @Command(name = "analyze", description = "Analyze collected data")
 * class AnalyzeCommand : CliCommand, Runnable {
 *
 *     @Option(names = ["--input"], required = true)
 *     lateinit var inputFile: Path
 *
 *     override fun toCommandLine() = CommandLine(this)
 *
 *     override fun run() { println("Analyzing $inputFile") }
 * }
 * }</pre>
 */
public interface CliCommand extends ExtensionPoint {

    /**
     * Returns a fully configured {@link CommandLine} instance for this command.
     *
     * <p>The implementing class should be annotated with
     * {@link picocli.CommandLine.Command @Command} to declare the subcommand name,
     * description, and options. The simplest implementation returns
     * {@code new CommandLine(this)} when the implementing class itself is the command.
     *
     * @return the picocli entry point for this extension; must not be {@code null}
     */
    CommandLine toCommandLine();
}
