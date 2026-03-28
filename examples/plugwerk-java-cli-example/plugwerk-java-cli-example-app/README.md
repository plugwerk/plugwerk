# plugwerk-java-cli-example-app

A Java CLI application demonstrating how to use the `plugwerk-client-plugin` in a PF4J host
application. Uses [picocli](https://picocli.info/) for command parsing and supports both built-in
marketplace commands and **dynamically loaded plugin commands** contributed by PF4J plugins
downloaded from the Plugwerk server.

## Architecture

```
JVM (Host)
├── Classpath: plugwerk-spi, pf4j, picocli, plugwerk-java-cli-example-api
└── DefaultPluginManager → plugins/plugwerk-client-plugin-<version>.zip
                                → extracted to plugins/<id>/ on first run
                                → exposes PlugwerkMarketplace via @Extension
                           plugins/<any-cli-plugin>.zip
                                → exposes CliCommand extensions (dynamic subcommands)
```

> **Note:** `DefaultPluginManager` is required (not `JarPluginManager`). In PF4J 3.15,
> `JarPluginManager` only accepts plain `.jar` files. `DefaultPluginManager` includes
> `DefaultPluginRepository`, which automatically extracts ZIP files to directories before loading.

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

3. **Build the SDK plugin ZIP** (from the main project root):

   ```bash
   ./gradlew :plugwerk-client-plugin:assemble
   ```

4. **Set up the plugins directory** and copy the SDK plugin ZIP:

   ```bash
   mkdir -p examples/plugwerk-java-cli-example/plugins
   cp plugwerk-client-plugin/build/pf4j/plugwerk-client-plugin-*.zip \
      examples/plugwerk-java-cli-example/plugins/
   ```

   On first run, PF4J automatically extracts the ZIP to a subdirectory. The extracted directory
   is reused on subsequent runs — the ZIP only needs to be replaced when updating the SDK version.

## Authentication

Namespaces on the Plugwerk server require a JWT Bearer token. Obtain one via the login endpoint:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-admin-password>"}' | jq -r .accessToken)
```

> The admin password is auto-generated on first startup and logged once at INFO level.
> Set `PLUGWERK_AUTH_ADMIN_PASSWORD` to use a fixed password (e.g. for CI/CD).

Pass the token to the CLI via `--access-token` or the `PLUGWERK_ACCESS_TOKEN` environment variable:

```bash
# Option A: inline
java -jar plugwerk-java-cli-example-app-*-fat.jar \
    --server=http://localhost:8080 --access-token=$TOKEN list

# Option B: environment variable (persists across commands in the shell session)
export PLUGWERK_ACCESS_TOKEN=$TOKEN
java -jar plugwerk-java-cli-example-app-*-fat.jar --server=http://localhost:8080 list
```

## Running

### Via Fat JAR

```bash
cd examples/
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:assemble

java -jar plugwerk-java-cli-example/plugwerk-java-cli-example-app/build/libs/*-fat.jar \
    --server=http://localhost:8080 --access-token=$TOKEN list
```

### Via Gradle (development)

```bash
cd examples/

# List all plugins in the default namespace
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="--server=http://localhost:8080 --access-token=$TOKEN list"

# Search by query, category, or tag
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="search --category=analytics"

# Install a specific plugin version
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="install io.example.my-plugin 1.0.0"

# Uninstall a plugin
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="uninstall io.example.my-plugin"

# Check for available updates
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="update"

# Apply all available updates
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="update --apply"
```

## Configuration

| Option | Short | Env Variable | Default | Description |
|---|---|---|---|---|
| `--server` | `-s` | `PLUGWERK_SERVER_URL` | `http://localhost:8080` | Plugwerk server base URL |
| `--namespace` | `-n` | `PLUGWERK_NAMESPACE` | `default` | Namespace slug |
| `--plugins-dir` | | `PLUGWERK_PLUGINS_DIR` | `./plugins` | PF4J plugins directory |
| `--access-token` | `-t` | `PLUGWERK_ACCESS_TOKEN` | _(none)_ | JWT Bearer token |

The `--plugins-dir` path is resolved relative to the **current working directory** when the JAR is
executed. Use an absolute path to avoid ambiguity:

```bash
java -jar *-fat.jar \
    --plugins-dir=/absolute/path/to/plugins \
    --server=http://localhost:8080 list
```

## Dynamic Commands

Plugins that implement the `CliCommand` extension point (from `plugwerk-java-cli-example-api`)
contribute new subcommands at runtime. After installing such a plugin, the new subcommand is
immediately available in the same process:

```bash
# Install a plugin that provides an `analyze` subcommand
java -jar *-fat.jar install io.example.analytics-plugin 1.0.0
# → [plugin] Registered dynamic command: analyze

# Use the newly registered command
java -jar *-fat.jar analyze --input=data.csv
```

## Example Plugins

Two ready-made example plugins are included in this project under
`plugwerk-java-cli-example/hello-cli-plugin/` and
`plugwerk-java-cli-example/sysinfo-cli-plugin/`. They demonstrate the full
workflow from build → upload → install → use.

### Build the example plugin ZIPs

```bash
cd examples/
./gradlew :plugwerk-java-cli-example:hello-cli-plugin:assemble \
          :plugwerk-java-cli-example:sysinfo-cli-plugin:assemble
```

The ZIPs are placed in each module's `build/pf4j/` directory.

### Upload to the Plugwerk server

Use the management API to upload a plugin release. The server reads the
`plugwerk.yml` descriptor from inside the JAR automatically.

```bash
# Register the plugin in the default namespace (requires auth token)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-admin-password>"}' | jq -r .accessToken)

# Upload hello-cli-plugin
curl -s -X POST "http://localhost:8080/api/v1/namespaces/default/plugins/hello-cli-plugin/releases" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@plugwerk-java-cli-example/hello-cli-plugin/build/pf4j/hello-cli-plugin-0.1.0-SNAPSHOT.zip"

# Upload sysinfo-cli-plugin
curl -s -X POST "http://localhost:8080/api/v1/namespaces/default/plugins/sysinfo-cli-plugin/releases" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@plugwerk-java-cli-example/sysinfo-cli-plugin/build/pf4j/sysinfo-cli-plugin-0.1.0-SNAPSHOT.zip"
```

### Install and use

```bash
cd examples/
JAR=plugwerk-java-cli-example/plugwerk-java-cli-example-app/build/libs/*-fat.jar

# Install both plugins from the server
java -jar $JAR --server=http://localhost:8080 --access-token=$TOKEN \
    install hello-cli-plugin 0.1.0-SNAPSHOT
java -jar $JAR --server=http://localhost:8080 --access-token=$TOKEN \
    install sysinfo-cli-plugin 0.1.0-SNAPSHOT

# Use the dynamically loaded commands (next invocation — PF4J loads from plugins/)
java -jar $JAR hello --name=Plugwerk
# → Hello, Plugwerk!

java -jar $JAR hello --name=Welt --language=de
# → Hallo, Welt!

java -jar $JAR sysinfo
# → Java:       21.0.3 (Eclipse Adoptium)
# → OS:         Mac OS X 14.5 (aarch64)
# → Heap:       256 MB free / 512 MB allocated / 1024 MB max
# → Processors: 10

java -jar $JAR sysinfo --all    # includes all system properties
```

## Debug Logging

Set `PLUGWERK_LOG_LEVEL=DEBUG` to see PF4J plugin loading details (ZIP extraction, classloader
setup, extension discovery):

```bash
PLUGWERK_LOG_LEVEL=DEBUG java -jar plugwerk-java-cli-example-app-*-fat.jar \
    --server=http://localhost:8080 list
```

## Known Limitations

- **PF4J 3.15 shutdown bug**: `stopPlugins()` throws a `ConcurrentModificationException` during
  JVM shutdown. This is suppressed internally and has no functional impact.
- **ZIP extraction**: The SDK plugin ZIP is extracted to a subdirectory on first run. If you
  replace the ZIP with a newer version, delete the extracted directory manually before the next run:
  ```bash
  rm -rf plugins/plugwerk-client-plugin-*/
  cp plugwerk-client-plugin-<new-version>.zip plugins/
  ```
