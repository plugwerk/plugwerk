# plugwerk-java-cli-example

A Java CLI application demonstrating the full Plugwerk workflow end-to-end:
plugins are published to a running Plugwerk server and then installed, updated,
and invoked at runtime via PF4J's dynamic extension mechanism.

## Project Structure

```
plugwerk-java-cli-example/
├── plugwerk-java-cli-example-api/                    # Extension-point interface: CliCommand
├── plugwerk-java-cli-example-app/                    # Host CLI application (picocli + PF4J)
├── plugwerk-java-cli-example-hello-cmd-plugin/       # Example plugin: "hello" greeting command
└── plugwerk-java-cli-example-sysinfo-cmd-plugin/     # Example plugin: "sysinfo" system info command
```

### plugwerk-java-cli-example-api

Defines `CliCommand`, the PF4J `ExtensionPoint` interface that every dynamically
loaded CLI command must implement. Plugin authors depend on this artifact.

```java
public interface CliCommand extends ExtensionPoint {
    CommandLine toCommandLine();
}
```

### plugwerk-java-cli-example-app

The host application. Provides built-in subcommands:

| Subcommand  | Description |
|-------------|-------------|
| `list`      | List all published plugins in the configured namespace |
| `search`    | Search by keyword, category, tag, or system-version compatibility |
| `install`   | Download and install a plugin from the Plugwerk server |
| `uninstall` | Stop, unload, and remove an installed plugin |
| `update`    | Check for or apply available updates |

Plugins that implement `CliCommand` are loaded as PF4J extensions and registered
as additional picocli subcommands at startup. After `install`, newly contributed
commands are available immediately in the same process. After `uninstall`, the
plugin is stopped and unloaded — and its extracted directory is removed — so it
is gone on the next invocation.

### plugwerk-java-cli-example-hello-cmd-plugin / plugwerk-java-cli-example-sysinfo-cmd-plugin

Ready-made example plugins that can be uploaded to the server and installed via
the CLI to demonstrate dynamic command loading:

| Plugin | Plugwerk ID | Subcommand | What it does |
|--------|-------------|------------|--------------|
| `plugwerk-java-cli-example-hello-cmd-plugin` | same | `hello` | Greets with `--name` and `--language` (en/de/es) |
| `plugwerk-java-cli-example-sysinfo-cmd-plugin` | same | `sysinfo` | Prints Java/OS/heap info; `--all` for all system properties |

---

## Prerequisites

### 1. Publish Plugwerk artifacts to Maven Local

The example build resolves `plugwerk-spi` and `plugwerk-client-plugin` from
Maven Local. Run once from the **main project root**:

```bash
./gradlew publishToMavenLocal
```

### 2. Start a local Plugwerk server

```bash
# Start the database
docker compose up -d postgres

# Start the server with the dev profile
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun \
    --args='--spring.profiles.active=dev'
```

The server listens on `http://localhost:8080`.

### 3. Build the SDK plugin ZIP and copy it to the plugins directory

The `plugwerk-client-plugin` is the bridge between the CLI app and the server.
It must be present in `plugins/` before the CLI app starts.

```bash
# Build the ZIP (from the main project root)
./gradlew :plugwerk-client-plugin:assemble

# Copy to the plugins directory
mkdir -p examples/plugwerk-java-cli-example/plugins
cp plugwerk-client-plugin/build/pf4j/plugwerk-client-plugin-*.zip \
   examples/plugwerk-java-cli-example/plugins/
```

PF4J extracts the ZIP to a subdirectory on first run and reuses it on subsequent
runs. Only re-copy if you update the SDK version (and delete the extracted directory).

---

## Authentication

The Plugwerk server requires a JWT Bearer token for write operations (upload,
approve) and for namespaces configured as non-public.

### Obtaining an access token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}' | jq -r .accessToken)

echo $TOKEN   # eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

> The dev profile ships with a hardcoded user `test` / `test`.

### Passing the token to the CLI

```bash
# Option A: inline flag
java -jar *-fat.jar --server=http://localhost:8080 --access-token=$TOKEN list

# Option B: environment variable (persists in the shell session)
export PLUGWERK_ACCESS_TOKEN=$TOKEN
java -jar *-fat.jar --server=http://localhost:8080 list
```

---

## Uploading Example Plugins to the Server

### 1. Build the plugin ZIPs

```bash
cd examples/
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-hello-cmd-plugin:assemble \
          :plugwerk-java-cli-example:plugwerk-java-cli-example-sysinfo-cmd-plugin:assemble
```

Artifacts are written to each module's `build/pf4j/` directory:
- `plugwerk-java-cli-example-hello-cmd-plugin/build/pf4j/plugwerk-java-cli-example-hello-cmd-plugin-0.1.0-SNAPSHOT.zip`
- `plugwerk-java-cli-example-sysinfo-cmd-plugin/build/pf4j/plugwerk-java-cli-example-sysinfo-cmd-plugin-0.1.0-SNAPSHOT.zip`

### 2. Create a namespace (if it does not exist yet)

```bash
curl -s -X POST http://localhost:8080/api/v1/namespaces \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"slug":"default","ownerOrg":"default"}'
```

If the namespace already exists the server returns HTTP 409 — that is fine.

### 3. Upload the plugin releases

The server reads the `plugwerk.yml` descriptor embedded inside the JAR
(within the ZIP) automatically. No manual metadata entry is required.

```bash
# Upload hello-cmd-plugin
curl -s -X POST \
  "http://localhost:8080/api/v1/namespaces/default/releases" \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifact=@plugwerk-java-cli-example-hello-cmd-plugin/build/pf4j/plugwerk-java-cli-example-hello-cmd-plugin-0.1.0-SNAPSHOT.zip"

# Upload sysinfo-cmd-plugin
curl -s -X POST \
  "http://localhost:8080/api/v1/namespaces/default/releases" \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifact=@plugwerk-java-cli-example-sysinfo-cmd-plugin/build/pf4j/plugwerk-java-cli-example-sysinfo-cmd-plugin-0.1.0-SNAPSHOT.zip"
```

A successful upload returns HTTP 201 with the release details in JSON.

### 4. Publish the releases (DRAFT → PUBLISHED)

Newly uploaded releases have status `DRAFT` and are not visible in the catalog
until explicitly published. Use the management API to approve them:

```bash
# Get the release ID from the upload response, or look it up:
curl -s "http://localhost:8080/api/v1/namespaces/default/plugins/plugwerk-java-cli-example-hello-cmd-plugin/releases/0.1.0-SNAPSHOT" \
  -H "Authorization: Bearer $TOKEN" | jq .id

# Approve (DRAFT → PUBLISHED) — replace <release-id> with the UUID from above
curl -s -X POST \
  "http://localhost:8080/api/v1/namespaces/default/reviews/<release-id>/approve" \
  -H "Authorization: Bearer $TOKEN"
```

Repeat for `sysinfo-cmd-plugin`. After approval both plugins appear in `list` and
are installable via the CLI.

### 5. Verify uploads via the catalog API

```bash
# List all plugins in the namespace (latestVersion is only set once PUBLISHED)
curl -s "http://localhost:8080/api/v1/namespaces/default/plugins" | jq .

# Show a specific plugin with its releases
curl -s "http://localhost:8080/api/v1/namespaces/default/plugins/plugwerk-java-cli-example-hello-cmd-plugin" | jq .

# Show the release detail (includes the release ID needed for approval)
curl -s "http://localhost:8080/api/v1/namespaces/default/plugins/plugwerk-java-cli-example-hello-cmd-plugin/releases/0.1.0-SNAPSHOT" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## Running the CLI

### Build the host application

```bash
cd examples/
./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:assemble
```

The fat JAR is written to
`plugwerk-java-cli-example/plugwerk-java-cli-example-app/build/libs/*-fat.jar`.

### Built-in marketplace commands

```bash
cd examples/
JAR=plugwerk-java-cli-example/plugwerk-java-cli-example-app/build/libs/*-fat.jar

# List published plugins
java -jar $JAR --server=http://localhost:8080 --access-token=$TOKEN list

# Search by category
java -jar $JAR --server=http://localhost:8080 search --category=utilities

# Search by keyword
java -jar $JAR --server=http://localhost:8080 search hello

# Check for updates (compares installed plugins against the server)
java -jar $JAR --server=http://localhost:8080 --access-token=$TOKEN update

# Apply all available updates
java -jar $JAR --server=http://localhost:8080 --access-token=$TOKEN update --apply
```

### Installing and using a dynamic plugin command

```bash
# Install hello-cmd-plugin from the server
java -jar $JAR --server=http://localhost:8080 --access-token=$TOKEN \
    install plugwerk-java-cli-example-hello-cmd-plugin 0.1.0-SNAPSHOT
# -> Successfully installed plugwerk-java-cli-example-hello-cmd-plugin@0.1.0-SNAPSHOT
# -> [plugin] Registered dynamic command: hello

# Install sysinfo-cmd-plugin
java -jar $JAR --server=http://localhost:8080 --access-token=$TOKEN \
    install plugwerk-java-cli-example-sysinfo-cmd-plugin 0.1.0-SNAPSHOT
# -> Successfully installed plugwerk-java-cli-example-sysinfo-cmd-plugin@0.1.0-SNAPSHOT
# -> [plugin] Registered dynamic command: sysinfo

# Use the dynamically loaded commands (on the next invocation)
java -jar $JAR hello
# -> Hello, World!

java -jar $JAR hello --name=Plugwerk --language=de
# -> Hallo, Plugwerk!

java -jar $JAR sysinfo
# -> Java:       21.0.3 (Eclipse Adoptium)
# -> OS:         Mac OS X 14.5 (aarch64)
# -> Heap:       256 MB free / 512 MB allocated / 1024 MB max
# -> Processors: 10

java -jar $JAR sysinfo --all     # includes all system properties

# Uninstall a plugin (stops + unloads it, removes ZIP and extracted directory)
java -jar $JAR uninstall plugwerk-java-cli-example-hello-cmd-plugin
```

### Via Gradle (without building the fat JAR)

```bash
cd examples/

./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="--server=http://localhost:8080 --access-token=$TOKEN list"

./gradlew :plugwerk-java-cli-example:plugwerk-java-cli-example-app:run \
    --args="install plugwerk-java-cli-example-hello-cmd-plugin 0.1.0-SNAPSHOT"
```

---

## Configuration Reference

| Option | Short | Env Variable | Default | Description |
|--------|-------|--------------|---------|-------------|
| `--server` | `-s` | `PLUGWERK_SERVER_URL` | `http://localhost:8080` | Plugwerk server base URL |
| `--namespace` | `-n` | `PLUGWERK_NAMESPACE` | `default` | Namespace slug |
| `--plugins-dir` | | `PLUGWERK_PLUGINS_DIR` | `./plugins` | PF4J plugins directory |
| `--access-token` | `-t` | `PLUGWERK_ACCESS_TOKEN` | _(none)_ | JWT Bearer token |

The `--plugins-dir` path is resolved relative to the **current working directory**.
Use an absolute path when invoking the JAR from a different directory:

```bash
java -jar $JAR --plugins-dir=/absolute/path/to/plugins --server=http://localhost:8080 list
```

---

## Writing Your Own CLI Plugin

1. Create a new Gradle module and add a `compileOnly` dependency on
   `plugwerk-java-cli-example-api`.

2. Implement `CliCommand` and annotate your class with `@Extension` and
   `@Command`:

   ```java
   @Extension
   @Command(name = "my-command", description = "Does something useful.")
   public class MyCommand implements CliCommand, Runnable {

       @Override
       public CommandLine toCommandLine() { return new CommandLine(this); }

       @Override
       public void run() { System.out.println("my-command executed"); }
   }
   ```

3. Add a `Plugin` subclass as the PF4J entry point:

   ```java
   public class MyPlugin extends Plugin {}
   ```

4. Configure PF4J metadata in `tasks.jar { manifest { attributes(...) } }` and
   build a ZIP with the same structure used by `plugwerk-java-cli-example-hello-cmd-plugin`.

5. Add `name`, `version`, `id`, and at least a `description` to `plugwerk.yml`
   embedded in `src/main/resources/`. The `id` must match `Plugin-Id` in the manifest.

6. Upload the ZIP to the Plugwerk server, approve the release (DRAFT → PUBLISHED),
   and install it via the CLI.

---

## Debug Logging

Set `PLUGWERK_LOG_LEVEL=DEBUG` to see PF4J plugin loading details:

```bash
PLUGWERK_LOG_LEVEL=DEBUG java -jar $JAR --server=http://localhost:8080 list
```
