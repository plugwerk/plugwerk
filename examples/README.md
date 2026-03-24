# Plugwerk Examples

Example projects demonstrating how to integrate the Plugwerk client SDK into various host application types.

## Prerequisites

Before building any example, publish the Plugwerk artifacts to your local Maven repository:

```bash
# From the main Plugwerk project root:
cd /path/to/plugwerk
./gradlew publishToMavenLocal
```

## Examples

| Directory | Description |
|---|---|
| [`plugwerk-java-cli-example`](plugwerk-java-cli-example/) | Java CLI application using picocli + PF4J with dynamic plugin commands |

## Building

Each example is a standalone Gradle project. Build from the `examples/` directory:

```bash
cd examples/

# Build all examples
./gradlew build

# Build a specific example
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:build
```

## Running

See the README in each example directory for specific run instructions.
