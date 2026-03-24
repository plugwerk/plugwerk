package io.plugwerk.example.cli;

import picocli.CommandLine;

/**
 * Entry point for the Plugwerk Java CLI example.
 *
 * <p>Run via Gradle:
 * <pre>
 * ./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
 *     --args="--server=http://localhost:8080 list"
 * </pre>
 *
 * <p>Or with the fat JAR after {@code ./gradlew assemble}:
 * <pre>
 * java -jar build/libs/plugwerk-java-cli-example-app-*-fat.jar list
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        PlugwerkCli cli = new PlugwerkCli();
        CommandLine commandLine = new CommandLine(cli);
        cli.setCommandLine(commandLine);

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}
