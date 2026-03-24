# plugwerk-java-cli-example-app

A Java CLI application demonstrating how to use the `plugwerk-client-sdk-plugin` in a PF4J host
application. Uses [picocli](https://picocli.info/) for command parsing and supports both built-in
marketplace commands and **dynamically loaded plugin commands** contributed by PF4J plugins
downloaded from the Plugwerk server.

## Architecture

```
JVM (Host)
├── Classpath: plugwerk-spi, pf4j, picocli, plugwerk-java-cli-example-api
└── JarPluginManager → plugins/plugwerk-client-sdk-plugin-<version>.zip
                            → exposes PlugwerkMarketplace via @Extension
                       plugins/<any-cli-plugin>.zip
                            → exposes CliCommand extensions (dynamic subcommands)
```

## Prerequisites

1. **Publish Plugwerk artifacts to Maven Local** (from the main project root):

   ```bash
   ./gradlew publishToMavenLocal
   ```

2. **Start a local Plugwerk server**:

   ```bash
   docker compose up -d postgres
   ./gradlew :plugwerk-server:plugwerk-server-backend:bootRun --args='--spring.profiles.active=dev'
   ```

3. **Set up the plugins directory** and copy the SDK plugin ZIP:

   ```bash
   mkdir -p examples/plugwerk-java-cli-example/plugins
   cp plugwerk-client-sdk-plugin/build/pf4j/plugwerk-client-sdk-plugin-*.zip \
      examples/plugwerk-java-cli-example/plugins/
   ```

## Running

### Via Gradle (development)

```bash
cd examples/

# List all plugins in the default namespace
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="--server=http://localhost:8080 list"

# Search by category
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="search --category=analytics"

# Install a plugin
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="install io.example.my-plugin 1.0.0"

# Check for updates
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="update"

# Apply all available updates
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="update --apply"
```

### Via Fat JAR

```bash
cd examples/
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:assemble

java -jar plugwerk-java-cli-example/plugwerk-java-cli-example-app/build/libs/*-fat.jar \
    --server=http://localhost:8080 list
```

## Configuration

| Option | Env Variable | Default | Description |
|---|---|---|---|
| `--server` | `PLUGWERK_SERVER_URL` | `http://localhost:8080` | Plugwerk server base URL |
| `--namespace` | `PLUGWERK_NAMESPACE` | `default` | Namespace slug |
| `--plugins-dir` | `PLUGWERK_PLUGINS_DIR` | `./plugins` | PF4J plugins directory |

Example with environment variables:

```bash
export PLUGWERK_SERVER_URL=https://plugins.example.com
export PLUGWERK_NAMESPACE=acme
java -jar plugwerk-java-cli-example-app-*-fat.jar list
```

## Dynamic Commands

Plugins that implement the `CliCommand` extension point (from `plugwerk-java-cli-example-api`)
contribute new subcommands at runtime. After installing such a plugin, the new subcommand is
immediately available in the same process:

```bash
# Install a plugin that provides a `analyze` subcommand
plugwerk-cli install io.example.analytics-plugin 1.0.0
# → [plugin] Registered dynamic command: analyze

# Use the newly registered command
plugwerk-cli analyze --input=data.csv
```

## Debug Logging

Set `PLUGWERK_LOG_LEVEL=DEBUG` to see PF4J plugin loading details:

```bash
PLUGWERK_LOG_LEVEL=DEBUG java -jar plugwerk-java-cli-example-app-*-fat.jar list
```
