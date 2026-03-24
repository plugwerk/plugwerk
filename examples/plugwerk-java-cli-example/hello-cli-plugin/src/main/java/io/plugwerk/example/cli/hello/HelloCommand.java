package io.plugwerk.example.cli.hello;

import io.plugwerk.example.cli.api.CliCommand;
import org.pf4j.Extension;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Example CLI command contributed dynamically via the Plugwerk server.
 *
 * <p>After uploading {@code hello-cli-plugin-<version>.zip} to the server and
 * installing it via {@code cli install hello-cli-plugin <version>}, this
 * subcommand becomes available in the host application:
 *
 * <pre>
 *   cli hello --name=World
 *   Hello, World!
 * </pre>
 */
@Extension
@Command(
        name = "hello",
        description = "Greets the specified name (or the world by default).",
        mixinStandardHelpOptions = true
)
public class HelloCommand implements CliCommand, Runnable {

    @Option(
            names = {"--name", "-n"},
            description = "Name to greet (default: ${DEFAULT-VALUE})",
            defaultValue = "World"
    )
    private String name;

    @Option(
            names = {"--language", "-l"},
            description = "Language for the greeting: en, de, es (default: ${DEFAULT-VALUE})",
            defaultValue = "en"
    )
    private String language;

    @Override
    public CommandLine toCommandLine() {
        return new CommandLine(this);
    }

    @Override
    public void run() {
        String greeting = switch (language.toLowerCase()) {
            case "de" -> "Hallo, " + name + "!";
            case "es" -> "Hola, " + name + "!";
            default   -> "Hello, " + name + "!";
        };
        System.out.println(greeting);
    }
}
