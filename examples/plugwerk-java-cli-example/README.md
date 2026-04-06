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

The host application. Uses [picocli](https://picocli.info/) for command parsing
and PF4J's `DefaultPluginManager` for plugin lifecycle management.

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

Built-in subcommands:

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

| Plugin | Plugin ID | Subcommand | What it does |
|--------|-----------|------------|--------------|
| `plugwerk-java-cli-example-hello-cmd-plugin` | `io.plugwerk.example.cli.hello` | `hello` | Greets with `--name` and `--language` (en/de/es) |
| `plugwerk-java-cli-example-sysinfo-cmd-plugin` | `io.plugwerk.example.cli.sysinfo` | `sysinfo` | Prints Java/OS/heap info; `--all` for all system properties |

---

## Building

This example is a self-contained Gradle project that can be built independently:

```bash
cd examples/plugwerk-java-cli-example/
./gradlew build
```

Dependencies on `plugwerk-spi` are resolved automatically via Gradle composite
build — no `publishToMavenLocal` needed.

> **Standalone mode**: If you build outside of the monorepo checkout, run
> `./gradlew publishToMavenLocal` in the main project first.

## Prerequisites

### 1. Start a local Plugwerk server

```bash
# Start the database
docker compose up -d postgres

# Start the server
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun
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

Authentication depends on the namespace's visibility setting:

### Public namespaces (no API key required for read operations)

If the namespace has **public catalog** enabled (`publicCatalog = true`), read-only
operations (list, search, download) work without authentication:

```bash
# No --api-key needed for listing plugins in a public namespace
java -jar *-fat.jar --server=http://localhost:8080 list
```

> The `default` namespace is public by default (created by the initial migration).

Write operations (upload, approve, delete) **always** require an API key,
even on public namespaces.

### Private namespaces (API key required for all operations)

For namespaces with `publicCatalog = false`, **all** operations require a
namespace-scoped API key. See [ADR-0011](../../docs/adrs/0011-client-auth-api-key-strategy.md).

### Generating an API key

Generate a key via the Plugwerk Web UI (Admin → Namespaces → select namespace →
API Keys → Generate Key) or via the API:

```bash
# Log in to get a JWT (one-time, for key generation only)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-admin-password>"}' | jq -r .accessToken)

# Generate a namespace-scoped API key
API_KEY=$(curl -s -X POST http://localhost:8080/api/v1/namespaces/default/access-keys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"CLI example"}' | jq -r .key)

echo $API_KEY   # pwk_...
```

> **Important:** The plain-text key is shown only once. Store it securely.

### Passing the API key to the CLI

```bash
# Option A: inline flag
java -jar *-fat.jar --server=http://localhost:8080 --api-key=$API_KEY list

# Option B: environment variable (persists in the shell session)
export PLUGWERK_API_KEY=$API_KEY
java -jar *-fat.jar --server=http://localhost:8080 list

# Option C: no key (works for read operations on public namespaces)
java -jar *-fat.jar --server=http://localhost:8080 list
```

---

### API key vs. JWT scope

API keys grant **read-only** access. Write and admin operations require a JWT.

| Operation | API Key (`X-Api-Key`) | JWT (MEMBER+) | JWT (ADMIN) |
|-----------|:---:|:---:|:---:|
| List / search / download plugins | ✅ | ✅ | ✅ |
| Check for updates / `plugins.json` | ✅ | ✅ | ✅ |
| Upload plugin releases | ❌ | ✅ | ✅ |
| Approve / reject releases | ❌ | ❌ | ✅ |
| Delete plugins / releases | ❌ | ❌ | ✅ |
| Manage namespace members | ❌ | ❌ | ✅ |
| Manage access keys | ❌ | ❌ | ✅ |
| Create / delete namespaces | ❌ | ❌ | ✅ (superadmin) |
| Manage users / OIDC | ❌ | ❌ | ✅ (superadmin) |

> API keys are designed for **SDK polling and plugin discovery**. All management
> operations (upload, delete, approve, members) require a JWT Bearer token.

---

## Uploading Example Plugins to the Server

### 1. Build the plugin ZIPs

```bash
cd examples/plugwerk-java-cli-example/
./gradlew :plugwerk-java-cli-example-hello-cmd-plugin:assemble \
          :plugwerk-java-cli-example-sysinfo-cmd-plugin:assemble
```

Artifacts are written to each module's `build/pf4j/` directory:
- `plugwerk-java-cli-example-hello-cmd-plugin/build/pf4j/io.plugwerk.example.cli.hello-0.1.0-SNAPSHOT.zip`
- `plugwerk-java-cli-example-sysinfo-cmd-plugin/build/pf4j/io.plugwerk.example.cli.sysinfo-0.1.0-SNAPSHOT.zip`

### 2. Create a namespace (if it does not exist yet)

Creating a namespace requires **superadmin** privileges — use a JWT Bearer token,
not an API key:

```bash
curl -s -X POST http://localhost:8080/api/v1/namespaces \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"slug":"default","ownerOrg":"default"}'
```

If the namespace already exists the server returns HTTP 409 — that is fine.

### 3. Upload the plugin releases

Uploading requires **MEMBER** or **ADMIN** role — use a JWT Bearer token:

```bash
# Upload hello-cmd-plugin
curl -s -X POST \
  "http://localhost:8080/api/v1/namespaces/default/plugin-releases" \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifact=@plugwerk-java-cli-example-hello-cmd-plugin/build/pf4j/io.plugwerk.example.cli.hello-0.1.0-SNAPSHOT.zip"

# Upload sysinfo-cmd-plugin
curl -s -X POST \
  "http://localhost:8080/api/v1/namespaces/default/plugin-releases" \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifact=@plugwerk-java-cli-example-sysinfo-cmd-plugin/build/pf4j/io.plugwerk.example.cli.sysinfo-0.1.0-SNAPSHOT.zip"
```

A successful upload returns HTTP 201 with the release details in JSON.

### 4. Publish the releases (DRAFT → PUBLISHED)

Newly uploaded releases have status `DRAFT` and are not visible in the catalog
until explicitly published. Approving requires **ADMIN** role — use a JWT:

```bash
# Get the release ID from the upload response, or look it up:
curl -s "http://localhost:8080/api/v1/namespaces/default/plugins/io.plugwerk.example.cli.hello/releases/0.1.0-SNAPSHOT" \
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
# List all plugins (public namespace — no auth needed)
curl -s "http://localhost:8080/api/v1/namespaces/default/plugins" | jq .

# Show a specific plugin with its releases
curl -s "http://localhost:8080/api/v1/namespaces/default/plugins/io.plugwerk.example.cli.hello" | jq .

# Show release detail (draft releases require auth)
curl -s "http://localhost:8080/api/v1/namespaces/default/plugins/io.plugwerk.example.cli.hello/releases/0.1.0-SNAPSHOT" \
  -H "X-Api-Key: $API_KEY" | jq .
```

---

## Running the CLI

### Build the host application

```bash
cd examples/plugwerk-java-cli-example/
./gradlew :plugwerk-java-cli-example-app:assemble
```

The fat JAR is written to
`plugwerk-java-cli-example-app/build/libs/*-fat.jar`.

### Built-in marketplace commands

```bash
cd examples/plugwerk-java-cli-example/
JAR=plugwerk-java-cli-example-app/build/libs/*-fat.jar

# List published plugins
java -jar $JAR --server=http://localhost:8080 --api-key=$API_KEY list

# Search by category
java -jar $JAR --server=http://localhost:8080 search --category=utilities

# Search by keyword
java -jar $JAR --server=http://localhost:8080 search hello

# Check for updates (compares installed plugins against the server)
java -jar $JAR --server=http://localhost:8080 --api-key=$API_KEY update

# Apply all available updates
java -jar $JAR --server=http://localhost:8080 --api-key=$API_KEY update --apply
```

### Installing and using a dynamic plugin command

```bash
# Install hello-cmd-plugin from the server
java -jar $JAR --server=http://localhost:8080 --api-key=$API_KEY \
    install io.plugwerk.example.cli.hello 0.1.0-SNAPSHOT
# -> Successfully installed io.plugwerk.example.cli.hello@0.1.0-SNAPSHOT
# -> [plugin] Registered dynamic command: hello

# Install sysinfo-cmd-plugin
java -jar $JAR --server=http://localhost:8080 --api-key=$API_KEY \
    install io.plugwerk.example.cli.sysinfo 0.1.0-SNAPSHOT
# -> Successfully installed io.plugwerk.example.cli.sysinfo@0.1.0-SNAPSHOT
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
java -jar $JAR uninstall io.plugwerk.example.cli.hello
```

### Via Gradle (without building the fat JAR)

```bash
cd examples/plugwerk-java-cli-example/

./gradlew :plugwerk-java-cli-example-app:run \
    --args="--server=http://localhost:8080 --api-key=$API_KEY list"

./gradlew :plugwerk-java-cli-example-app:run \
    --args="install io.plugwerk.example.cli.hello 0.1.0-SNAPSHOT"
```

---

## Configuration Reference

| Option | Short | Env Variable | Default | Description |
|--------|-------|--------------|---------|-------------|
| `--server` | `-s` | `PLUGWERK_SERVER_URL` | `http://localhost:8080` | Plugwerk server base URL |
| `--namespace` | `-n` | `PLUGWERK_NAMESPACE` | `default` | Namespace slug |
| `--plugins-dir` | | `PLUGWERK_PLUGINS_DIR` | `./plugins` | PF4J plugins directory |
| `--api-key` | `-k` | `PLUGWERK_API_KEY` | _(none)_ | Namespace-scoped API key |

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

4. Configure **all** plugin metadata in `tasks.jar { manifest { attributes(...) } }`.
   The Plugwerk server reads everything from `MANIFEST.MF` — no `plugwerk.yml` needed.

   ```kotlin
   tasks.jar {
       manifest {
           attributes(
               // PF4J standard attributes
               "Plugin-Id"          to "com.example.my-plugin",
               "Plugin-Class"       to "com.example.MyPlugin",
               "Plugin-Version"     to project.version.toString(),
               "Plugin-Provider"    to "Example Corp",
               "Plugin-Description" to "Does something useful.",
               // Plugwerk custom attributes (optional, but recommended)
               "Plugin-Name"        to "My Plugin",
               "Plugin-License"     to "MIT",
               "Plugin-Tags"        to "my-tag, demo, utilities",
           )
       }
   }
   ```

5. Build a ZIP with the same structure used by `plugwerk-java-cli-example-hello-cmd-plugin`.

6. Upload the ZIP to the Plugwerk server, approve the release (DRAFT → PUBLISHED),
   and install it via the CLI.

---

## Debug Logging

Set `PLUGWERK_LOG_LEVEL=DEBUG` to see PF4J plugin loading details:

```bash
PLUGWERK_LOG_LEVEL=DEBUG java -jar $JAR --server=http://localhost:8080 list
```

---

## Known Limitations

- **PF4J 3.15 shutdown bug**: `stopPlugins()` throws a `ConcurrentModificationException` during
  JVM shutdown. This is suppressed internally and has no functional impact.
- **ZIP extraction**: The SDK plugin ZIP is extracted to a subdirectory on first run. If you
  replace the ZIP with a newer version, delete the extracted directory manually before the next run:
  ```bash
  rm -rf plugins/plugwerk-client-plugin-*/
  cp plugwerk-client-plugin-<new-version>.zip plugins/
  ```
