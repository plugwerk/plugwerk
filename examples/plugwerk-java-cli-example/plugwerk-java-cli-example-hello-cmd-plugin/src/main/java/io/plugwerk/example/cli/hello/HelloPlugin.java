package io.plugwerk.example.cli.hello;

import org.pf4j.Plugin;

/**
 * PF4J plugin entry point for the hello-cli-plugin.
 *
 * <p>Contributes the {@link HelloCommand} subcommand to the CLI host application
 * via the {@link io.plugwerk.example.cli.api.CliCommand} extension point.
 */
public class HelloPlugin extends Plugin {
}
