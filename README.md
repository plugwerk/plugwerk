# Plugwerk

**Self-Hosted Plugin Management and Marketplace Software for the Java/PF4J Ecosystem**

Plugwerk is the missing link between [PF4J](https://github.com/pf4j/pf4j) and a product's plugin ecosystem. It gives you a central, self-hosted hub for publishing, versioning, distributing, and updating plugins at runtime — comparable to Maven Central for build dependencies, but designed for runtime plugins.

## Who is Plugwerk for?

| Role | How Plugwerk helps |
|------|--------------------|
| **Application developers** | Integrate the `plugwerk-client-plugin` into your PF4J-based application to let users discover, install, and update plugins at runtime — without shipping a new application release |
| **Plugin developers** | Upload plugin artifacts via the Web UI or REST API, manage versions and metadata, and publish releases with a single API call from your CI/CD pipeline |
| **Operations teams** | Deploy a self-hosted, Docker-based plugin management platform for your organization; one server can serve multiple products via namespaces |

## How it works

```
Your Application                     Plugwerk Server
┌────────────────────────────────┐    ┌─────────────────────────────┐
│ PF4J PluginManager             │    │ REST API  /api/v1/...       │
│  ↕ plugwerk-client-plugin      │◄──►│ Web UI    http://...        │
│    PlugwerkMarketplace         │    │ PostgreSQL + Artifact Store │
└────────────────────────────────┘    └─────────────────────────────┘
         ↑ runtime install/update               ↑ upload via CI/CD
         |                                      |
  end users / admins                    plugin developers
```

A **namespace** scopes all plugins for one product (e.g. `acme-crm`). Plugin artifacts (JAR/ZIP) embed a `plugwerk.yml` descriptor; the server reads it automatically on upload.

## Quick Start (Self-Hosted)

### Prerequisites

- Docker + Docker Compose

### Start with Docker Compose

```bash
git clone https://github.com/devtank42gmbh/plugwerk.git
cd plugwerk
docker compose up -d
```

Wait for the server to be ready:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Open the Web UI at **http://localhost:8080**.

### Default credentials

| Username | Password |
|----------|----------|
| `test`   | `test`   |

> **Production:** Set `PLUGWERK_JWT_SECRET` to a strong secret (at least 32 characters) and configure proper user credentials. Never use the defaults on a publicly accessible server.

### Obtain a token and create a namespace

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}' | jq -r '.accessToken')

# Create a namespace for your product
curl -s -X POST http://localhost:8080/api/v1/namespaces \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"slug":"myproduct","ownerOrg":"My Company"}'
```

### Upload and publish a plugin

```bash
# Upload a plugin JAR/ZIP — descriptor is read automatically from plugwerk.yml inside the artifact
curl -s -X POST http://localhost:8080/api/v1/namespaces/myproduct/releases \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifact=@my-plugin-1.0.0.jar"
# Returns: {"id":"...","version":"1.0.0","status":"draft",...}

# Publish the release (draft → published)
RELEASE_ID="<id from above>"
curl -s -X POST http://localhost:8080/api/v1/namespaces/myproduct/reviews/$RELEASE_ID/approve \
  -H "Authorization: Bearer $TOKEN"

# Browse the catalog
curl -s http://localhost:8080/api/v1/namespaces/myproduct/plugins | jq .
```

## Integrating the Client SDK into your Application

The `plugwerk-client-plugin` is a PF4J plugin with an isolated classloader — it has no dependency conflicts with your application. Add it to your PF4J plugins directory and use it to connect to a Plugwerk server at runtime.

### Add to your Gradle project

```kotlin
// build.gradle.kts
dependencies {
    // plugwerk-spi provides the ExtensionPoint interfaces
    implementation("io.plugwerk:plugwerk-spi:<version>")
}
```

The `plugwerk-client-plugin` ZIP is deployed as a PF4J plugin alongside your application's plugins, not as a compile-time dependency.

### Configure and use the client

```kotlin
import io.plugwerk.spi.PlugwerkConfig
import io.plugwerk.spi.PlugwerkMarketplace

// Build the configuration
val config = PlugwerkConfig.builder()
    .serverUrl("https://plugins.mycompany.com")
    .namespace("myproduct")
    .apiKey(System.getenv("PLUGWERK_API_KEY"))
    .build()

// Use as a pf4j-update UpdateRepository drop-in
val marketplace = PlugwerkMarketplace(config)
val updateManager = UpdateManager(pluginManager, listOf(marketplace.asUpdateRepository()))

// Or use the granular extension points directly
val catalog = pluginManager.getExtension(PlugwerkCatalog::class.java)
val installer = pluginManager.getExtension(PlugwerkInstaller::class.java)
val updateChecker = pluginManager.getExtension(PlugwerkUpdateChecker::class.java)
```

### pf4j-update drop-in replacement

Already using `pf4j-update`? Point it at Plugwerk's `plugins.json` endpoint — no code changes required:

```
https://your-plugwerk-server/api/v1/namespaces/{namespace}/plugins.json
```

### The `plugwerk.yml` descriptor

Embed this file inside your plugin JAR/ZIP so the server can read metadata automatically:

```yaml
# src/main/resources/plugwerk.yml
plugwerk:
  id: com.example.my-plugin         # must match Plugin-Id in MANIFEST.MF
  version: 1.2.0
  name: My Plugin
  description: Exports reports as PDF with configurable templates
  author: ACME GmbH
  license: Apache-2.0
  requires:
    system-version: ">=2.0.0 & <4.0.0"   # compatible host application versions
    plugins:
      - id: com.example.template-engine
        version: ">=1.0.0"
  categories:
    - export
    - reporting
  tags:
    - pdf
    - report
```

If `plugwerk.yml` is absent, the server falls back to the PF4J manifest (`MANIFEST.MF` / `plugin.properties`).

## API Documentation

Interactive API reference powered by [Scalar](https://scalar.com/) — open in the browser after starting the server:

```
http://localhost:8080/api-docs
```

Raw OpenAPI 3.1 spec:

```
http://localhost:8080/api-docs/openapi.yaml
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PLUGWERK_DB_URL` | `jdbc:postgresql://localhost:5432/plugwerk` | PostgreSQL JDBC URL |
| `PLUGWERK_DB_USERNAME` | `plugwerk` | Database username |
| `PLUGWERK_DB_PASSWORD` | `plugwerk` | Database password |
| `PLUGWERK_JWT_SECRET` | _(dev default)_ | HMAC-SHA256 signing secret, minimum 32 characters. **Must be changed in production.** |
| `PLUGWERK_BASE_URL` | `http://localhost:8080` | Public base URL of the server (used for artifact download links) |
| `PLUGWERK_STORAGE_ROOT` | `/var/plugwerk/artifacts` | Filesystem path for storing artifact binaries |
| `PLUGWERK_STORAGE_TYPE` | `fs` | Storage backend: `fs` (filesystem) |

## Key Features

- Searchable plugin catalog with full-text search, category and tag filters
- Plugin upload via Web UI or REST API (CI/CD-ready)
- SemVer-based versioning with compatibility ranges (`requires: >=2.0.0 & <4.0.0`)
- SHA-256 checksum verification for artifact integrity
- Drop-in replacement for [`pf4j-update`](https://github.com/pf4j/pf4j-update) — backward-compatible `plugins.json` endpoint
- Review/approval workflow before releases are published
- Multi-namespace support: one server serves multiple products/organizations
- Self-hosted via Docker Compose (open source, AGPL-3.0)

## Development Setup

### Prerequisites

- JDK 21+
- Node.js 20+
- Docker (for PostgreSQL)

### Run locally

```bash
# Start PostgreSQL
docker compose up -d postgres

# Build all modules
./gradlew build

# Start the server
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun
```

The server starts at `http://localhost:8080`. The Vite dev server (frontend with hot reload) starts separately:

```bash
cd plugwerk-server/plugwerk-server-frontend
npm run dev
# Frontend available at http://localhost:5173 (proxies /api → localhost:8080)
```

### E2E Smoke Test

```bash
./scripts/smoke-test.sh
```

Runs the full upload → catalog → download flow against a Docker Compose stack.

### Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all modules + run all tests |
| `./gradlew build -x test` | Build without tests |
| `./gradlew :plugwerk-api:openApiGenerate` | Regenerate backend DTOs/interfaces from OpenAPI spec |
| `./gradlew :plugwerk-server:plugwerk-server-backend:bootRun` | Start the backend server (port 8080) |
| `npm run dev` | Start the Vite frontend dev server (port 5173) |
| `npm run generate:api` | Regenerate TypeScript client from OpenAPI spec |
| `npm run test:run` | Run frontend tests (Vitest) |
| `npm run test:coverage` | Frontend tests with V8 coverage report |

## Module Structure

```
plugwerk/
├── plugwerk-api/                  # OpenAPI 3.1 spec (API-First) + generated DTOs/interfaces
├── plugwerk-spi/                  # Shared ExtensionPoint interfaces, DTOs, constants (JVM 11)
├── plugwerk-descriptor/           # plugwerk.yml parser/validator + PF4J manifest fallback (JVM 11)
├── plugwerk-server/
│   ├── plugwerk-server-backend/   # Spring Boot 4.x + Kotlin REST API (JVM 21)
│   └── plugwerk-server-frontend/  # React + TypeScript + Material UI + Zustand (embedded in server JAR)
├── plugwerk-client-plugin/        # PF4J plugin with isolated classloader: OkHttp + Jackson (JVM 17)
└── examples/
    └── plugwerk-java-cli-example/ # End-to-end example: CLI app with dynamically loaded plugins
```

## Documentation

- [Interactive API Reference](http://localhost:8080/api-docs) (requires running server)
- [Example: Java CLI Application](examples/plugwerk-java-cli-example/README.md)
- [Product Concept (EN)](docs/concepts/concept-pf4j-marketplace-en.md)
- [Produktkonzept (DE)](docs/concepts/concept-pf4j-marketplace-de.md)
- [Architecture Decision Records](docs/adrs/)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

- Language: **English** for all code, docs, issues, and reviews
- Branches: `feature/<issue-id>_<short-description>` — never commit directly to `main`
- Commits: [Conventional Commits](https://www.conventionalcommits.org/) format
- Use the issue and PR templates in `.github/`

## License

[AGPL-3.0](LICENSE)
