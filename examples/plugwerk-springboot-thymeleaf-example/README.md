# plugwerk-springboot-thymeleaf-example

A Spring Boot web application demonstrating dynamic plugin pages powered by
PF4J and the Plugwerk client SDK. Plugins contribute menu entries and full
HTML pages to the host application at runtime.

## Project Structure

```
plugwerk-springboot-thymeleaf-example/
├── src/                                            # Host Spring Boot application
├── plugwerk-springboot-thymeleaf-example-api/      # Extension-point interface: PageContribution
├── plugwerk-example-plugin-sysinfo/                # Example plugin: system information page
└── plugwerk-example-plugin-env/                    # Example plugin: environment variables page
```

### plugwerk-springboot-thymeleaf-example-api

Defines `PageContribution`, the PF4J `ExtensionPoint` interface that every
dynamically loaded page plugin must implement:

```java
public interface PageContribution extends ExtensionPoint {
    String getMenuLabel();   // label in the navigation bar
    String getRoute();       // URL path segment (e.g. "sysinfo")
    String getTitle();       // page <title> and heading
    String renderHtml();     // HTML fragment for the page body
}
```

### Host Application

The Spring Boot host application:
- Manages PF4J plugin lifecycle (load, start, stop, uninstall)
- Configures the Plugwerk SDK plugin for server communication
- Discovers `PageContribution` extensions and renders them via Thymeleaf
- Provides plugin catalog, install, uninstall, and update pages

### Example Plugins

| Plugin | Plugin ID | Route | What it does |
|--------|-----------|-------|--------------|
| `plugwerk-example-plugin-sysinfo` | `io.plugwerk.example.webapp.sysinfo` | `/page/sysinfo` | Displays JVM, OS, memory, and uptime |
| `plugwerk-example-plugin-env` | `io.plugwerk.example.webapp.env` | `/page/env` | Displays environment variables (secrets masked) |

---

## Building

```bash
cd examples/plugwerk-springboot-thymeleaf-example/
./gradlew build
```

Dependencies on `plugwerk-spi` are resolved automatically via Gradle composite
build — no `publishToMavenLocal` needed.

---

## Running

### 1. Start the Plugwerk server

```bash
# From the project root
docker compose up -d postgres
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun
```

### 2. Build and copy the SDK plugin

The `plugwerk-client-plugin` ZIP is automatically copied to `plugins/` during
the build. If you only want to copy it manually:

```bash
# From the project root
./gradlew :plugwerk-client-plugin:assemble
mkdir -p examples/plugwerk-springboot-thymeleaf-example/plugins
cp plugwerk-client-plugin/build/pf4j/plugwerk-client-plugin-*.zip \
   examples/plugwerk-springboot-thymeleaf-example/plugins/
```

### 3. Start the example application

```bash
cd examples/plugwerk-springboot-thymeleaf-example/
./gradlew bootRun
```

Open `http://localhost:8081` in your browser.

---

## Publishing Example Plugins to the Server

### 1. Build the plugin ZIPs

```bash
cd examples/plugwerk-springboot-thymeleaf-example/
./gradlew :plugwerk-example-plugin-sysinfo:assemble \
          :plugwerk-example-plugin-env:assemble
```

### 2. Upload and approve

```bash
# Get a JWT token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-admin-password>"}' | jq -r .accessToken)

# Upload sysinfo plugin
curl -s -X POST \
  "http://localhost:8080/api/v1/namespaces/default/plugin-releases" \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifact=@plugwerk-example-plugin-sysinfo/build/pf4j/io.plugwerk.example.webapp.sysinfo-0.1.0-SNAPSHOT.zip"

# Upload env plugin
curl -s -X POST \
  "http://localhost:8080/api/v1/namespaces/default/plugin-releases" \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifact=@plugwerk-example-plugin-env/build/pf4j/io.plugwerk.example.webapp.env-0.1.0-SNAPSHOT.zip"

# Approve the releases (replace <release-id> with the UUID from the upload response)
curl -s -X POST "http://localhost:8080/api/v1/namespaces/default/reviews/<release-id>/approve" \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Install via the web UI

1. Open `http://localhost:8081/plugins/available`
2. Click **Install** next to the desired plugin
3. The plugin's page appears in the navigation bar immediately

---

## Configuration Reference

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `server.port` | `SERVER_PORT` | `8081` | Web server port |
| `plugwerk.server-url` | `PLUGWERK_SERVER_URL` | `http://localhost:8080` | Plugwerk server base URL |
| `plugwerk.namespace` | `PLUGWERK_NAMESPACE` | `default` | Namespace slug |
| `plugwerk.api-key` | `PLUGWERK_API_KEY` | _(none)_ | Namespace-scoped API key |
| `plugwerk.plugins-dir` | `PLUGWERK_PLUGINS_DIR` | `./plugins` | PF4J plugins directory |

---

## Writing Your Own Page Plugin

1. Create a new Gradle module with a `compileOnly` dependency on
   `plugwerk-springboot-thymeleaf-example-api`.

2. Implement `PageContribution` and annotate with `@Extension`:

   ```java
   @Extension
   public class StatusContribution implements PageContribution {
       public String getMenuLabel() { return "Status"; }
       public String getRoute()     { return "status"; }
       public String getTitle()     { return "System Status"; }
       public String renderHtml()   { return "<p>All systems operational.</p>"; }
   }
   ```

3. Add a `Plugin` subclass as the PF4J entry point:

   ```java
   public class StatusPlugin extends Plugin {}
   ```

4. Configure plugin metadata in `tasks.jar { manifest { attributes(...) } }`.

5. Build a ZIP, upload to the Plugwerk server, approve, and install via the web UI.
